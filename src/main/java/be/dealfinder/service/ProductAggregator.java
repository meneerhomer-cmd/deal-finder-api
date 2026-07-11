package be.dealfinder.service;

import be.dealfinder.entity.Deal;
import be.dealfinder.entity.Product;
import be.dealfinder.extraction.ExtractionReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aggregates fingerprinted Deal rows into the canonical Product registry —
 * one Product per fingerprint. Runs on a schedule and on demand via
 * AdminResource. Mirrors ScraperService's scheduled-job style.
 */
@ApplicationScoped
public class ProductAggregator {

    private static final Logger LOG = Logger.getLogger(ProductAggregator.class);

    @ConfigProperty(name = "product.aggregation.enabled", defaultValue = "true")
    boolean enabled;

    private final ObjectMapper mapper = new ObjectMapper();

    @Scheduled(cron = "{product.aggregation.cron}")
    void scheduledAggregate() {
        if (!enabled) return;
        AggregateResult result = aggregateAll();
        LOG.infof("Product aggregation (scheduled): %s", result);
    }

    @Transactional
    public AggregateResult aggregateAll() {
        LocalDate today = LocalDate.now();
        List<Deal> deals = Deal.find("fingerprint is not null and validUntil >= ?1", today).list();

        Map<String, List<Deal>> byFingerprint = deals.stream()
                .collect(Collectors.groupingBy(d -> d.fingerprint));

        for (Map.Entry<String, List<Deal>> entry : byFingerprint.entrySet()) {
            upsertProduct(entry.getKey(), entry.getValue());
        }

        Set<String> live = byFingerprint.keySet();
        long deleted = live.isEmpty()
                ? Product.deleteAll()
                : Product.delete("fingerprint not in ?1", live);

        AggregateResult result = new AggregateResult(byFingerprint.size(), (int) deleted, deals.size());
        LOG.infof("Product aggregation: %s", result);
        return result;
    }

    private void upsertProduct(String fingerprint, List<Deal> group) {
        Deal canonical = pickCanonical(group);
        JsonNode ex = parse(canonical.extractionJson);

        Product p = Product.findByFingerprint(fingerprint);
        if (p == null) {
            p = new Product();
            p.fingerprint = fingerprint;
            p.firstSeenAt = LocalDateTime.now();
        }

        p.category = text(ex, "category");
        p.brand = text(ex, "brand");
        p.productLine = text(ex, "productLine");
        p.style = text(ex, "style");
        p.variantFamily = text(ex, "variantFamily");
        p.categoryAttributesJson = ex != null && ex.has("categoryAttributes") && !ex.get("categoryAttributes").isNull()
                ? ex.get("categoryAttributes").toString() : null;

        p.canonicalName = canonical.productName;
        p.canonicalImageUrl = canonical.imageUrl;

        p.dealCount = group.size();
        p.retailerCount = (int) group.stream()
                .filter(d -> d.retailer != null)
                .map(d -> d.retailer.id)
                .distinct().count();

        // Every member must be comparison-grade. One brand-level deal in the group is enough
        // to make "goedkoopst bij X — bespaar €Y" a claim about two different products.
        p.comparisonGrade = group.stream()
                .allMatch(d -> ExtractionReader.isComparisonGrade(d.extractionJson));
        p.minCurrentPrice = group.stream()
                .map(d -> d.currentPrice)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);

        p.lastComputedAt = LocalDateTime.now();
        p.persist();
    }

    /** Highest extraction confidence wins; ties fall back to most recent scrape. */
    private Deal pickCanonical(List<Deal> group) {
        return group.stream()
                .max(Comparator
                        .comparingDouble(this::confidenceOf)
                        .thenComparing(d -> d.scrapedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(group.get(0));
    }

    private double confidenceOf(Deal d) {
        JsonNode ex = parse(d.extractionJson);
        return ex != null ? ex.path("confidence").asDouble(0.0) : 0.0;
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.path(field);
        if (v.isNull() || v.isMissingNode()) return null;
        String s = v.asText();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    public record AggregateResult(int upserted, int deleted, int scannedDeals) {}
}
