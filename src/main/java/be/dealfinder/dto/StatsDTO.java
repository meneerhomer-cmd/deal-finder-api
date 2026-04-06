package be.dealfinder.dto;

import java.util.List;

public record StatsDTO(
    long totalDeals,
    long totalRetailers,
    double avgDiscount,
    int bestDiscount,
    String bestDealProduct,
    long expiringSoonCount,
    List<RetailerStat> byRetailer,
    List<CategoryStat> topCategories
) {
    public record RetailerStat(String slug, String name, long dealCount) {}
    public record CategoryStat(String slug, String name, long dealCount) {}
}
