package be.dealfinder.service;

import be.dealfinder.dto.DealDTO;
import be.dealfinder.dto.OpportunityDTO;
import be.dealfinder.dto.PagedResponse;
import be.dealfinder.entity.Category;
import be.dealfinder.entity.Deal;
import be.dealfinder.entity.MatchCorrection;
import be.dealfinder.entity.PriceHistory;
import be.dealfinder.entity.Product;
import be.dealfinder.extraction.ExtractionReader;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class DealService {

    @ConfigProperty(name = "deals.expired-visible-days", defaultValue = "3")
    int expiredVisibleDays;

    @ConfigProperty(name = "deals.minimum-discount", defaultValue = "20")
    int defaultMinDiscount;

    /** Substitute matcher: both source and candidate must be at least this confident. */
    private static final double SUBSTITUTE_CONFIDENCE_FLOOR = 0.80;
    /** Substitute must be at least this much cheaper per unit (€/L, €/kg, …). */
    private static final BigDecimal SUBSTITUTE_MAX_UNIT_RATIO = new BigDecimal("0.80");
    /** …and save at least this many euros in absolute terms (a tip worth taking). */
    private static final BigDecimal SUBSTITUTE_MIN_ABS_SAVING = new BigDecimal("1.50");

    /** Home banner only trusts high-confidence products with real price depth. */
    private static final double OPPORTUNITY_CONFIDENCE_FLOOR = 0.90;
    private static final int OPPORTUNITY_MIN_HISTORY_DAYS = 30;

    public List<DealDTO> findDeals(
            List<String> retailers,
            List<String> categories,
            Integer minDiscount,
            String search,
            String sortBy,
            String sortOrder,
            String language
    ) {
        LocalDate cutoffDate = LocalDate.now().minusDays(expiredVisibleDays);
        int discount = minDiscount != null ? minDiscount : defaultMinDiscount;

        StringBuilder query = new StringBuilder("validUntil >= ?1 AND discountPercentage >= ?2");
        List<Object> params = new ArrayList<>();
        params.add(cutoffDate);
        params.add(discount);
        int paramIndex = 3;

        if (retailers != null && !retailers.isEmpty()) {
            query.append(" AND retailer.slug IN (?").append(paramIndex++).append(")");
            params.add(retailers);
        }

        if (categories != null && !categories.isEmpty()) {
            query.append(" AND category.slug IN (?").append(paramIndex++).append(")");
            params.add(categories);
        }

        if (search != null && !search.isBlank()) {
            query.append(" AND LOWER(productName) LIKE ?").append(paramIndex++);
            params.add("%" + search.toLowerCase() + "%");
        }

        Sort sort = buildSort(sortBy, sortOrder);
        List<Deal> deals = Deal.findWithRelations(query.toString(), sort, params.toArray());

        String lang = language != null ? language : "en";
        Map<PriceHistory.LowestPriceKey, BigDecimal> lows = PriceHistory.findLowestPricesPerProductRetailer();
        return deals.stream()
                .map(deal -> DealDTO.from(deal, lang, lookupLowest(lows, deal)))
                .collect(Collectors.toList());
    }

    public PagedResponse<DealDTO> findDealsPaged(
            List<String> retailers, List<String> categories, Integer minDiscount,
            String search, String sortBy, String sortOrder, String language,
            int page, int pageSize
    ) {
        LocalDate cutoffDate = LocalDate.now().minusDays(expiredVisibleDays);
        int discount = minDiscount != null ? minDiscount : defaultMinDiscount;

        StringBuilder query = new StringBuilder("validUntil >= ?1 AND discountPercentage >= ?2");
        List<Object> params = new ArrayList<>();
        params.add(cutoffDate);
        params.add(discount);
        int paramIndex = 3;

        if (retailers != null && !retailers.isEmpty()) {
            query.append(" AND retailer.slug IN (?").append(paramIndex++).append(")");
            params.add(retailers);
        }
        if (categories != null && !categories.isEmpty()) {
            query.append(" AND category.slug IN (?").append(paramIndex++).append(")");
            params.add(categories);
        }
        if (search != null && !search.isBlank()) {
            query.append(" AND LOWER(productName) LIKE ?").append(paramIndex++);
            params.add("%" + search.toLowerCase() + "%");
        }

        Sort sort = buildSort(sortBy, sortOrder);
        long totalItems = Deal.count(query.toString(), params.toArray());
        List<Deal> deals = Deal.find(query.toString(), sort, params.toArray())
                .page(page, pageSize)
                .list();

        String lang = language != null ? language : "en";
        Map<PriceHistory.LowestPriceKey, BigDecimal> lows = PriceHistory.findLowestPricesPerProductRetailer();
        List<DealDTO> dtos = deals.stream()
                .map(d -> DealDTO.from(d, lang, lookupLowest(lows, d)))
                .collect(Collectors.toList());
        return PagedResponse.of(dtos, totalItems, page, pageSize);
    }

    public List<DealDTO> findDealsByRetailer(String retailerSlug, String language) {
        List<Deal> deals = Deal.findByRetailer(retailerSlug, expiredVisibleDays);
        String lang = language != null ? language : "en";
        Map<PriceHistory.LowestPriceKey, BigDecimal> lows = PriceHistory.findLowestPricesPerProductRetailer();
        return deals.stream()
                .map(deal -> DealDTO.from(deal, lang, lookupLowest(lows, deal)))
                .collect(Collectors.toList());
    }

    public Optional<DealDTO> findDealById(Long id, String language) {
        Deal deal = Deal.findById(id);
        if (deal == null) {
            return Optional.empty();
        }
        String lang = language != null ? language : "en";
        // Same semantic as the batch finder: only return a meaningful "lowest
        // seen" when the product has actually moved between ≥2 distinct prices.
        // Otherwise the badge would fire on every never-changed deal (no signal).
        String normalized = PriceHistory.normalizeProductName(deal.productName);
        List<PriceHistory> history = PriceHistory.findByProductAndRetailerLast90Days(normalized, deal.retailer.id);
        long distinctPrices = history.stream().map(p -> p.price).filter(Objects::nonNull).distinct().count();
        BigDecimal low = distinctPrices >= 2
                ? history.stream().map(p -> p.price).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(null)
                : null;
        return Optional.of(DealDTO.from(deal, lang, low));
    }

    /**
     * "Same brand at other retailers" panel on the deal-detail page. Originally
     * tried strict matching on first significant word of productName + quantity
     * — but retailers describe the same brand's products with wildly different
     * first words ("Galler chocolade" vs "Chocoladerepen Galler"), so coverage
     * was 0%. Pivoted to brand-level matching: same brand + different retailer
     * + (same quantity when source has one). The panel UI is titled "Zelfde
     * merk bij andere winkels" so this is honest about what's shown.
     *
     * Filters out three categories of useless matches:
     * - source has no brand → no comparison possible
     * - source brand matches its own retailer name (private label like
     *   Carrefour-branded products) → won't appear elsewhere by definition
     * - source brand suffixed with "merk" / "huismerk" (Kruidvat Merk,
     *   Albert Heijn Huismerk) → same private-label pattern under a
     *   different naming convention
     *
     * Quantity is NOT used as a filter — retailers describe identical
     * physical packaging with wildly different strings ("4 x 65g of 70g"
     * vs "4 x 65 g" vs "4 x 70 g" all mean the same 4-pack of Galler
     * chocolate). Quantity-string matching dropped real cross-retailer
     * coverage to ~0%. The frontend renders each match's own quantity
     * so the user can tell pack sizes apart at a glance.
     */
    public List<DealDTO> findCrossRetailerMatches(Long dealId, String language) {
        Deal source = Deal.findById(dealId);
        if (source == null || source.brand == null || source.retailer == null) {
            return List.of();
        }
        if (isPrivateLabel(source.brand, source.retailer.name)) return List.of();

        List<Deal> matches = Deal.findWithRelations(
                "LOWER(brand) = LOWER(?1) " +
                "AND retailer.id != ?2 " +
                "AND validUntil >= ?3 " +
                "AND discountPercentage >= ?4",
                Sort.ascending("currentPrice"),
                source.brand,
                source.retailer.id,
                LocalDate.now().minusDays(expiredVisibleDays),
                defaultMinDiscount
        );

        String lang = language != null ? language : "en";
        Map<PriceHistory.LowestPriceKey, BigDecimal> lows = PriceHistory.findLowestPricesPerProductRetailer();
        return matches.stream()
                .map(d -> DealDTO.from(d, lang, lookupLowest(lows, d)))
                .collect(Collectors.toList());
    }

    public List<DealDTO> findDealsByFingerprint(String fingerprint, String language) {
        if (fingerprint == null || fingerprint.isBlank()) return List.of();
        List<Deal> matches = Deal.findWithRelations(
                "fingerprint = ?1 AND validUntil >= ?2",
                Sort.ascending("currentPrice"),
                fingerprint,
                LocalDate.now().minusDays(expiredVisibleDays)
        );
        String lang = language != null ? language : "en";
        Map<PriceHistory.LowestPriceKey, BigDecimal> lows = PriceHistory.findLowestPricesPerProductRetailer();
        return matches.stream()
                .map(d -> DealDTO.from(d, lang, lookupLowest(lows, d)))
                .collect(Collectors.toList());
    }

    /**
     * The "Bespaar meer met een vergelijkbare optie" tip: ONE meaningfully
     * cheaper alternative in the same category + style but a different brand.
     * Locked rules (launch plan): same category:style, different brand, both
     * confidence ≥ 0.80, candidate ≥ 20% cheaper per comparable unit AND
     * ≥ €1,50 cheaper in absolute terms. Returns the cheapest qualifying
     * candidate, or empty when none clears the bar (silence beats a weak tip).
     *
     * Matching keys off the fingerprint string itself — format
     * {@code category:style:brand:productLine:variantFamily} — so "same
     * category+style, different brand" is a pure prefix filter in SQL, no
     * per-row extraction parse needed to shortlist.
     */
    public Optional<DealDTO> findSubstitute(Long dealId, String language) {
        Deal source = Deal.findById(dealId);
        if (source == null || source.fingerprint == null || source.fingerprint.isBlank()
                || source.currentPrice == null) {
            return Optional.empty();
        }
        if (ExtractionReader.confidence(source.extractionJson) < SUBSTITUTE_CONFIDENCE_FLOOR) {
            return Optional.empty();
        }
        ExtractionReader.UnitPrice sourceUnit = ExtractionReader.unitPrice(source.extractionJson, source.currentPrice);
        if (sourceUnit == null) return Optional.empty();

        String[] parts = source.fingerprint.split(":");
        if (parts.length < 3) return Optional.empty();
        String stylePrefix = parts[0] + ":" + parts[1] + ":";      // category:style:
        String brandPrefix = stylePrefix + parts[2] + ":";          // category:style:brand:

        List<Deal> candidates = Deal.findWithRelations(
                "fingerprint LIKE ?1 AND fingerprint NOT LIKE ?2 " +
                "AND retailer is not null AND currentPrice is not null AND validUntil >= ?3",
                Sort.ascending("currentPrice"),
                stylePrefix + "%", brandPrefix + "%",
                LocalDate.now().minusDays(expiredVisibleDays));

        BigDecimal maxUnitPrice = sourceUnit.value().multiply(SUBSTITUTE_MAX_UNIT_RATIO);
        Deal best = null;
        BigDecimal bestUnitPrice = null;
        for (Deal c : candidates) {
            if (ExtractionReader.confidence(c.extractionJson) < SUBSTITUTE_CONFIDENCE_FLOOR) continue;
            ExtractionReader.UnitPrice cu = ExtractionReader.unitPrice(c.extractionJson, c.currentPrice);
            if (cu == null || !cu.label().equals(sourceUnit.label())) continue;            // comparable units only
            if (cu.value().compareTo(maxUnitPrice) > 0) continue;                           // not ≥20% cheaper/unit
            if (source.currentPrice.subtract(c.currentPrice).compareTo(SUBSTITUTE_MIN_ABS_SAVING) < 0) continue;
            if (bestUnitPrice == null || cu.value().compareTo(bestUnitPrice) < 0) {
                best = c;
                bestUnitPrice = cu.value();
            }
        }
        if (best == null) return Optional.empty();

        String lang = language != null ? language : "en";
        Map<PriceHistory.LowestPriceKey, BigDecimal> lows = PriceHistory.findLowestPricesPerProductRetailer();
        return Optional.of(DealDTO.from(best, lang, lookupLowest(lows, best)));
    }

    /**
     * The home banner's "Vandaag bespaar je het meest op …" pick. Scans
     * fingerprinted products, keeps only high-confidence ones with ≥30 days of
     * price history, and ranks by absolute € saving vs the recent expected
     * price (max of 30-day average and 30-day 90th percentile). Tie-break:
     * highest discount %. Empty when nothing clears the bar — the banner then
     * hides rather than promote a thin signal.
     */
    public Optional<OpportunityDTO> findBiggestOpportunity(String language) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusDays(30);
        LocalDateTime depthCutoff = now.minusDays(OPPORTUNITY_MIN_HISTORY_DAYS);
        LocalDate activeCutoff = LocalDate.now().minusDays(expiredVisibleDays);

        OpportunityDTO best = null;
        for (Product p : Product.<Product>listAll()) {
            if (p.minCurrentPrice == null) continue;

            List<Deal> deals = Deal.find("fingerprint = ?1 AND validUntil >= ?2", p.fingerprint, activeCutoff).list();
            if (deals.isEmpty()) continue;

            double confidence = deals.stream()
                    .mapToDouble(d -> ExtractionReader.confidence(d.extractionJson))
                    .max().orElse(0.0);
            if (confidence < OPPORTUNITY_CONFIDENCE_FLOOR) continue;

            List<BigDecimal> window = new ArrayList<>();
            LocalDateTime oldest = null;
            for (Deal d : deals) {
                if (d.retailer == null) continue;
                String norm = PriceHistory.normalizeProductName(d.productName);
                for (PriceHistory h : PriceHistory.findByProductAndRetailerLast90Days(norm, d.retailer.id)) {
                    if (h.price == null) continue;
                    if (oldest == null || h.recordedAt.isBefore(oldest)) oldest = h.recordedAt;
                    if (!h.recordedAt.isBefore(windowStart)) window.add(h.price);
                }
            }
            if (oldest == null || oldest.isAfter(depthCutoff) || window.isEmpty()) continue;

            BigDecimal expected = expectedPrice(window);
            BigDecimal saving = expected.subtract(p.minCurrentPrice);
            if (saving.signum() <= 0) continue;

            Deal cheapest = deals.stream()
                    .filter(d -> d.currentPrice != null && d.retailer != null)
                    .min(Comparator.comparing(d -> d.currentPrice))
                    .orElse(null);
            if (cheapest == null) continue;

            OpportunityDTO candidate = new OpportunityDTO(
                    p.fingerprint, p.canonicalName, p.canonicalImageUrl, p.category,
                    cheapest.retailer.name, p.minCurrentPrice, expected, saving,
                    cheapest.discountPercentage);

            if (best == null || beats(candidate, best)) best = candidate;
        }
        return Optional.ofNullable(best);
    }

    private static boolean beats(OpportunityDTO a, OpportunityDTO b) {
        int bySaving = a.savingEur().compareTo(b.savingEur());
        if (bySaving != 0) return bySaving > 0;
        int da = a.discountPercentage() != null ? a.discountPercentage() : 0;
        int db = b.discountPercentage() != null ? b.discountPercentage() : 0;
        return da > db;
    }

    /** expectedPrice = max(30-day average, 30-day 90th percentile). */
    private static BigDecimal expectedPrice(List<BigDecimal> prices) {
        List<BigDecimal> sorted = prices.stream().sorted().collect(Collectors.toList());
        BigDecimal sum = sorted.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(sorted.size()), 2, RoundingMode.HALF_UP);
        int idx = Math.min(sorted.size() - 1, Math.max(0, (int) Math.ceil(0.90 * sorted.size()) - 1));
        return avg.max(sorted.get(idx));
    }

    /** Records a shopper's "verkeerde match?" report for later admin review. */
    @Transactional
    public void reportWrongMatch(Long sourceDealId, Long targetDealId) {
        Deal source = Deal.findById(sourceDealId);
        Deal target = Deal.findById(targetDealId);
        MatchCorrection.report(
                sourceDealId,
                targetDealId,
                source != null ? source.fingerprint : null,
                target != null ? target.fingerprint : null
        ).persist();
    }

    private static boolean isPrivateLabel(String brand, String retailerName) {
        if (brand == null) return true;
        String b = brand.toLowerCase().trim();
        if (b.equalsIgnoreCase(retailerName)) return true;
        // "Kruidvat Merk", "Albert Heijn Huismerk", "Delhaize 365" etc.
        if (b.endsWith(" merk") || b.endsWith(" huismerk")) return true;
        return false;
    }

    public Map<String, List<DealDTO>> findDealsGroupedByRetailer(
            Integer minDiscount,
            String language
    ) {
        LocalDate cutoffDate = LocalDate.now().minusDays(expiredVisibleDays);
        int discount = minDiscount != null ? minDiscount : defaultMinDiscount;

        List<Deal> deals = Deal.find(
                "validUntil >= ?1 AND discountPercentage >= ?2",
                Sort.descending("discountPercentage"),
                cutoffDate, discount
        ).list();

        String lang = language != null ? language : "en";
        Map<PriceHistory.LowestPriceKey, BigDecimal> lows = PriceHistory.findLowestPricesPerProductRetailer();
        return deals.stream()
                .map(deal -> DealDTO.from(deal, lang, lookupLowest(lows, deal)))
                .collect(Collectors.groupingBy(DealDTO::retailerSlug));
    }

    private static BigDecimal lookupLowest(Map<PriceHistory.LowestPriceKey, BigDecimal> lows, Deal deal) {
        String normalized = PriceHistory.normalizeProductName(deal.productName);
        return lows.get(new PriceHistory.LowestPriceKey(normalized, deal.retailer.id));
    }

    @Transactional
    public void assignCategories() {
        List<Category> categories = Category.findAllActive();
        List<Deal> uncategorizedDeals = Deal.find("category IS NULL").list();

        for (Deal deal : uncategorizedDeals) {
            for (Category category : categories) {
                if (category.matchesProduct(deal.productName)) {
                    deal.category = category;
                    deal.persist();
                    break;
                }
            }
        }
    }

    @Transactional
    public long cleanupOldDeals(int daysToKeep) {
        return Deal.deleteExpiredOlderThan(daysToKeep);
    }

    private Sort buildSort(String sortBy, String sortOrder) {
        String field = switch (sortBy != null ? sortBy.toLowerCase() : "discount") {
            case "price" -> "currentPrice";
            case "expiry", "validuntil" -> "validUntil";
            case "name", "product" -> "productName";
            default -> "discountPercentage";
        };

        boolean ascending = "asc".equalsIgnoreCase(sortOrder);

        // For discount, we want highest first by default
        if ("discountPercentage".equals(field) && sortOrder == null) {
            ascending = false;
        }
        // For expiry, we want soonest first by default
        if ("validUntil".equals(field) && sortOrder == null) {
            ascending = true;
        }

        return ascending ? Sort.ascending(field) : Sort.descending(field);
    }
}
