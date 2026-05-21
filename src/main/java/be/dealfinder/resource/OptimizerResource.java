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
import java.util.stream.Collectors;

@Path("/api/v1/optimizer")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Optimizer", description = "Shopping list optimization — find cheapest retailers")
public class OptimizerResource {

    private static final String GRAPHQL_URL = "https://api.jafolders.com/graphql";
    private static final String CONTEXT_HEADER = "myshopi;nl;web;1;1";

    @Inject
    GraphQLCache cache;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Optimize a shopping list",
               description = "Takes a list of product names, searches for the cheapest option per product across all retailers, and groups results by retailer for optimal shopping.")
    public Response optimize(List<String> productNames) {
        if (productNames == null || productNames.isEmpty()) {
            return Response.status(400).entity(Map.of("error", "Provide a list of product names")).build();
        }

        try {
            List<Map<String, Object>> items = new ArrayList<>();
            Map<String, Double> retailerTotals = new LinkedHashMap<>();
            // store -> (product -> cheapest price at that store), for the
            // "everything in one shop" option.
            Map<String, Map<String, Double>> storeBaskets = new LinkedHashMap<>();

            for (String product : productNames) {
                List<Map<String, Object>> results = searchProduct(product.trim());

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("searchTerm", product);
                item.put("resultCount", results.size());

                if (!results.isEmpty()) {
                    Map<String, Object> cheapest = results.get(0);
                    item.put("cheapest", cheapest);
                    item.put("allOptions", results.size() > 5 ? results.subList(0, 5) : results);

                    String retailer = (String) cheapest.get("retailerName");
                    Double price = (Double) cheapest.get("currentPrice");
                    if (price != null) {
                        retailerTotals.merge(retailer, price, Double::sum);
                    }

                    // Record each store's cheapest offer for this product (results
                    // are price-sorted, so putIfAbsent keeps the lowest per store).
                    for (Map<String, Object> offer : results) {
                        String store = (String) offer.get("retailerName");
                        Double p = (Double) offer.get("currentPrice");
                        if (store != null && p != null) {
                            storeBaskets.computeIfAbsent(store, k -> new LinkedHashMap<>())
                                    .putIfAbsent(product, p);
                        }
                    }
                } else {
                    item.put("cheapest", null);
                    item.put("allOptions", List.of());
                }

                items.add(item);
            }

            // Best single store: the one carrying the MOST of the list (tie →
            // cheapest basket). Lets the shopper trade a few euros for one stop.
            Map<String, Object> bestSingleStore = null;
            String bestStore = null;
            int bestCount = -1;
            double bestTotal = Double.MAX_VALUE;
            for (Map.Entry<String, Map<String, Double>> e : storeBaskets.entrySet()) {
                int count = e.getValue().size();
                double tot = e.getValue().values().stream().mapToDouble(d -> d).sum();
                if (count > bestCount || (count == bestCount && tot < bestTotal)) {
                    bestCount = count;
                    bestTotal = tot;
                    bestStore = e.getKey();
                }
            }
            if (bestStore != null) {
                List<Map<String, Object>> bsItems = storeBaskets.get(bestStore).entrySet().stream()
                        .map(en -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("searchTerm", en.getKey());
                            m.put("price", Math.round(en.getValue() * 100.0) / 100.0);
                            return m;
                        })
                        .collect(Collectors.toList());
                bestSingleStore = new LinkedHashMap<>();
                bestSingleStore.put("retailerName", bestStore);
                bestSingleStore.put("itemCount", bestCount);
                bestSingleStore.put("estimatedTotal", Math.round(bestTotal * 100.0) / 100.0);
                bestSingleStore.put("items", bsItems);
            }

            // Build retailer summary
            List<Map<String, Object>> retailerSummary = retailerTotals.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .map(e -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("retailerName", e.getKey());
                        r.put("estimatedTotal", Math.round(e.getValue() * 100.0) / 100.0);
                        r.put("itemCount", (int) items.stream()
                                .filter(i -> i.get("cheapest") != null &&
                                        e.getKey().equals(((Map<String, Object>) i.get("cheapest")).get("retailerName")))
                                .count());
                        return r;
                    })
                    .collect(Collectors.toList());

            // Group items by cheapest retailer
            Map<String, List<String>> shoppingRoute = new LinkedHashMap<>();
            for (Map<String, Object> item : items) {
                if (item.get("cheapest") != null) {
                    String retailer = (String) ((Map<String, Object>) item.get("cheapest")).get("retailerName");
                    shoppingRoute.computeIfAbsent(retailer, k -> new ArrayList<>())
                            .add((String) item.get("searchTerm"));
                }
            }

            double totalEstimate = retailerTotals.values().stream().mapToDouble(d -> d).sum();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("totalProducts", productNames.size());
            response.put("totalEstimate", Math.round(totalEstimate * 100.0) / 100.0);
            response.put("stopsNeeded", retailerTotals.size());
            response.put("retailerSummary", retailerSummary);
            response.put("shoppingRoute", shoppingRoute);
            response.put("items", items);
            response.put("bestSingleStore", bestSingleStore);
            return Response.ok(response).build();

        } catch (Exception e) {
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
    }

    private List<Map<String, Object>> searchProduct(String searchTerm) throws Exception {
        String query = "query($s: String!) { offers(offers: { search: $s }, pagination: { limit: 10, offset: 0 }) { id name priceAfterDiscount priceBeforeDiscount discountPercent shop { slug name } } }";
        String body = mapper.writeValueAsString(Map.of(
                "query", query,
                "variables", mapper.readTree(mapper.writeValueAsString(Map.of("s", searchTerm)))
        ));

        String cacheKey = "optim:" + searchTerm.toLowerCase();
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

        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonNode offer : root.path("data").path("offers")) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("productName", offer.path("name").asText());
            r.put("retailerSlug", offer.path("shop").path("slug").asText());
            r.put("retailerName", offer.path("shop").path("name").asText());
            r.put("currentPrice", offer.path("priceAfterDiscount").isNull() ? null : offer.path("priceAfterDiscount").asDouble());
            r.put("originalPrice", offer.path("priceBeforeDiscount").isNull() ? null : offer.path("priceBeforeDiscount").asDouble());
            r.put("discountPercent", (int) Math.round(offer.path("discountPercent").asDouble(0)));
            results.add(r);
        }

        results.sort(Comparator.comparing(r -> r.get("currentPrice") != null ? (Double) r.get("currentPrice") : Double.MAX_VALUE));
        return results;
    }
}
