package be.dealfinder.service;

import be.dealfinder.entity.Deal;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class StartupService {

    private static final Logger LOG = Logger.getLogger(StartupService.class);

    @Inject
    DataInitService dataInitService;

    @Inject
    ScraperService scraperService;

    void onStart(@Observes StartupEvent event) {
        LOG.info("===========================================");
        LOG.info("Belgian Deal Finder API - Starting up...");
        LOG.info("===========================================");

        dataInitService.initializeAll();

        // Auto-scrape if database is empty (e.g., Cloud Run cold start with H2 in-memory)
        if (Deal.count() == 0) {
            LOG.info("Database is empty — triggering background scrape...");
            CompletableFuture.runAsync(scraperService::scrapeAll);
        }

        LOG.info("===========================================");
        LOG.info("Startup complete!");
        LOG.info("===========================================");
    }
}
