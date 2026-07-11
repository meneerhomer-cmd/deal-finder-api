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

    /**
     * Bump when the fingerprint join rule changes. Deals carry the version they were
     * fingerprinted under, so /admin/refingerprint can recompute stale ones from the
     * stored extraction JSON — no Anthropic call, no re-spend.
     */
    public static final int SCHEME_VERSION = 1;

    /**
     * A fingerprint is comparison-grade only when the identity is pinned down to a product
     * line. Without one, the fingerprint means "some product of this brand" — fine to group,
     * never sound to price-compare (two Pampers packs are not the same purchasable item).
     */
    public static final double COMPARISON_CONFIDENCE_FLOOR = 0.80;

    public record VolumeStructure(Integer unitCount, Double amountPerUnit, String baseUnit) {}

    public record FlyerUnitPrice(Double value, String unit) {}

    public String fingerprint() {
        return fingerprintOf(fingerprintable, category, style, brand, productLine, variantFamily);
    }

    /** The single definition of the join. ExtractionReader recomputes through this too. */
    public static String fingerprintOf(boolean fingerprintable, String category, String style,
                                       String brand, String productLine, String variantFamily) {
        if (!fingerprintable) return null;
        return String.join(":",
                slug(category),
                slug(style),
                slug(brand),
                slug(productLine),
                slug(variantFamily));
    }

    /**
     * Structurally identified: the fingerprint names an actual product line, not just a brand.
     * A missing field is joined in as the literal "null", so without this check two deals the
     * extractor knew LESS about collapse onto the same fingerprint.
     */
    public static boolean isStructurallySpecified(String category, String brand, String productLine) {
        return notBlank(category) && notBlank(brand) && notBlank(productLine);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank() && !"null".equalsIgnoreCase(s);
    }

    private static String slug(String s) {
        if (s == null || s.isBlank()) return "null";
        return s.toLowerCase().replace(' ', '-').replace("'", "");
    }
}
