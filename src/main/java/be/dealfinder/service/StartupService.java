package be.dealfinder.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class StartupService {

    private static final Logger LOG = Logger.getLogger(StartupService.class);

    @Inject
    DataInitService dataInitService;

    @Inject
    ScraperService scraperService;

    @ConfigProperty(name = "app.scrape-on-startup", defaultValue = "false")
    boolean scrapeOnStartup;

    void onStart(@Observes StartupEvent event) {
        LOG.info("===========================================");
        LOG.info("Belgian Deal Finder API - Starting up...");
        LOG.info("===========================================");

        // Initialize data
        dataInitService.initializeAll();

        // Optionally scrape on startup
        if (scrapeOnStartup) {
            LOG.info("Scrape on startup is enabled, starting initial scrape...");
            scraperService.scrapeAll();
        }

        LOG.info("===========================================");
        LOG.info("Startup complete!");
        LOG.info("Swagger UI: http://localhost:8080/swagger-ui");
        LOG.info("===========================================");
    }
}
