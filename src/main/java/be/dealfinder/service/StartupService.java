package be.dealfinder.service;

import be.dealfinder.entity.Deal;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

@ApplicationScoped
public class StartupService {

    private static final Logger LOG = Logger.getLogger(StartupService.class);

    @Inject
    DataInitService dataInitService;

    @Inject
    ScraperService scraperService;

    @Inject
    ManagedExecutor executor;

    void onStart(@Observes StartupEvent event) {
        LOG.info("===========================================");
        LOG.info("Belgian Deal Finder API - Starting up...");
        LOG.info("===========================================");

        dataInitService.initializeAll();

        // Auto-scrape if database is empty (e.g., Cloud Run cold start with H2 in-memory)
        if (Deal.count() == 0) {
            LOG.info("Database is empty — triggering background scrape...");
            executor.runAsync(scraperService::scrapeAll);
        }

        LOG.info("===========================================");
        LOG.info("Startup complete!");
        LOG.info("===========================================");
    }
}
