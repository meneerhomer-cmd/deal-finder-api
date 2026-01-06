package be.dealfinder.dto;

import be.dealfinder.entity.Deal;
import java.math.BigDecimal;
import java.time.LocalDate;

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
    LocalDate validFrom,
    LocalDate validUntil,
    String imageUrl,
    String sourceUrl,
    boolean expired,
    Long daysExpired
) {
    public static DealDTO from(Deal deal, String language) {
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
            deal.validFrom,
            deal.validUntil,
            deal.imageUrl,
            deal.sourceUrl,
            deal.isExpired(),
            deal.getDaysExpired()
        );
    }

    public static DealDTO from(Deal deal) {
        return from(deal, "en");
    }
}
