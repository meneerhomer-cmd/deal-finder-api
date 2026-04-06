package be.dealfinder.dto;

import be.dealfinder.entity.Deal;
import be.dealfinder.entity.Retailer;

import java.time.LocalDate;

public record RetailerDTO(
    Long id,
    String name,
    String slug,
    String logoUrl,
    long dealCount
) {
    public static RetailerDTO from(Retailer retailer) {
        long count = Deal.count("retailer.id = ?1 AND validUntil >= ?2", retailer.id, LocalDate.now());
        return new RetailerDTO(
            retailer.id,
            retailer.name,
            retailer.slug,
            retailer.logoUrl,
            count
        );
    }
}
