package be.dealfinder.dto;

import be.dealfinder.entity.Retailer;

public record RetailerDTO(
    Long id,
    String name,
    String slug,
    String logoUrl
) {
    public static RetailerDTO from(Retailer retailer) {
        return new RetailerDTO(
            retailer.id,
            retailer.name,
            retailer.slug,
            retailer.logoUrl
        );
    }
}
