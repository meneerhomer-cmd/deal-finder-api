package be.dealfinder.extraction;

import java.util.Map;

public record ProductExtraction(
        boolean fingerprintable,
        double confidence,
        String ambiguityNotes,
        String category,
        String brand,
        String productLine,
        String variantFamily,
        String style,
        Map<String, Object> categoryAttributes,
        String trapDetected,
        boolean isPrivateLabel,
        VolumeStructure volumeStructure,
        FlyerUnitPrice flyerUnitPrice,
        String bundleType,
        Double bundleEffectiveDiscount,
        String rawJson
) {

    public record VolumeStructure(Integer unitCount, Double amountPerUnit, String baseUnit) {}

    public record FlyerUnitPrice(Double value, String unit) {}

    public String fingerprint() {
        if (!fingerprintable) return null;
        return String.join(":",
                slug(category),
                slug(style),
                slug(brand),
                slug(productLine),
                slug(variantFamily));
    }

    private static String slug(String s) {
        if (s == null || s.isBlank()) return "null";
        return s.toLowerCase().replace(' ', '-').replace("'", "");
    }
}
