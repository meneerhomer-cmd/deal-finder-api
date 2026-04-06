package be.dealfinder.resource;

import be.dealfinder.dto.StatsDTO;
import be.dealfinder.entity.Category;
import be.dealfinder.entity.Deal;
import be.dealfinder.entity.Retailer;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Path("/api/v1/stats")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Statistics", description = "Deal statistics")
public class StatsResource {

    @GET
    @Operation(summary = "Get deal statistics overview")
    public StatsDTO getStats() {
        LocalDate today = LocalDate.now();
        LocalDate expiryCutoff = today.plusDays(2);

        List<Deal> activeDeals = Deal.findActive();
        long totalDeals = activeDeals.size();
        long totalRetailers = Retailer.count("active", true);

        double avgDiscount = activeDeals.stream()
                .mapToInt(d -> d.discountPercentage)
                .average()
                .orElse(0);

        Deal bestDeal = activeDeals.stream()
                .max(Comparator.comparingInt(d -> d.discountPercentage))
                .orElse(null);

        long expiringSoon = activeDeals.stream()
                .filter(d -> !d.validUntil.isBefore(today) && !d.validUntil.isAfter(expiryCutoff))
                .count();

        List<StatsDTO.RetailerStat> byRetailer = Retailer.findAllActive().stream()
                .map(r -> new StatsDTO.RetailerStat(
                        r.slug, r.name,
                        Deal.count("retailer.id = ?1 AND validUntil >= ?2", r.id, today)
                ))
                .sorted(Comparator.comparingLong(StatsDTO.RetailerStat::dealCount).reversed())
                .collect(Collectors.toList());

        List<StatsDTO.CategoryStat> topCategories = Category.findAllActive().stream()
                .map(c -> new StatsDTO.CategoryStat(
                        c.slug, c.nameNl,
                        Deal.count("category.id = ?1 AND validUntil >= ?2", c.id, today)
                ))
                .filter(s -> s.dealCount() > 0)
                .sorted(Comparator.comparingLong(StatsDTO.CategoryStat::dealCount).reversed())
                .limit(10)
                .collect(Collectors.toList());

        return new StatsDTO(
                totalDeals,
                totalRetailers,
                Math.round(avgDiscount * 10.0) / 10.0,
                bestDeal != null ? bestDeal.discountPercentage : 0,
                bestDeal != null ? bestDeal.productName : null,
                expiringSoon,
                byRetailer,
                topCategories
        );
    }
}
