package be.dealfinder.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Canonical product registry — one row per fingerprint, aggregated from the
 * Deal rows that share that fingerprint. Kept in sync by ProductAggregator.
 *
 * Loosely coupled to Deal via the fingerprint string (no FK), mirroring how
 * PriceHistory keys on productNameNormalized.
 */
@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_fingerprint", columnList = "fingerprint", unique = true),
    @Index(name = "idx_product_category", columnList = "category"),
    @Index(name = "idx_product_brand", columnList = "brand"),
    @Index(name = "idx_product_style", columnList = "style")
})
public class Product extends PanacheEntity {

    @Column(nullable = false, unique = true, length = 500)
    public String fingerprint;

    // Decomposed identity (from the canonical deal's extractionJson — preserves display casing)
    public String category;
    public String brand;
    public String productLine;
    public String style;
    public String variantFamily;

    // Canonical display
    public String canonicalName;

    @Column(length = 1000)
    public String canonicalImageUrl;

    @Column(columnDefinition = "TEXT")
    public String categoryAttributesJson;

    // Aggregates (refreshed each worker run; may be up to ~1h stale)
    public Integer dealCount;
    public Integer retailerCount;

    /**
     * True only when every deal in this group names a real product line at sufficient
     * confidence. False means the fingerprint is brand-level ("any Pampers"), so the group
     * may hold different SKUs — group them, list them, but never claim a saving across them.
     */
    public Boolean comparisonGrade;

    @Column(precision = 10, scale = 2)
    public BigDecimal minCurrentPrice;

    public LocalDateTime firstSeenAt;
    public LocalDateTime lastComputedAt;

    public static Product findByFingerprint(String fingerprint) {
        return find("fingerprint", fingerprint).firstResult();
    }
}
