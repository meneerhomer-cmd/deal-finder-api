package be.dealfinder.service;

import be.dealfinder.dto.DealDTO;
import be.dealfinder.entity.Category;
import be.dealfinder.entity.Deal;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
import java.util.*;
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

        // Build query dynamically
        StringBuilder query = new StringBuilder("validUntil >= ?1 AND discountPercentage >= ?2");
        Map<String, Object> params = new HashMap<>();
        int paramIndex = 3;

        // Add retailer filter
        if (retailers != null && !retailers.isEmpty()) {
            query.append(" AND retailer.slug IN (?").append(paramIndex++).append(")");
        }

        // Add category filter
        if (categories != null && !categories.isEmpty()) {
            query.append(" AND category.slug IN (?").append(paramIndex++).append(")");
        }

        // Add search filter
        if (search != null && !search.isBlank()) {
            query.append(" AND LOWER(productName) LIKE ?").append(paramIndex++);
        }

        // Build sort
        Sort sort = buildSort(sortBy, sortOrder);

        // Execute query
        List<Deal> deals;
        if (retailers != null && !retailers.isEmpty() && categories != null && !categories.isEmpty() && search != null && !search.isBlank()) {
            deals = Deal.find(query.toString(), sort, cutoffDate, discount, retailers, categories, "%" + search.toLowerCase() + "%").list();
        } else if (retailers != null && !retailers.isEmpty() && categories != null && !categories.isEmpty()) {
            deals = Deal.find(query.toString(), sort, cutoffDate, discount, retailers, categories).list();
        } else if (retailers != null && !retailers.isEmpty() && search != null && !search.isBlank()) {
            deals = Deal.find(query.toString(), sort, cutoffDate, discount, retailers, "%" + search.toLowerCase() + "%").list();
        } else if (categories != null && !categories.isEmpty() && search != null && !search.isBlank()) {
            deals = Deal.find(query.toString(), sort, cutoffDate, discount, categories, "%" + search.toLowerCase() + "%").list();
        } else if (retailers != null && !retailers.isEmpty()) {
            deals = Deal.find(query.toString(), sort, cutoffDate, discount, retailers).list();
        } else if (categories != null && !categories.isEmpty()) {
            deals = Deal.find(query.toString(), sort, cutoffDate, discount, categories).list();
        } else if (search != null && !search.isBlank()) {
            deals = Deal.find(query.toString(), sort, cutoffDate, discount, "%" + search.toLowerCase() + "%").list();
        } else {
            deals = Deal.find(query.toString(), sort, cutoffDate, discount).list();
        }

        String lang = language != null ? language : "en";
        return deals.stream()
                .map(deal -> DealDTO.from(deal, lang))
                .collect(Collectors.toList());
    }

    public List<DealDTO> findDealsByRetailer(String retailerSlug, String language) {
        List<Deal> deals = Deal.findByRetailer(retailerSlug, expiredVisibleDays);
        String lang = language != null ? language : "en";
        return deals.stream()
                .map(deal -> DealDTO.from(deal, lang))
                .collect(Collectors.toList());
    }

    public Optional<DealDTO> findDealById(Long id, String language) {
        Deal deal = Deal.findById(id);
        if (deal == null) {
            return Optional.empty();
        }
        String lang = language != null ? language : "en";
        return Optional.of(DealDTO.from(deal, lang));
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
        return deals.stream()
                .map(deal -> DealDTO.from(deal, lang))
                .collect(Collectors.groupingBy(DealDTO::retailerSlug));
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
