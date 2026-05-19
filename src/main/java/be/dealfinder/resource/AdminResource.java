package be.dealfinder.resource;

import be.dealfinder.entity.Deal;
import be.dealfinder.extraction.ProductExtractor;
import be.dealfinder.scraper.GraphQLScraper;
import be.dealfinder.service.NotificationService;
import be.dealfinder.service.ScraperService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.time.LocalDate;
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
    ProductExtractor productExtractor;

    @Inject
    NotificationService notificationService;

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
    @Operation(summary = "Run product extraction on existing active deals that haven't been fingerprinted yet")
    public Response backfillImages(@QueryParam("limit") @DefaultValue("50") int limit) {
        LocalDate today = LocalDate.now();
        String query = "fingerprint is null and imageUrl is not null and validUntil >= ?1";
        List<Deal> candidates = Deal.find(query, today).page(0, limit).list();
        LOG.info("Extraction backfill: " + candidates.size() + " active candidates");

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

        long remaining = Deal.count(query, today) - analyzed;
        LOG.info("Extraction backfill done: analyzed=" + analyzed + " failed=" + failed + " remaining=" + remaining);
        return Response.ok(Map.of(
                "analyzed", analyzed,
                "failed", failed,
                "remaining", remaining
        )).build();
    }

    @POST
    @Path("/test-notification")
    @Operation(summary = "Send a test FCM push to every registered token for a given user UID")
    public Response testNotification(@QueryParam("uid") String uid) {
        if (uid == null || uid.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "uid query parameter is required"))
                    .build();
        }
        int sent = notificationService.sendTestPush(uid);
        return Response.ok(Map.of(
                "uid", uid,
                "sent", sent
        )).build();
    }

    @Transactional
    boolean analyzeAndPersist(Deal deal) {
        ProductExtractor.Listing listing = new ProductExtractor.Listing(
                deal.productName, deal.brand, deal.quantity,
                deal.retailer != null ? deal.retailer.slug : null,
                deal.retailer != null ? deal.retailer.name : null);
        return productExtractor.extract(deal.imageUrl, listing).map(ex -> {
            Deal managed = Deal.findById(deal.id);
            if (managed == null) return false;
            GraphQLScraper.applyExtraction(managed, ex);
            managed.persist();
            return true;
        }).orElse(false);
    }
}
