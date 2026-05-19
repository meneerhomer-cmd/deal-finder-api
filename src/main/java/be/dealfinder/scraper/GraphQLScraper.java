package be.dealfinder.scraper;

import be.dealfinder.entity.Category;
import be.dealfinder.entity.Deal;
import be.dealfinder.entity.PriceHistory;
import be.dealfinder.entity.Retailer;
import be.dealfinder.extraction.ProductExtraction;
import be.dealfinder.extraction.ProductExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;

@ApplicationScoped
public class GraphQLScraper {

    private static final Logger LOG = Logger.getLogger(GraphQLScraper.class);
    private static final String GRAPHQL_URL = "https://api.jafolders.com/graphql";
    private static final String CONTEXT_HEADER = "myshopi;nl;web;1;1";

    @Inject
    ProductExtractor productExtractor;

    private static final String OFFERS_QUERY = """
        query($shopSlug: String!, $limit: Int!, $offset: Int!) {
          offers(offers: { shopSlug: $shopSlug }, pagination: { limit: $limit, offset: $offset }) {
            id
            name
            discountPercent
            priceAfterDiscount
            priceBeforeDiscount
            pageIndex
            activeFrom
            expireAfter
            brandName
            description
            hotspot {
              ... on HotspotProductEntity {
                fileUrl(version: ORIGINAL)
              }
            }
          }
        }
        """;

    @ConfigProperty(name = "scraper.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "deals.minimum-discount", defaultValue = "20")
    int minimumDiscount;

    @ConfigProperty(name = "scraper.product-extraction.enabled", defaultValue = "true")
    boolean productExtractionEnabled;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public GraphQLScraper.ScraperResult scrapeRetailer(Retailer retailer) {
        if (!enabled) {
            return new GraphQLScraper.ScraperResult(retailer.slug, 0, 0, "Scraper disabled", List.of());
        }

        LOG.info("GraphQL scrape starting for " + retailer.name);
        List<Category> categories = Category.findAllActive();

        try {
            List<JsonNode> allOffers = fetchOffers(retailer.slug);
            LOG.info("GraphQL returned " + allOffers.size() + " offers for " + retailer.name);

            int added = 0;
            int updated = 0;
            List<Long> newDealIds = new ArrayList<>();

            for (JsonNode offer : allOffers) {
                try {
                    int[] result = processOffer(offer, retailer, categories, newDealIds);
                    added += result[0];
                    updated += result[1];
                } catch (Exception e) {
                    LOG.trace("Failed to process offer: " + e.getMessage());
                }
            }

            LOG.info("GraphQL scrape complete for " + retailer.name + ": " + added + " added, " + updated + " updated");
            return new GraphQLScraper.ScraperResult(retailer.slug, added, updated, null, newDealIds);

        } catch (Exception e) {
            LOG.error("GraphQL scrape failed for " + retailer.name + ": " + e.getMessage(), e);
            return new GraphQLScraper.ScraperResult(retailer.slug, 0, 0, e.getMessage(), List.of());
        }
    }

    private List<JsonNode> fetchOffers(String shopSlug) throws Exception {
        List<JsonNode> allOffers = new ArrayList<>();
        int offset = 0;
        int pageSize = 24;

        while (true) {
            String variables = mapper.writeValueAsString(Map.of(
                    "shopSlug", shopSlug,
                    "limit", 500,
                    "offset", offset
            ));

            String body = mapper.writeValueAsString(Map.of(
                    "query", OFFERS_QUERY,
                    "variables", mapper.readTree(variables)
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GRAPHQL_URL))
                    .header("Content-Type", "application/json")
                    .header("jafolders-context", CONTEXT_HEADER)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());

            if (root.has("errors") && !root.get("errors").isEmpty()) {
                throw new RuntimeException("GraphQL error: " + root.get("errors").get(0).get("message").asText());
            }

            JsonNode offers = root.path("data").path("offers");
            int count = 0;
            for (JsonNode offer : offers) {
                allOffers.add(offer);
                count++;
            }

            if (count < pageSize) break;
            offset += pageSize;
            if (offset > 2000) break;
            Thread.sleep(200);
        }

        LOG.info("Fetched " + allOffers.size() + " offers for " + shopSlug);
        return allOffers;
    }

