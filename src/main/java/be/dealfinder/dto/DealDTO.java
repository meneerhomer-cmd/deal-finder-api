package be.dealfinder.dto;

import be.dealfinder.entity.Deal;
import be.dealfinder.extraction.ExtractionReader;
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
    String minPurchase,
    String variants,
    String loyaltyCard,
    String department,
    String validDays,
    LocalDate validFrom,
    LocalDate validUntil,
    String imageUrl,
    String sourceUrl,
    boolean expired,
    Long daysExpired,
    boolean expiringSoon,
    BigDecimal lowestPriceSeen,
    boolean atLowestPrice,
    String fingerprint,
    boolean extracted,
    BigDecimal derivedUnitPrice,
    String derivedUnitLabel
) {
    public static DealDTO from(Deal deal, String language, BigDecimal lowestPriceSeen) {
        long daysUntilExpiry = ChronoUnit.DAYS.between(LocalDate.now(), deal.validUntil);
        boolean atLowest = lowestPriceSeen != null
                && deal.currentPrice != null
                && deal.currentPrice.compareTo(lowestPriceSeen) <= 0;
        ExtractionReader.UnitPrice unit = ExtractionReader.unitPrice(deal.extractionJson, deal.currentPrice);
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
            deal.dealType,
            deal.quantity,
            deal.unitPrice,
            deal.brand,
            deal.conditions,
            deal.minPurchase,
            deal.variants,
            deal.loyaltyCard,
            deal.department,
            deal.validDays,
            deal.validFrom,
            deal.validUntil,
            deal.imageUrl,
            deal.sourceUrl,
            deal.isExpired(),
            deal.getDaysExpired(),
            !deal.isExpired() && daysUntilExpiry <= 2,
            lowestPriceSeen,
            atLowest,
            deal.fingerprint,
            deal.extractionJson != null,
            unit != null ? unit.value() : null,
            unit != null ? unit.label() : null
        );
    }

    public static DealDTO from(Deal deal, String language) {
        return from(deal, language, null);
    }

    public static DealDTO from(Deal deal) {
        return from(deal, "en", null);
    }
}
