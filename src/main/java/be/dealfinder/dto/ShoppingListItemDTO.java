package be.dealfinder.dto;

import be.dealfinder.entity.ShoppingListItem;
import java.time.LocalDateTime;

public record ShoppingListItemDTO(
    Long id,
    DealDTO deal,
    LocalDateTime addedAt,
    boolean purchased,
    LocalDateTime purchasedAt
) {
    public static ShoppingListItemDTO from(ShoppingListItem item, String language) {
        return new ShoppingListItemDTO(
            item.id,
            DealDTO.from(item.deal, language),
            item.addedAt,
            item.purchased,
            item.purchasedAt
        );
    }

    public static ShoppingListItemDTO from(ShoppingListItem item) {
        return from(item, "en");
    }
}
