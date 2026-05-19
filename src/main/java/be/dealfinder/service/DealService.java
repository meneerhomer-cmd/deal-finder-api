package be.dealfinder.service;

import be.dealfinder.dto.DealDTO;
import be.dealfinder.dto.PagedResponse;
import be.dealfinder.entity.Category;
import be.dealfinder.entity.Deal;
import be.dealfinder.entity.PriceHistory;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
