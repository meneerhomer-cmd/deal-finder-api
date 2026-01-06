package be.dealfinder.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "shopping_list_items", indexes = {
    @Index(name = "idx_shopping_session", columnList = "sessionId"),
    @Index(name = "idx_shopping_deal", columnList = "deal_id")
})
public class ShoppingListItem extends PanacheEntity {

    @Column(nullable = false)
    public String sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id", nullable = false)
    public Deal deal;

    @Column(nullable = false)
    public LocalDateTime addedAt;

    @Column(nullable = false)
    public boolean purchased = false;

    public LocalDateTime purchasedAt;

    // === Panache Finders ===

    public static List<ShoppingListItem> findBySession(String sessionId) {
        return find("sessionId = ?1 ORDER BY addedAt DESC", sessionId).list();
    }

    public static List<ShoppingListItem> findBySessionNotPurchased(String sessionId) {
        return find("sessionId = ?1 AND purchased = false ORDER BY addedAt DESC", sessionId).list();
    }

    public static List<ShoppingListItem> findBySessionPurchased(String sessionId) {
        return find("sessionId = ?1 AND purchased = true ORDER BY purchasedAt DESC", sessionId).list();
    }

    public static ShoppingListItem findBySessionAndDeal(String sessionId, Long dealId) {
        return find("sessionId = ?1 AND deal.id = ?2", sessionId, dealId).firstResult();
    }

    public static long deleteBySession(String sessionId) {
        return delete("sessionId", sessionId);
    }

    public static long countBySession(String sessionId) {
        return count("sessionId = ?1 AND purchased = false", sessionId);
    }

    // === Factory Method ===

    public static ShoppingListItem create(String sessionId, Deal deal) {
        ShoppingListItem item = new ShoppingListItem();
        item.sessionId = sessionId;
        item.deal = deal;
        item.addedAt = LocalDateTime.now();
        item.purchased = false;
        return item;
    }

    // === Helper Methods ===

    public void markPurchased() {
        this.purchased = true;
        this.purchasedAt = LocalDateTime.now();
    }

    public void markNotPurchased() {
        this.purchased = false;
        this.purchasedAt = null;
    }
}
