package be.dealfinder.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Composite key for the lowest-price lookup map. Identifies a product
     * variant within a single retailer's history.
     */
    public record LowestPriceKey(String productNameNormalized, Long retailerId) {}

    /**
     * One batch query for the entire catalog's per-product lowest price within
     * the retention window. Used by DealService to enrich DTOs with the
     * `lowestPriceSeen` field without N+1ing the deal listing endpoint.
     *
     * `HAVING COUNT(DISTINCT p.price) >= 2` filters out products that have
     * only ever been seen at one price — for those, MIN == current trivially
     * and a "lowest price" badge would fire on every card (no signal). The
     * badge only carries meaning when the product has actually moved between
     * prices and the current one is the lowest of that movement.
     *
     * The 90-day window matches ScraperService.cleanupPriceHistory()'s
     * retention so we never query rows that are about to be deleted.
     */
    public static Map<LowestPriceKey, BigDecimal> findLowestPricesPerProductRetailer() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        List<Object[]> rows = getEntityManager()
                .createQuery(
                        "SELECT p.productNameNormalized, p.retailer.id, MIN(p.price) " +
                        "FROM PriceHistory p " +
                        "WHERE p.recordedAt >= :cutoff " +
                        "GROUP BY p.productNameNormalized, p.retailer.id " +
                        "HAVING COUNT(DISTINCT p.price) >= 2",
                        Object[].class)
                .setParameter("cutoff", cutoff)
                .getResultList();
        Map<LowestPriceKey, BigDecimal> map = new HashMap<>(rows.size() * 2);
        for (Object[] row : rows) {
            map.put(new LowestPriceKey((String) row[0], (Long) row[1]), (BigDecimal) row[2]);
        }
        return map;
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
