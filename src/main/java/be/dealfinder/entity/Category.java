package be.dealfinder.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.util.List;
import java.util.regex.Pattern;

@Entity
@Table(name = "categories")
public class Category extends PanacheEntity {

    @Column(nullable = false, unique = true)
    public String slug;

    @Column(nullable = false)
    public String nameEn;

    @Column(nullable = false)
    public String nameNl;

    @Column(nullable = false)
    public String nameFr;

    @Column(length = 2000)
    public String keywords;

    @Column(nullable = false)
    public boolean active = true;

    // === Panache Finders ===

    public static Category findBySlug(String slug) {
        return find("slug", slug).firstResult();
    }

    public static List<Category> findAllActive() {
        return find("active", true).list();
    }

    // === Helper Methods ===

    public String getName(String language) {
        return switch (language.toLowerCase()) {
            case "nl" -> nameNl;
            case "fr" -> nameFr;
            default -> nameEn;
        };
    }

    public boolean matchesProduct(String productName) {
        if (keywords == null || keywords.isBlank()) {
            return false;
        }
        String lowerProduct = productName.toLowerCase();
        for (String keyword : keywords.toLowerCase().split(",")) {
            String kw = keyword.trim();
            if (kw.isEmpty()) continue;
            // Whole-word match, not substring — avoids false positives like
            // "hoeSLAken" → sla (groenten) or "choCOLAde" → cola. Multi-word
            // keywords (e.g. "rode wijn") still match as a phrase. Note this
            // can't catch semantic mislabels where the keyword IS a real word
            // in a non-food name (e.g. "Cool Water" cologne → water).
            if (Pattern.compile("\\b" + Pattern.quote(kw) + "\\b").matcher(lowerProduct).find()) {
                return true;
            }
        }
        return false;
    }

    // === Factory Method ===

    public static Category create(String slug, String nameEn, String nameNl, String nameFr, String keywords) {
        Category category = new Category();
        category.slug = slug;
        category.nameEn = nameEn;
        category.nameNl = nameNl;
        category.nameFr = nameFr;
        category.keywords = keywords;
        category.active = true;
        return category;
    }
}
