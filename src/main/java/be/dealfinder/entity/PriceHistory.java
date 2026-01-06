package be.dealfinder.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "price_history", indexes = {
    @Index(name = "idx_price_product", columnList = "productNameNormalized"),
    @Index(name = "idx_price_retailer", columnList = "retailer_id"),
    @Index(name = "idx_price_recorded", columnList = "recordedAt")
})
public class PriceHistory extends PanacheEntity {

    @Column(nullable = false)
    public String productName;

    @Column(nullable = false)
    public String productNameNormalized;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retailer_id", nullable = false)
    public Retailer retailer;

    @Column(precision = 10, scale = 2)
    public BigDecimal price;

    @Column(precision = 10, scale = 2)
    public BigDecimal originalPrice;

    public Integer discountPercentage;

    @Column(nullable = false)
    public LocalDateTime recordedAt;

    public LocalDate validFrom;
    public LocalDate validUntil;

    // === Panache Finders ===

    public static List<PriceHistory> findByProductAndRetailer(String productNameNormalized, Long retailerId) {
        return find("productNameNormalized = ?1 AND retailer.id = ?2 ORDER BY recordedAt DESC",
                productNameNormalized, retailerId).list();
    }

    public static List<PriceHistory> findByProductAndRetailerLast90Days(String productNameNormalized, Long retailerId) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        return find("productNameNormalized = ?1 AND retailer.id = ?2 AND recordedAt >= ?3 ORDER BY recordedAt DESC",
                productNameNormalized, retailerId, cutoff).list();
    }

    public static PriceHistory findBestDiscount(String productNameNormalized, Long retailerId) {
        return find("productNameNormalized = ?1 AND retailer.id = ?2 ORDER BY discountPercentage DESC",
                productNameNormalized, retailerId).firstResult();
    }

    public static long deleteOlderThan(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return delete("recordedAt < ?1", cutoff);
    }

    // === Helper Methods ===

    public static String normalizeProductName(String productName) {
        if (productName == null) return "";
        return productName.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // === Factory Method ===

    public static PriceHistory create(Deal deal) {
        PriceHistory history = new PriceHistory();
        history.productName = deal.productName;
        history.productNameNormalized = normalizeProductName(deal.productName);
        history.retailer = deal.retailer;
        history.price = deal.currentPrice;
        history.originalPrice = deal.originalPrice;
        history.discountPercentage = deal.discountPercentage;
        history.recordedAt = LocalDateTime.now();
        history.validFrom = deal.validFrom;
        history.validUntil = deal.validUntil;
        return history;
    }
}
