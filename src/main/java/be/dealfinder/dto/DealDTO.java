package be.dealfinder.dto;

import be.dealfinder.entity.Deal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record DealDTO(
    Long id,
    String productName,
    String retailerName,
    String retailerSlug,
    String retailerLogoUrl,
    String categoryName,
    String categorySlug,
    BigDecimal currentPrice,
    BigDecimal originalPrice,
    Integer discountPercentage,
    String dealType,
    String quantity,
    String unitPrice,
    String brand,
    String conditions,
    LocalDate validFrom,
    LocalDate validUntil,
    String imageUrl,
    String sourceUrl,
    boolean expired,
    Long daysExpired,
    boolean expiringSoon
) {
    public static DealDTO from(Deal deal, String language) {
        long daysUntilExpiry = ChronoUnit.DAYS.between(LocalDate.now(), deal.validUntil);
        return new DealDTO(
            deal.id,
            deal.productName,
            deal.retailer.name,
            deal.retailer.slug,
            deal.retailer.logoUrl,
            deal.category != null ? deal.category.getName(language) : null,
            deal.category != null ? deal.category.slug : null,
            deal.currentPrice,
            deal.originalPrice,
            deal.discountPercentage,
            deal.dealType != null ? deal.dealType : detectDealType(deal.currentPrice, deal.originalPrice),
            deal.quantity,
            deal.unitPrice,
            deal.brand,
            deal.conditions,
            deal.validFrom,
            deal.validUntil,
            deal.imageUrl,
            deal.sourceUrl,
            deal.isExpired(),
            deal.getDaysExpired(),
            !deal.isExpired() && daysUntilExpiry <= 2
        );
    }

    private static String detectDealType(BigDecimal currentPrice, BigDecimal originalPrice) {
        if (currentPrice == null || originalPrice == null || currentPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        double ratio = originalPrice.doubleValue() / currentPrice.doubleValue();
        if (Math.abs(ratio - 2.0) < 0.05) return "Mogelijk 1+1 gratis";
        if (Math.abs(ratio - 3.0) < 0.05) return "Mogelijk 2+1 gratis";
        return null;
    }

    public static DealDTO from(Deal deal) {
        return from(deal, "en");
    }
}
