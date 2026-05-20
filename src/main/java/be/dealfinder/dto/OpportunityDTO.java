package be.dealfinder.dto;

import java.math.BigDecimal;

/**
 * The single biggest savings opportunity in Belgium today — the home banner's
 * payload ("Vandaag bespaar je het meest op …"). Ranked by absolute € saving
 * vs the product's recent expected price. Null when no fingerprinted product
 * clears the confidence + price-history bar (cold-start: banner hides).
 */
public record OpportunityDTO(
    String fingerprint,
    String canonicalName,
    String canonicalImageUrl,
    String category,
    String cheapestRetailer,
    BigDecimal currentPrice,
    BigDecimal expectedPrice,
    BigDecimal savingEur,
    Integer discountPercentage
) {}
