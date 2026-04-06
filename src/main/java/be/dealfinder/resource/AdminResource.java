package be.dealfinder.resource;

import be.dealfinder.entity.Retailer;
import be.dealfinder.scraper.MyShopScraper;
import be.dealfinder.service.ScraperService;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Path("/api/v1/admin")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Admin", description = "Administrative operations")
public class AdminResource {

    @Inject
    ScraperService scraperService;

    @ConfigProperty(name = "scraper.user-agent")
    String userAgent;

    @ConfigProperty(name = "scraper.timeout", defaultValue = "15000")
    int timeout;

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
        MyShopScraper.ScraperResult result = scraperService.scrapeRetailer(retailerSlug);
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

    @GET
    @Path("/debug/{retailerSlug}")
    @Operation(summary = "Debug: Show what JSoup sees for a retailer page")
    public Response debugScraper(@PathParam("retailerSlug") String retailerSlug) {
        Retailer retailer = Retailer.findBySlug(retailerSlug);
        if (retailer == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Retailer not found: " + retailerSlug))
                    .build();
        }

        try {
            Document doc = Jsoup.connect(retailer.scrapingUrl)
                    .userAgent(userAgent)
                    .timeout(timeout)
                    .get();

            Map<String, Object> debug = new HashMap<>();
            debug.put("url", retailer.scrapingUrl);
            debug.put("title", doc.title());
            debug.put("htmlLength", doc.html().length());

            // Count various selectors
            debug.put("allLinks", doc.select("a").size());
            debug.put("linksWithOffer", doc.select("a[href*='offer']").size());
            debug.put("linksWithFolder", doc.select("a[href*='folder']").size());
            debug.put("linksWithKorting", doc.select("a:contains(korting)").size());
            debug.put("elementsWithKorting", doc.select(":contains(korting)").size());
            debug.put("imagesWithPromotie", doc.select("img[alt*='Promotie']").size());

            // Sample links containing "offer"
            List<Map<String, String>> sampleOffers = new ArrayList<>();
            Elements offerLinks = doc.select("a[href*='offer']");
            int count = 0;
            for (Element link : offerLinks) {
                if (count++ >= 5) break;
                Map<String, String> linkInfo = new HashMap<>();
                linkInfo.put("href", link.attr("href"));
                linkInfo.put("text", link.text().length() > 200 ? link.text().substring(0, 200) + "..." : link.text());
                linkInfo.put("html", link.outerHtml().length() > 500 ? link.outerHtml().substring(0, 500) + "..." : link.outerHtml());
                sampleOffers.add(linkInfo);
            }
            debug.put("sampleOfferLinks", sampleOffers);

            // Sample links containing "korting"
            List<Map<String, String>> sampleKorting = new ArrayList<>();
            Elements kortingLinks = doc.select("a:contains(korting)");
            count = 0;
            for (Element link : kortingLinks) {
                if (count++ >= 5) break;
                Map<String, String> linkInfo = new HashMap<>();
                linkInfo.put("href", link.attr("href"));
                linkInfo.put("text", link.text().length() > 200 ? link.text().substring(0, 200) + "..." : link.text());
                sampleKorting.add(linkInfo);
            }
            debug.put("sampleKortingLinks", sampleKorting);

            // First 2000 chars of body text
            String bodyText = doc.body().text();
            debug.put("bodyTextSample", bodyText.length() > 2000 ? bodyText.substring(0, 2000) + "..." : bodyText);

            return Response.ok(debug).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    public record ScrapeResponse(String message, List<MyShopScraper.ScraperResult> results) {}
}
