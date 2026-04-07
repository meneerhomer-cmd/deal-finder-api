package be.dealfinder.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Entity
@Table(name = "deals", indexes = {
    @Index(name = "idx_deal_retailer", columnList = "retailer_id"),
    @Index(name = "idx_deal_category", columnList = "category_id"),
    @Index(name = "idx_deal_discount", columnList = "discountPercentage"),
    @Index(name = "idx_deal_valid_until", columnList = "validUntil")
})
public class Deal extends PanacheEntity {

    @Column(nullable = false)
    public String productName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retailer_id", nullable = false)
    public Retailer retailer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    public Category category;

    @Column(precision = 10, scale = 2)
    public BigDecimal currentPrice;

    @Column(precision = 10, scale = 2)
    public BigDecimal originalPrice;

    @Column(nullable = false)
    public Integer discountPercentage;

    public LocalDate validFrom;

    @Column(nullable = false)
    public LocalDate validUntil;

    public String imageUrl;

    @Column(length = 500)
    public String sourceUrl;

    @Column(nullable = false)
    public LocalDateTime scrapedAt;

    @Column(unique = true)
    public String externalId;

    // Image analysis fields (populated by DealImageAnalyzer)
    public String dealType;
    public String quantity;
    public String unitPrice;
    public String brand;
    public String conditions;

    // === Computed Properties ===

    @Transient
    public boolean isExpired() {
        return validUntil.isBefore(LocalDate.now());
    }

    @Transient
    public long getDaysExpired() {
        if (!isExpired()) return 0;
        return ChronoUnit.DAYS.between(validUntil, LocalDate.now());
    }

    @Transient
    public boolean isRecentlyExpired(int days) {
        return isExpired() && getDaysExpired() <= days;
    }

    // === Panache Finders ===

    public static List<Deal> findActive() {
        return find("validUntil >= ?1", LocalDate.now()).list();
    }

    public static List<Deal> findActiveAndRecentlyExpired(int expiredDays) {
        LocalDate cutoffDate = LocalDate.now().minusDays(expiredDays);
        return find("validUntil >= ?1", Sort.descending("discountPercentage"), cutoffDate).list();
    }

    public static List<Deal> findByRetailer(String retailerSlug, int expiredDays) {
        LocalDate cutoffDate = LocalDate.now().minusDays(expiredDays);
        return find("retailer.slug = ?1 AND validUntil >= ?2",
                Sort.descending("discountPercentage"), retailerSlug, cutoffDate).list();
    }

    public static List<Deal> findByCategory(String categorySlug, int expiredDays) {
        LocalDate cutoffDate = LocalDate.now().minusDays(expiredDays);
        return find("category.slug = ?1 AND validUntil >= ?2",
                Sort.descending("discountPercentage"), categorySlug, cutoffDate).list();
    }

    public static List<Deal> findByMinDiscount(int minDiscount, int expiredDays) {
        LocalDate cutoffDate = LocalDate.now().minusDays(expiredDays);
        return find("discountPercentage >= ?1 AND validUntil >= ?2",
                Sort.descending("discountPercentage"), minDiscount, cutoffDate).list();
    }

    public static List<Deal> search(String query, int expiredDays) {
        LocalDate cutoffDate = LocalDate.now().minusDays(expiredDays);
        return find("LOWER(productName) LIKE ?1 AND validUntil >= ?2",
                Sort.descending("discountPercentage"),
                "%" + query.toLowerCase() + "%", cutoffDate).list();
    }

    public static Deal findByExternalId(String externalId) {
        return find("externalId", externalId).firstResult();
    }

    public static long deleteExpiredOlderThan(int days) {
        LocalDate cutoffDate = LocalDate.now().minusDays(days);
        ShoppingListItem.delete("deal.id IN (SELECT d.id FROM Deal d WHERE d.validUntil < ?1)", cutoffDate);
        return delete("validUntil < ?1", cutoffDate);
    }

    /**
     * Find deals with eagerly fetched retailer and category to avoid N+1 queries.
     */
    public static List<Deal> findWithRelations(String whereClause, Sort sort, Object... params) {
        String jpql = "FROM Deal d LEFT JOIN FETCH d.retailer LEFT JOIN FETCH d.category WHERE " + whereClause;
        var query = Deal.find(jpql, sort, params);
        return query.list();
    }

    // === Factory Method ===

    public static Deal create(String productName, Retailer retailer, BigDecimal currentPrice,
                              BigDecimal originalPrice, Integer discountPercentage,
                              LocalDate validFrom, LocalDate validUntil,
                              String imageUrl, String sourceUrl, String externalId) {
        Deal deal = new Deal();
        deal.productName = productName;
        deal.retailer = retailer;
        deal.currentPrice = currentPrice;
        deal.originalPrice = originalPrice;
        deal.discountPercentage = discountPercentage;
        deal.validFrom = validFrom;
        deal.validUntil = validUntil;
        deal.imageUrl = imageUrl;
        deal.sourceUrl = sourceUrl;
        deal.externalId = externalId;
        deal.scrapedAt = LocalDateTime.now();
        return deal;
    }
}
