package be.dealfinder.resource;

import be.dealfinder.entity.Deal;
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
import java.util.stream.Collectors;

@Path("/api/v1/brands")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Brands", description = "Brand discovery and deal lookup")
public class BrandResource {

    private static final String GRAPHQL_URL = "https://api.jafolders.com/graphql";
    private static final String CONTEXT_HEADER = "myshopi;nl;web;1;1";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @GET
    @Operation(summary = "Get all brands with active deals",
               description = "Aggregates unique brand names from deals in our database (from AI image scanning)")
    public Response getBrands() {
        List<Deal> deals = Deal.listAll();
        Map<String, Set<String>> brandRetailers = new LinkedHashMap<>();

        for (Deal deal : deals) {
            if (deal.brand != null && !deal.brand.isBlank()) {
                brandRetailers.computeIfAbsent(deal.brand, k -> new TreeSet<>())
                        .add(deal.retailer != null ? deal.retailer.name : "Onbekend");
            }
        }

        List<Map<String, Object>> brands = brandRetailers.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("retailers", e.getValue());
                    m.put("retailerCount", e.getValue().size());
                    return m;
                })
                .sorted(Comparator.<Map<String, Object>, Integer>comparing(m -> (int) m.get("retailerCount")).reversed()
                        .thenComparing(m -> (String) m.get("name")))
                .collect(Collectors.toList());

        return Response.ok(Map.of("count", brands.size(), "brands", brands)).build();
    }

    @GET
    @Path("/{brandName}/deals")
    @Operation(summary = "Get deals for a specific brand across all retailers")
    public Response getDealsByBrand(
            @PathParam("brandName") String brandName,
            @QueryParam("limit") @DefaultValue("50") int limit
    ) {
        try {
            String queryStr = String.format("""
                query($search: String!, $limit: Int!) {
                  offers(offers: { search: $search }, pagination: { limit: $limit, offset: 0 }) {
                    id name brandName discountPercent priceAfterDiscount priceBeforeDiscount
                    activeFrom expireAfter description shop { slug name }
                  }
                }
                """);

            String body = mapper.writeValueAsString(Map.of(
                    "query", queryStr,
                    "variables", mapper.readTree(mapper.writeValueAsString(
                            Map.of("search", brandName, "limit", Math.min(limit, 100))))
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GRAPHQL_URL))
                    .header("Content-Type", "application/json")
                    .header("jafolders-context", CONTEXT_HEADER)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            JsonNode offers = root.path("data").path("offers");

            List<Map<String, Object>> results = new ArrayList<>();
            for (JsonNode offer : offers) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", offer.path("id").asText());
                r.put("productName", offer.path("name").asText());
                r.put("brandName", offer.path("brandName").asText(null));
                r.put("retailerSlug", offer.path("shop").path("slug").asText());
                r.put("retailerName", offer.path("shop").path("name").asText());
                r.put("currentPrice", offer.path("priceAfterDiscount").isNull() ? null : offer.path("priceAfterDiscount").asDouble());
                r.put("originalPrice", offer.path("priceBeforeDiscount").isNull() ? null : offer.path("priceBeforeDiscount").asDouble());
                r.put("discountPercentage", (int) Math.round(offer.path("discountPercent").asDouble(0)));
                results.add(r);
            }

            results.sort(Comparator.comparing(r -> r.get("currentPrice") != null ? (Double) r.get("currentPrice") : Double.MAX_VALUE));

            return Response.ok(Map.of("brand", brandName, "count", results.size(), "deals", results)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage())).build();
        }
    }
}
