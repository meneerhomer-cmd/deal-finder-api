package be.dealfinder.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Path("/api/v1/flyers")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Flyers", description = "Interactive flyer/folder viewer data")
public class FlyerResource {

    private static final String GRAPHQL_URL = "https://api.jafolders.com/graphql";
    private static final String CONTEXT_HEADER = "myshopi;nl;web;1;1";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @GET
    @Path("/{shopSlug}")
    @Operation(summary = "Get active flyers/folders for a retailer",
               description = "Returns brochure metadata with cover images")
    public Response getFlyers(@PathParam("shopSlug") String shopSlug) {
        try {
            String query = String.format("""
                {
                  brochures(brochures: { shopSlug: "%s" }, pagination: { limit: 10, offset: 0 }) {
                    id name activeFrom expireAfter hotspotMode
                    cover { id fileUrl imageRatio index }
                    shop { slug name }
                    shareUrl
                  }
                }
                """, shopSlug.replace("\"", ""));

            JsonNode brochures = executeQuery(query, "brochures");
            List<Map<String, Object>> results = new ArrayList<>();
            for (JsonNode b : brochures) {
                Map<String, Object> flyer = new LinkedHashMap<>();
                flyer.put("id", b.path("id").asText());
                flyer.put("name", b.path("name").asText());
                flyer.put("activeFrom", b.path("activeFrom").asText(null));
                flyer.put("expireAfter", b.path("expireAfter").asText(null));
                flyer.put("hotspotMode", b.path("hotspotMode").asText(null));
                flyer.put("shareUrl", b.path("shareUrl").asText(null));
                flyer.put("retailerSlug", b.path("shop").path("slug").asText());
                flyer.put("retailerName", b.path("shop").path("name").asText());
                flyer.put("coverImage", b.path("cover").path("fileUrl").asText(null));
                results.add(flyer);
            }
            return Response.ok(Map.of("shopSlug", shopSlug, "count", results.size(), "flyers", results)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/{shopSlug}/{brochureId}/pages")
    @Operation(summary = "Get all pages of a flyer with product hotspots",
               description = "Returns page images and clickable product regions with x/y coordinates")
    public Response getFlyerPages(
            @PathParam("shopSlug") String shopSlug,
            @PathParam("brochureId") String brochureId
    ) {
        try {
            String offersQuery = String.format("""
                {
                  offers(offers: { brochureId: "%s" }, pagination: { limit: 200, offset: 0 }) {
                    id name discountPercent priceAfterDiscount priceBeforeDiscount
                    pageIndex brandName
                    hotspot {
                      ... on HotspotProductEntity {
                        id x y width height pageId fileUrl
                      }
                    }
                  }
                }
                """, brochureId.replace("\"", ""));

            JsonNode offers = executeQuery(offersQuery, "offers");

            Map<Integer, List<Map<String, Object>>> pageMap = new TreeMap<>();

            for (JsonNode offer : offers) {
                int pageIndex = offer.path("pageIndex").asInt(-1);
                if (pageIndex < 0) continue;

                Map<String, Object> product = new LinkedHashMap<>();
                product.put("id", offer.path("id").asText());
                product.put("name", offer.path("name").asText());
                product.put("brandName", offer.path("brandName").asText(null));
                product.put("discountPercent", (int) Math.round(offer.path("discountPercent").asDouble(0)));
                product.put("priceAfterDiscount", offer.path("priceAfterDiscount").isNull() ? null : offer.path("priceAfterDiscount").asDouble());
                product.put("priceBeforeDiscount", offer.path("priceBeforeDiscount").isNull() ? null : offer.path("priceBeforeDiscount").asDouble());

                JsonNode hotspot = offer.path("hotspot");
                if (!hotspot.isMissingNode() && !hotspot.isNull()) {
                    product.put("hotspot", Map.of(
                            "id", hotspot.path("id").asText(),
                            "x", hotspot.path("x").asDouble(),
                            "y", hotspot.path("y").asDouble(),
                            "width", hotspot.path("width").asDouble(),
                            "height", hotspot.path("height").asDouble(),
                            "pageId", hotspot.path("pageId").asText(),
                            "imageUrl", hotspot.path("fileUrl").asText(null)
                    ));
                }

                pageMap.computeIfAbsent(pageIndex, k -> new ArrayList<>()).add(product);
            }

            List<Map<String, Object>> pages = new ArrayList<>();
            for (var entry : pageMap.entrySet()) {
                pages.add(Map.of(
                        "pageIndex", entry.getKey(),
                        "products", entry.getValue()
                ));
            }

            return Response.ok(Map.of(
                    "brochureId", brochureId,
                    "shopSlug", shopSlug,
                    "pageCount", pages.size(),
                    "totalProducts", pageMap.values().stream().mapToInt(List::size).sum(),
                    "pages", pages
            )).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage())).build();
        }
    }

    private JsonNode executeQuery(String query, String rootField) throws Exception {
        String body = mapper.writeValueAsString(Map.of("query", query));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GRAPHQL_URL))
                .header("Content-Type", "application/json")
                .header("jafolders-context", CONTEXT_HEADER)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = mapper.readTree(response.body());

        if (root.has("errors") && !root.path("errors").isEmpty()) {
            throw new RuntimeException(root.path("errors").get(0).path("message").asText());
        }

        return root.path("data").path(rootField);
    }
}
