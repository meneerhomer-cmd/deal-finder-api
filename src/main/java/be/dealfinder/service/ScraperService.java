package be.dealfinder.service;

import be.dealfinder.entity.Retailer;
import be.dealfinder.scraper.MyShopScraper;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class ScraperService {

    private static final Logger LOG = Logger.getLogger(ScraperService.class);

    @Inject
    MyShopScraper scraper;

    @Inject
    DealService dealService;

    @ConfigProperty(name = "scraper.enabled", defaultValue = "true")
    boolean enabled;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private LocalDateTime lastRunTime;
    private List<MyShopScraper.ScraperResult> lastResults = new ArrayList<>();

    @Scheduled(cron = "{scraper.schedule.cron}")
    public void scheduledScrape() {
        LOG.info("Scheduled scrape triggered");
        scrapeAll();
    }

    public List<MyShopScraper.ScraperResult> scrapeAll() {
        if (!enabled) {
            LOG.info("Scraper is disabled");
            return List.of();
        }

        if (!isRunning.compareAndSet(false, true)) {
            LOG.warn("Scrape already in progress, skipping...");
            return lastResults;
        }

        try {
            LOG.info("Starting full scrape of all retailers...");
            lastRunTime = LocalDateTime.now();
            lastResults = new ArrayList<>();

            List<Retailer> retailers = Retailer.findAllActive();
            LOG.info("Found " + retailers.size() + " active retailers");

            int totalAdded = 0;
            int totalUpdated = 0;

            for (Retailer retailer : retailers) {
                MyShopScraper.ScraperResult result = scraper.scrapeRetailer(retailer);
                lastResults.add(result);
                
                if (result.isSuccess()) {
                    totalAdded += result.added();
                    totalUpdated += result.updated();
                }
            }

            // Assign categories to any uncategorized deals
            dealService.assignCategories();

            // Cleanup old expired deals (keep for 30 days for history)
            long deleted = dealService.cleanupOldDeals(30);
            if (deleted > 0) {
                LOG.info("Cleaned up " + deleted + " old expired deals");
            }

            LOG.info("Full scrape completed: " + totalAdded + " added, " + totalUpdated + " updated");
            return lastResults;

        } finally {
            isRunning.set(false);
        }
    }

    public MyShopScraper.ScraperResult scrapeRetailer(String retailerSlug) {
        Retailer retailer = Retailer.findBySlug(retailerSlug);
        if (retailer == null) {
            return new MyShopScraper.ScraperResult(retailerSlug, 0, 0, "Retailer not found");
        }

        if (!retailer.active) {
            return new MyShopScraper.ScraperResult(retailerSlug, 0, 0, "Retailer is inactive");
        }

        return scraper.scrapeRetailer(retailer);
    }

    public ScraperStatus getStatus() {
        return new ScraperStatus(
                enabled,
                isRunning.get(),
                lastRunTime,
                lastResults
        );
    }

    public record ScraperStatus(
            boolean enabled,
            boolean running,
            LocalDateTime lastRunTime,
            List<MyShopScraper.ScraperResult> lastResults
    ) {
        public String getLastRunTimeFormatted() {
            if (lastRunTime == null) return "Never";
            return lastRunTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        public int getTotalDealsAdded() {
            return lastResults.stream()
                    .mapToInt(MyShopScraper.ScraperResult::added)
                    .sum();
        }

        public int getTotalDealsUpdated() {
            return lastResults.stream()
                    .mapToInt(MyShopScraper.ScraperResult::updated)
                    .sum();
        }

        public long getErrorCount() {
            return lastResults.stream()
                    .filter(r -> !r.isSuccess())
                    .count();
        }
    }
}
