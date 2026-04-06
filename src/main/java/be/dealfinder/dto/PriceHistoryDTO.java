package be.dealfinder.dto;

import be.dealfinder.entity.PriceHistory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PriceHistoryDTO(
    Long id,
    BigDecimal price,
    BigDecimal originalPrice,
    Integer discountPercentage,
    LocalDateTime recordedAt
) {
    public static PriceHistoryDTO from(PriceHistory history) {
        return new PriceHistoryDTO(
            history.id,
            history.price,
            history.originalPrice,
            history.discountPercentage,
            history.recordedAt
        );
    }
}
