package be.dealfinder.resource;

import be.dealfinder.scraper.GraphQLScraper;
import be.dealfinder.service.ScraperService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Path("/api/v1/admin")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Admin", description = "Administrative operations")
public class AdminResource {

    @Inject
    ScraperService scraperService;

    @POST
    @Path("/scrape")
    @Operation(summary = "Trigger a full scrape of all retailers (async)")
    public Response scrapeAll() {
        if (scraperService.getStatus().running()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("message", "Scrape already in progress"))
                    .build();
        }
        CompletableFuture.runAsync(scraperService::scrapeAll);
        return Response.accepted(Map.of("message", "Scrape started. Check GET /status for progress.")).build();
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
}
