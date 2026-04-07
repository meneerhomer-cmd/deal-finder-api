package be.dealfinder.resource;

import be.dealfinder.service.GraphQLCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
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
@Tag(name = "Flyers", description = "Interactive flyer/folder viewer")
public class FlyerResource {

    private static final String GRAPHQL_URL = "https://api.jafolders.com/graphql";
    private static final String CONTEXT_HEADER = "myshopi;nl;web;1;1";

    @Inject
    GraphQLCache cache;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @GET
    @Path("/{shopSlug}")
    @Operation(summary = "Get active flyers for a retailer with cover images")
    public Response getFlyers(@PathParam("shopSlug") String shopSlug) {
        try {
            String query = String.format("""
                { brochures(brochures: { shopSlug: "%s" }, pagination: { limit: 10, offset: 0 }) {
                    id name activeFrom expireAfter hotspotMode shareUrl
                    cover { id fileUrl(version: LARGE) imageRatio index }
                    shop { slug name }
                } }
                """, shopSlug.replace("\"", ""));

            JsonNode brochures = executeQuery(query, "brochures");
            List<Map<String, Object>> results = new ArrayList<>();
            for (JsonNode b : brochures) {
                Map<String, Object> flyer = new LinkedHashMap<>();
                flyer.put("id", b.path("id").asText());
                flyer.put("name", b.path("name").asText());
                flyer.put("activeFrom", b.path("activeFrom").asText(null));
                flyer.put("expireAfter", b.path("expireAfter").asText(null));
                flyer.put("shareUrl", b.path("shareUrl").asText(null));
                flyer.put("retailerSlug", b.path("shop").path("slug").asText());
                flyer.put("retailerName", b.path("shop").path("name").asText());
                flyer.put("coverImage", b.path("cover").path("fileUrl").asText(null));
                flyer.put("coverRatio", b.path("cover").path("imageRatio").asDouble());
                results.add(flyer);
            }
            return Response.ok(Map.of("shopSlug", shopSlug, "flyers", results)).build();
        } catch (Exception e) {
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/{shopSlug}/{brochureId}/pages")
    @Operation(summary = "Get flyer pages with product hotspot overlays",
               description = "Returns page images + product positions (x,y,w,h as percentages) for interactive viewer")
    public Response getFlyerPages(
            @PathParam("shopSlug") String shopSlug,
            @PathParam("brochureId") String brochureId
    ) {
        try {
            JsonNode offers = executeQuery(String.format("""
                { offers(offers: { brochureId: "%s" }, pagination: { limit: 200, offset: 0 }) {
                    id name discountPercent priceAfterDiscount priceBeforeDiscount
                    brandName pageIndex
                    hotspot { ... on HotspotProductEntity {
                        id x y width height pageId
                        fileUrl(version: ORIGINAL)
                    } }
                } }
                """, brochureId.replace("\"", "")), "offers");

            // Group products by pageId, collect unique page IDs
            Map<String, List<Map<String, Object>>> pageProducts = new LinkedHashMap<>();
            Map<String, Integer> pageIndexMap = new TreeMap<>();

            for (JsonNode offer : offers) {
                JsonNode h = offer.path("hotspot");
                if (h.isMissingNode() || h.isNull() || !h.has("pageId")) continue;

                String pageId = h.path("pageId").asText();
                int pageIndex = offer.path("pageIndex").asInt(-1);
                pageIndexMap.put(pageId, pageIndex);

                Map<String, Object> product = new LinkedHashMap<>();
                product.put("id", offer.path("id").asText());
                product.put("name", offer.path("name").asText());
                product.put("brandName", offer.path("brandName").asText(null));
                product.put("discountPercent", (int) Math.round(offer.path("discountPercent").asDouble(0)));
                product.put("priceAfter", offer.path("priceAfterDiscount").isNull() ? null : offer.path("priceAfterDiscount").asDouble());
                product.put("priceBefore", offer.path("priceBeforeDiscount").isNull() ? null : offer.path("priceBeforeDiscount").asDouble());
                product.put("cropImage", h.path("fileUrl").asText(null));
                product.put("x", h.path("x").asDouble());
                product.put("y", h.path("y").asDouble());
                product.put("width", h.path("width").asDouble());
                product.put("height", h.path("height").asDouble());

                pageProducts.computeIfAbsent(pageId, k -> new ArrayList<>()).add(product);
            }

            // Build pages array with image URLs
            List<Map<String, Object>> pages = new ArrayList<>();
            for (var entry : pageProducts.entrySet()) {
                String pageId = entry.getKey();
                pages.add(Map.of(
                        "pageId", pageId,
                        "pageIndex", pageIndexMap.getOrDefault(pageId, 0),
                        "imageUrl", "https://cdn.jafolders.com/pages/" + pageId + "/large.webp",
                        "products", entry.getValue()
                ));
            }
            pages.sort(Comparator.comparingInt(p -> (int) p.get("pageIndex")));

            return Response.ok(Map.of(
                    "brochureId", brochureId,
                    "shopSlug", shopSlug,
                    "pageCount", pages.size(),
                    "totalProducts", pageProducts.values().stream().mapToInt(List::size).sum(),
                    "pages", pages
            )).build();
        } catch (Exception e) {
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
    }

    private JsonNode executeQuery(String query, String rootField) throws Exception {
        String cacheKey = "flyer:" + rootField + ":" + query.hashCode();
        String cached = cache.get(cacheKey);
        String responseBody;

        if (cached != null) {
            responseBody = cached;
        } else {
            String body = mapper.writeValueAsString(Map.of("query", query));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GRAPHQL_URL))
                    .header("Content-Type", "application/json")
                    .header("jafolders-context", CONTEXT_HEADER)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            responseBody = response.body();
            cache.put(cacheKey, responseBody, 60);
        }

        JsonNode root = mapper.readTree(responseBody);
        if (root.has("errors") && !root.path("errors").isEmpty()) {
            throw new RuntimeException(root.path("errors").get(0).path("message").asText());
        }
        return root.path("data").path(rootField);
    }
}
