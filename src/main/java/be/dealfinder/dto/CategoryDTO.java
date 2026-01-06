package be.dealfinder.dto;

import be.dealfinder.entity.Category;

public record CategoryDTO(
    Long id,
    String slug,
    String name,
    String nameEn,
    String nameNl,
    String nameFr
) {
    public static CategoryDTO from(Category category, String language) {
        return new CategoryDTO(
            category.id,
            category.slug,
            category.getName(language),
            category.nameEn,
            category.nameNl,
            category.nameFr
        );
    }

    public static CategoryDTO from(Category category) {
        return from(category, "en");
    }
}
