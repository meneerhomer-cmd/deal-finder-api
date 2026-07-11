package be.dealfinder.resource;

import be.dealfinder.service.DealService;
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
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
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

    @Inject
    DealService dealService;

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
            SearchOutcome outcome = searchOffersWithKind(query.trim(), limit);
            attachLocalDealIds(outcome.results);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", query);
            body.put("count", outcome.results.size());
            body.put("kind", outcome.kind);
            if (outcome.synonymsUsed != null) body.put("synonymsUsed", outcome.synonymsUsed);
            body.put("results", outcome.results);
            return Response.ok(body).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    private SearchOutcome searchOffersWithKind(String searchTerm, int limit) throws Exception {
        List<Map<String, Object>> primary = executeJafoldersSearch(searchTerm, limit);
        List<Map<String, Object>> filtered = postFilterByTokenPrefix(primary, searchTerm);
        if (!filtered.isEmpty()) return new SearchOutcome("primary", filtered, null);

        // Fallback 1 — per-token AND intersection for multi-word queries.
        // jafolders' search returns 0 for queries like "rode wijn" / "witte wijn aanbieding"
        // because it doesn't split on whitespace. We re-query each token separately and
        // intersect by id, then post-filter to keep word-prefix semantics.
        String[] tokens = searchTerm.trim().split("\\s+");
        if (tokens.length > 1 && primary.isEmpty()) {
            List<Map<String, Object>> intersected = perTokenIntersection(tokens, limit);
            List<Map<String, Object>> intersectedFiltered = postFilterByTokenPrefix(intersected, searchTerm);
            if (!intersectedFiltered.isEmpty()) return new SearchOutcome("intersection", intersectedFiltered, null);
        }

        // Fallback 2 — brand-name synonym map for known recall gaps in jafolders' index.
        // Only fires when we'd otherwise return 0; never replaces real hits. Results are
        // returned WITHOUT the token-prefix filter (the original term doesn't appear in
        // the synonyms by design).
        List<String> synonyms = BRAND_SYNONYMS.get(searchTerm.toLowerCase(Locale.ROOT).trim());
        if (synonyms != null) {
            Set<String> seen = new HashSet<>();
            List<Map<String, Object>> merged = new ArrayList<>();
            for (String syn : synonyms) {
                List<Map<String, Object>> synHits = postFilterByTokenPrefix(
                        executeJafoldersSearch(syn, limit), syn);
                for (Map<String, Object> r : synHits) {
                    if (seen.add(Objects.toString(r.get("id"), ""))) merged.add(r);
                }
            }
            merged.sort(Comparator.comparing(
                    r -> r.get("currentPrice") != null ? (Double) r.get("currentPrice") : Double.MAX_VALUE));
            if (!merged.isEmpty()) {
                List<Map<String, Object>> capped = merged.stream().limit(limit).collect(Collectors.toList());
                return new SearchOutcome("synonym", capped, synonyms);
            }
        }

        return new SearchOutcome("primary", List.of(), null);
    }

    private void attachLocalDealIds(List<Map<String, Object>> results) {
        if (results.isEmpty()) return;

        Map<String, List<Map<String, Object>>> byExternalId = new LinkedHashMap<>();
        for (Map<String, Object> result : results) {
            result.put("dealId", null);
            String slug = Objects.toString(result.get("retailerSlug"), null);
            String offerId = Objects.toString(result.get("id"), null);
            if (slug == null || slug.isBlank() || offerId == null || offerId.isBlank()) continue;
            byExternalId.computeIfAbsent(DealService.externalIdFor(slug, offerId), k -> new ArrayList<>())
                    .add(result);
        }

        Map<String, Long> localIds = dealService.findLocalDealIdsByExternalIds(byExternalId.keySet());
        localIds.forEach((externalId, dealId) ->
                byExternalId.getOrDefault(externalId, List.of())
                        .forEach(result -> result.put("dealId", dealId)));
    }

    private record SearchOutcome(String kind, List<Map<String, Object>> results, List<String> synonymsUsed) {}

    private List<Map<String, Object>> perTokenIntersection(String[] tokens, int limit) throws Exception {
        Map<String, Map<String, Object>> intersection = null;
        for (String token : tokens) {
            if (token.isBlank()) continue;
            List<Map<String, Object>> tokenHits = executeJafoldersSearch(token, Math.min(limit * 3, 100));
            Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
            for (Map<String, Object> r : tokenHits) {
                byId.put(Objects.toString(r.get("id"), ""), r);
            }
            if (intersection == null) {
                intersection = byId;
            } else {
                intersection.keySet().retainAll(byId.keySet());
            }
            if (intersection.isEmpty()) return List.of();
        }
        if (intersection == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>(intersection.values());
        out.sort(Comparator.comparing(
                r -> r.get("currentPrice") != null ? (Double) r.get("currentPrice") : Double.MAX_VALUE));
        return out.stream().limit(limit).collect(Collectors.toList());
    }

    private List<Map<String, Object>> executeJafoldersSearch(String searchTerm, int limit) throws Exception {
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

    /**
     * Brand → Dutch-generic fallback. Fires only when the primary brand query returns 0
     * hits in jafolders. Conservative list — start with brands users have actually tried
     * (and got 0 from) and grow as gaps surface. Keep keys lowercase and trimmed.
     */
    private static final Map<String, List<String>> BRAND_SYNONYMS = Map.ofEntries(
            Map.entry("nutella", List.of("hazelnootpasta", "chocopasta")),
            Map.entry("nespresso", List.of("koffiecapsules", "koffiepads")),
            Map.entry("senseo", List.of("koffiepads")),
            Map.entry("red bull", List.of("energiedrank")),
            Map.entry("redbull", List.of("energiedrank")),
            Map.entry("ariel", List.of("wasmiddel")),
            Map.entry("dreft", List.of("afwasmiddel", "vaatwasmiddel")),
            Map.entry("evian", List.of("mineraalwater", "bronwater")),
            Map.entry("oral-b", List.of("tandenborstel", "tandpasta")),
            Map.entry("oral b", List.of("tandenborstel", "tandpasta")),
            Map.entry("colgate", List.of("tandpasta")),
            Map.entry("nivea", List.of("bodylotion", "huidverzorging"))
    );

    /**
     * Jafolders' GraphQL search does substring matching, so "cola" returns "Chocolade"
     * and "wijn" returns "Wijnglas" (drinking glasses). We re-filter so each
     * whitespace-separated token in the user's query must appear as a word-prefix in
     * productName, brandName, or description — i.e. the token must start at the
     * beginning of the string or immediately after a non-word character.
     *
     * Strict by design: if the filter removes everything, the user sees 0 results.
     * Better empty than misleading; brand-name recall gaps (e.g. nutella → 0 raw hits
     * from jafolders) need a separate fix (custom index or synonym map).
     */
    private static List<Map<String, Object>> postFilterByTokenPrefix(
            List<Map<String, Object>> results, String searchTerm) {
        List<Pattern> tokenPatterns = Arrays.stream(searchTerm.toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(t -> !t.isBlank())
                .map(t -> Pattern.compile("(?:^|\\W)" + Pattern.quote(t), Pattern.CASE_INSENSITIVE))
                .toList();
        if (tokenPatterns.isEmpty()) return results;

        return results.stream()
                .filter(r -> {
                    String haystack = String.join(" ",
                            Objects.toString(r.get("productName"), ""),
                            Objects.toString(r.get("brandName"), ""),
                            Objects.toString(r.get("description"), ""));
                    return tokenPatterns.stream().allMatch(p -> p.matcher(haystack).find());
                })
                .collect(Collectors.toList());
    }

    private String textOrNull(JsonNode node) {
        return (node == null || node.isNull() || node.isMissingNode()) ? null : node.asText();
    }

    private Double doubleOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        return BigDecimal.valueOf(node.asDouble()).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
