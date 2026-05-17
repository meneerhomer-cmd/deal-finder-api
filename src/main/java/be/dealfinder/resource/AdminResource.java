package be.dealfinder.resource;

import be.dealfinder.entity.Deal;
import be.dealfinder.scraper.DealImageAnalyzer;
import be.dealfinder.scraper.GraphQLScraper;
import be.dealfinder.service.ScraperService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

@Path("/api/v1/admin")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Admin", description = "Administrative operations")
public class AdminResource {

    private static final Logger LOG = Logger.getLogger(AdminResource.class);

    @Inject
    ScraperService scraperService;

    @Inject
    DealImageAnalyzer imageAnalyzer;

    @POST
    @Path("/scrape")
    @Operation(summary = "Trigger a full scrape of all retailers")
    public Response scrapeAll() {
        if (scraperService.getStatus().running()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("message", "Scrape already in progress"))
                    .build();
        }
        scraperService.scrapeAll();
        var status = scraperService.getStatus();
        return Response.ok(Map.of(
                "message", "Scrape completed",
                "results", status.lastResults()
        )).build();
    }

    @POST
    @Path("/scrape/{retailerSlug}")
    @Operation(summary = "Trigger scrape for a specific retailer")
    public Response scrapeRetailer(@PathParam("retailerSlug") String retailerSlug) {
        GraphQLScraper.ScraperResult result = scraperService.scrapeRetailer(retailerSlug);
        if (result.isSuccess()) {
            return Response.ok(result).build();
        } else {
            return Response.status(Response.Status.BAD_REQUEST).entity(result).build();
        }
    }

    @GET
    @Path("/status")
    @Operation(summary = "Get scraper status")
    public ScraperService.ScraperStatus getStatus() {
        return scraperService.getStatus();
    }

    @POST
    @Path("/backfill-images")
    @Operation(summary = "Run image analysis on existing deals that haven't been analyzed yet")
    public Response backfillImages(@QueryParam("limit") @DefaultValue("50") int limit) {
        List<Deal> candidates = Deal.find("brand is null and imageUrl is not null")
                .page(0, limit)
                .list();
        LOG.info("Image backfill: " + candidates.size() + " candidates");

        int analyzed = 0;
        int failed = 0;
        for (Deal deal : candidates) {
            try {
                if (analyzeAndPersist(deal)) analyzed++;
            } catch (Exception e) {
                failed++;
                LOG.warn("Backfill failed for deal " + deal.id + ": " + e.getMessage());
            }
        }

        long remaining = Deal.count("brand is null and imageUrl is not null") - analyzed;
        LOG.info("Image backfill done: analyzed=" + analyzed + " failed=" + failed + " remaining=" + remaining);
        return Response.ok(Map.of(
                "analyzed", analyzed,
                "failed", failed,
                "remaining", remaining
        )).build();
    }

    @Transactional
    boolean analyzeAndPersist(Deal deal) {
        return imageAnalyzer.analyze(deal.imageUrl).map(info -> {
            Deal managed = Deal.findById(deal.id);
            if (managed == null) return false;
            managed.dealType = info.dealType();
            managed.quantity = info.quantity();
            managed.unitPrice = info.unitPrice();
            managed.brand = info.brand();
            managed.conditions = info.conditions();
            managed.minPurchase = info.minPurchase();
            managed.variants = info.variants();
            managed.loyaltyCard = info.loyaltyCard();
            managed.department = info.department();
            managed.validDays = info.validDays();
            managed.persist();
            return true;
        }).orElse(false);
    }
}
