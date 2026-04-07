package be.dealfinder.resource;

import be.dealfinder.service.GraphQLCache;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

@Path("/api/v1/search")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Search", description = "Cross-retailer product search powered by myShopi GraphQL")
public class SearchResource {

    private static final String GRAPHQL_URL = "https://api.jafolders.com/graphql";
    private static final String CONTEXT_HEADER = "myshopi;nl;web;1;1";

    private static final String SEARCH_QUERY = """
        query($search: String!, $limit: Int!, $offset: Int!) {
          offers(offers: { search: $search }, pagination: { limit: $limit, offset: $offset }) {
            id name discountPercent priceAfterDiscount priceBeforeDiscount
            pageIndex activeFrom expireAfter brandName description
            shop { slug name }
          }
        }
        """;

    @Inject
    GraphQLCache cache;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @GET
    @Operation(summary = "Search deals across all retailers",
               description = "Real-time cross-retailer search via myShopi. Returns matching deals from all 56 available shops, sorted by price (cheapest first).")
    public Response search(
            @Parameter(description = "Search term (product name, brand, etc.)", required = true)
            @QueryParam("q") String query,

            @Parameter(description = "Maximum results (default 50)")
            @QueryParam("limit") @DefaultValue("50") int limit
    ) {
        if (query == null || query.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Search query is required"))
                    .build();
        }

        try {
            List<Map<String, Object>> results = searchOffers(query.trim(), limit);
            return Response.ok(Map.of(
                    "query", query,
                    "count", results.size(),
                    "results", results
            )).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    private List<Map<String, Object>> searchOffers(String searchTerm, int limit) throws Exception {
        String variables = mapper.writeValueAsString(Map.of(
                "search", searchTerm,
                "limit", Math.min(limit, 100),
                "offset", 0
        ));

        String body = mapper.writeValueAsString(Map.of(
                "query", SEARCH_QUERY,
                "variables", mapper.readTree(variables)
        ));

        String cacheKey = "search:" + searchTerm.toLowerCase() + ":" + limit;
        String cached = cache.get(cacheKey);
        String responseBody;

        if (cached != null) {
            responseBody = cached;
        } else {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GRAPHQL_URL))
                    .header("Content-Type", "application/json")
                    .header("jafolders-context", CONTEXT_HEADER)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            responseBody = response.body();
            cache.put(cacheKey, responseBody, 30);
        }

        JsonNode root = mapper.readTree(responseBody);

        if (root.has("errors") && !root.path("errors").isEmpty()) {
            throw new RuntimeException(root.path("errors").get(0).path("message").asText());
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonNode offer : root.path("data").path("offers")) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", offer.path("id").asText());
            result.put("productName", offer.path("name").asText());
            result.put("brandName", textOrNull(offer.path("brandName")));
            result.put("description", textOrNull(offer.path("description")));
            result.put("retailerSlug", offer.path("shop").path("slug").asText());
            result.put("retailerName", offer.path("shop").path("name").asText());

            Double priceAfter = doubleOrNull(offer.path("priceAfterDiscount"));
            Double priceBefore = doubleOrNull(offer.path("priceBeforeDiscount"));
            result.put("currentPrice", priceAfter);
            result.put("originalPrice", priceBefore);

            double discountPct = offer.path("discountPercent").asDouble(0);
            if (discountPct == 0 && priceBefore != null && priceAfter != null && priceBefore > 0) {
                discountPct = ((priceBefore - priceAfter) / priceBefore) * 100;
            }
            result.put("discountPercentage", (int) Math.round(discountPct));

            result.put("activeFrom", textOrNull(offer.path("activeFrom")));
            result.put("expireAfter", textOrNull(offer.path("expireAfter")));
            result.put("pageIndex", offer.path("pageIndex").isNull() ? null : offer.path("pageIndex").asInt());
            results.add(result);
        }

        results.sort(Comparator.comparing(
                r -> r.get("currentPrice") != null ? (Double) r.get("currentPrice") : Double.MAX_VALUE
        ));

        return results;
    }

    private String textOrNull(JsonNode node) {
        return (node == null || node.isNull() || node.isMissingNode()) ? null : node.asText();
    }

    private Double doubleOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        return BigDecimal.valueOf(node.asDouble()).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