    @Transactional
    int[] processOffer(JsonNode offer, Retailer retailer, List<Category> categories, List<Long> newDealIds) {
        String name = offer.path("name").asText(null);
        if (name == null || name.length() < 3) return new int[]{0, 0};

        double discountPct = offer.path("discountPercent").asDouble(0);
        int discount = (int) Math.round(discountPct);
        if (discount < minimumDiscount && discount > 0) return new int[]{0, 0};

        String offerId = offer.path("id").asText();
        String externalId = "gql-" + retailer.slug + "-" + offerId;

        BigDecimal priceAfter = toBigDecimal(offer.path("priceAfterDiscount"));
        BigDecimal priceBefore = toBigDecimal(offer.path("priceBeforeDiscount"));

        if (priceAfter == null && priceBefore == null) return new int[]{0, 0};

        if (discount == 0 && priceBefore != null && priceAfter != null && priceBefore.compareTo(BigDecimal.ZERO) > 0) {
            discount = priceBefore.subtract(priceAfter)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(priceBefore, 0, RoundingMode.HALF_UP)
                    .intValue();
        }

        if (discount < minimumDiscount) return new int[]{0, 0};

        // Skip mobile-subscription bundle deals. Carrefour publishes phones like "€9
        // met Smart Data abonnement, per smartphone zonder abonnement: 699€" — the
        // headline price requires a 24-month telecom contract, so the 98% is theatre.
        // Heuristic: ≥95% off a ≥€100 item is almost always a bundle in flyer context.
        if (discount >= 95 && priceBefore != null && priceBefore.compareTo(BigDecimal.valueOf(100)) >= 0) {
            Deal stale = Deal.findByExternalId(externalId);
            if (stale != null) stale.delete();
            return new int[]{0, 0};
        }

        LocalDate validFrom = parseDate(offer.path("activeFrom").asText(null));
        LocalDate validUntil = parseDate(offer.path("expireAfter").asText(null));
        if (validUntil == null) validUntil = LocalDate.now().plusDays(7);

        Category category = findBestCategory(name, categories);

        Deal existing = Deal.findByExternalId(externalId);
        if (existing != null) {
            boolean priceChanged = existing.currentPrice != null && priceAfter != null
                    && existing.currentPrice.compareTo(priceAfter) != 0;
            existing.currentPrice = priceAfter;
            existing.originalPrice = priceBefore;
            existing.discountPercentage = discount;
            existing.validUntil = validUntil;
            existing.scrapedAt = LocalDateTime.now();
            if (category != null) existing.category = category;
            existing.persist();
            if (priceChanged) PriceHistory.create(existing).persist();
            return new int[]{0, 1};
        }

        String hotspotImageUrl = offer.path("hotspot").path("fileUrl").asText(null);

        Deal deal = Deal.create(
                name, retailer, priceAfter, priceBefore, discount,
                validFrom != null ? validFrom : LocalDate.now(), validUntil,
                hotspotImageUrl, null, externalId
        );
        deal.category = category;

        if (hotspotImageUrl != null && productExtractionEnabled) {
            ProductExtractor.Listing listing = new ProductExtractor.Listing(
                    name, null, null, retailer.slug, retailer.name);
            productExtractor.extract(hotspotImageUrl, listing).ifPresent(ex -> applyExtraction(deal, ex));
        }

        deal.persist();
        PriceHistory.create(deal).persist();
        newDealIds.add(deal.id);
        return new int[]{1, 0};
    }

    public static void applyExtraction(Deal deal, ProductExtraction ex) {
        deal.brand = ex.brand();
        if (ex.bundleType() != null) deal.dealType = ex.bundleType();
        String qty = formatQuantity(ex.volumeStructure());
        if (qty != null) deal.quantity = qty;
        String unit = formatFlyerUnitPrice(ex.flyerUnitPrice());
        if (unit != null) deal.unitPrice = unit;
        deal.loyaltyCard = "loyalty-card-only".equals(ex.trapDetected()) ? "vereist klantenkaart" : null;
        deal.fingerprint = ex.fingerprint();
        deal.extractionJson = ex.rawJson();
    }

    static String formatQuantity(ProductExtraction.VolumeStructure vs) {
        if (vs == null || vs.baseUnit() == null || vs.amountPerUnit() == null) return null;
        String unit = switch (vs.baseUnit()) {
            case "ml", "g" -> vs.baseUnit();
            case "wash" -> " wash";
            case "diaper" -> " stuks";
            default -> "";
        };
        Integer count = vs.unitCount();
        String amount = formatNumber(vs.amountPerUnit());
        if (count != null && count > 1) return count + " x " + amount + unit;
        return amount + unit;
    }

    static String formatFlyerUnitPrice(ProductExtraction.FlyerUnitPrice fup) {
        if (fup == null || fup.value() == null || fup.unit() == null) return null;
        return String.format(java.util.Locale.US, "%.2f/%s", fup.value(), fup.unit());
    }

    private static String formatNumber(double n) {
        return n == Math.floor(n) ? String.valueOf((int) n) : String.valueOf(n);
    }

    private Category findBestCategory(String productName, List<Category> categories) {
        String lower = productName.toLowerCase();
        String[] words = lower.split("[\\s\\-_/(),.]+");
        Set<String> wordSet = new HashSet<>(Arrays.asList(words));

        Category best = null;
        int bestScore = 0;

        for (Category cat : categories) {
            if (cat.keywords == null || cat.keywords.isBlank()) continue;
            int score = 0;
            for (String kw : cat.keywords.toLowerCase().split(",")) {
                kw = kw.trim();
                if (kw.length() < 3) continue;
                if (wordSet.contains(kw)) score += 10;
                else if (kw.length() >= 5 && lower.contains(kw)) score += 5;
            }
            if (score > bestScore) {
                bestScore = score;
                best = cat;
            }
        }
        return best;
    }

    private BigDecimal toBigDecimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        return BigDecimal.valueOf(node.asDouble()).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            return ZonedDateTime.parse(dateStr).toLocalDate();
        } catch (Exception e) {
            return null;
        }
    }

    public record ScraperResult(String retailerSlug, int added, int updated, String error, List<Long> newDealIds) {
        public boolean isSuccess() {
            return error == null || error.isBlank();
        }
    }
}
