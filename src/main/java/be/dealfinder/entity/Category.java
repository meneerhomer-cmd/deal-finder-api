package be.dealfinder.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.util.List;

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
        String[] keywordList = keywords.toLowerCase().split(",");
        for (String keyword : keywordList) {
            if (lowerProduct.contains(keyword.trim())) {
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
