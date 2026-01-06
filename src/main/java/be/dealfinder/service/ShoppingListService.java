package be.dealfinder.service;

import be.dealfinder.dto.ShoppingListItemDTO;
import be.dealfinder.entity.Deal;
import be.dealfinder.entity.ShoppingListItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class ShoppingListService {

    public List<ShoppingListItemDTO> getShoppingList(String sessionId, String language) {
        List<ShoppingListItem> items = ShoppingListItem.findBySession(sessionId);
        String lang = language != null ? language : "en";
        return items.stream()
                .map(item -> ShoppingListItemDTO.from(item, lang))
                .collect(Collectors.toList());
    }

    public List<ShoppingListItemDTO> getActiveItems(String sessionId, String language) {
        List<ShoppingListItem> items = ShoppingListItem.findBySessionNotPurchased(sessionId);
        String lang = language != null ? language : "en";
        return items.stream()
                .map(item -> ShoppingListItemDTO.from(item, lang))
                .collect(Collectors.toList());
    }

    public List<ShoppingListItemDTO> getPurchasedItems(String sessionId, String language) {
        List<ShoppingListItem> items = ShoppingListItem.findBySessionPurchased(sessionId);
        String lang = language != null ? language : "en";
        return items.stream()
                .map(item -> ShoppingListItemDTO.from(item, lang))
                .collect(Collectors.toList());
    }

    @Transactional
    public ShoppingListItemDTO addToList(String sessionId, Long dealId, String language) {
        // Check if already in list
        ShoppingListItem existing = ShoppingListItem.findBySessionAndDeal(sessionId, dealId);
        if (existing != null) {
            return ShoppingListItemDTO.from(existing, language != null ? language : "en");
        }

        // Find deal
        Deal deal = Deal.findById(dealId);
        if (deal == null) {
            throw new NotFoundException("Deal not found: " + dealId);
        }

        // Create new item
        ShoppingListItem item = ShoppingListItem.create(sessionId, deal);
        item.persist();

        return ShoppingListItemDTO.from(item, language != null ? language : "en");
    }

    @Transactional
    public void removeFromList(String sessionId, Long dealId) {
        ShoppingListItem item = ShoppingListItem.findBySessionAndDeal(sessionId, dealId);
        if (item != null) {
            item.delete();
        }
    }

    @Transactional
    public ShoppingListItemDTO markPurchased(String sessionId, Long dealId, String language) {
        ShoppingListItem item = ShoppingListItem.findBySessionAndDeal(sessionId, dealId);
        if (item == null) {
            throw new NotFoundException("Item not found in shopping list");
        }

        item.markPurchased();
        item.persist();

        return ShoppingListItemDTO.from(item, language != null ? language : "en");
    }

    @Transactional
    public ShoppingListItemDTO markNotPurchased(String sessionId, Long dealId, String language) {
        ShoppingListItem item = ShoppingListItem.findBySessionAndDeal(sessionId, dealId);
        if (item == null) {
            throw new NotFoundException("Item not found in shopping list");
        }

        item.markNotPurchased();
        item.persist();

        return ShoppingListItemDTO.from(item, language != null ? language : "en");
    }

    @Transactional
    public void clearList(String sessionId) {
        ShoppingListItem.deleteBySession(sessionId);
    }

    @Transactional
    public void clearPurchased(String sessionId) {
        List<ShoppingListItem> purchased = ShoppingListItem.findBySessionPurchased(sessionId);
        for (ShoppingListItem item : purchased) {
            item.delete();
        }
    }

    public long getItemCount(String sessionId) {
        return ShoppingListItem.countBySession(sessionId);
    }
}
