package be.dealfinder.service;

import be.dealfinder.entity.Deal;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class StartupService {

    private static final Logger LOG = Logger.getLogger(StartupService.class);

    @Inject
    DataInitService dataInitService;

    @Inject
    ScraperService scraperService;

    private boolean initialScrapeNeeded = false;

    void onStart(@Observes StartupEvent event) {
        LOG.info("===========================================");
        LOG.info("Belgian Deal Finder API - Starting up...");
        LOG.info("===========================================");

        dataInitService.initializeAll();

        if (Deal.count() == 0) {
            LOG.info("Database is empty — will scrape after startup completes");
            initialScrapeNeeded = true;
        }

        LOG.info("===========================================");
        LOG.info("Startup complete!");
        LOG.info("===========================================");
    }

    @Scheduled(every = "30s", delayed = "5s")
    void checkInitialScrape() {
        if (initialScrapeNeeded && !scraperService.getStatus().running()) {
            initialScrapeNeeded = false;
            LOG.info("Triggering initial scrape (empty database)...");
            scraperService.scrapeAll();
        }
    }
}
