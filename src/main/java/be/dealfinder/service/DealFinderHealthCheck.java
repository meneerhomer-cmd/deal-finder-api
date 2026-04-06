package be.dealfinder.service;

import be.dealfinder.entity.Deal;
import be.dealfinder.entity.Retailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class DealFinderHealthCheck implements HealthCheck {

    @Inject
    ScraperService scraperService;

    @Override
    public HealthCheckResponse call() {
        long retailerCount = Retailer.count("active", true);
        long dealCount = Deal.count();
        var status = scraperService.getStatus();

        return HealthCheckResponse.named("deal-finder")
                .status(retailerCount > 0)
                .withData("retailers", retailerCount)
                .withData("deals", dealCount)
                .withData("scraperRunning", status.running())
                .withData("lastScrape", status.getLastRunTimeFormatted())
                .build();
    }
}
