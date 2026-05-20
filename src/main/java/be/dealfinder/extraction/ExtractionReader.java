package be.dealfinder.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Read-only helper over a Deal's stored {@code extractionJson} (the tool-input
 * JSON the Claude {@link ProductExtractor} produced). Used by the DTO factory
 * and the substitute matcher — both need the self-reported confidence and a
 * comparable per-unit price (€/L, €/kg, €/wasbeurt, €/luier) without
 * round-tripping through the full {@link ProductExtraction} record.
 */
public final class ExtractionReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExtractionReader() {}

    /** Self-reported extraction confidence (0.0–1.0), 0.0 when absent/unparseable. */
    public static double confidence(String extractionJson) {
        JsonNode ex = parse(extractionJson);
        return ex != null ? ex.path("confidence").asDouble(0.0) : 0.0;
    }

    /**
     * Per-unit price the shopper can compare across pack sizes. Prefers the
     * flyer's printed unit price when present, otherwise derives it from the
     * extracted volume structure and the deal's current price. Returns null
     * when neither is available (silence beats a fabricated comparator).
     */
    public static UnitPrice unitPrice(String extractionJson, BigDecimal currentPrice) {
        JsonNode ex = parse(extractionJson);
        if (ex == null || currentPrice == null || currentPrice.signum() <= 0) return null;

        UnitPrice fromFlyer = fromFlyer(ex.path("flyerUnitPrice"));
        if (fromFlyer != null) return fromFlyer;

        return fromVolume(ex.path("volumeStructure"), currentPrice);
    }

    private static UnitPrice fromFlyer(JsonNode flyer) {
        if (flyer == null || flyer.isMissingNode() || flyer.isNull()) return null;
        double value = flyer.path("value").asDouble(0.0);
        String unit = text(flyer.path("unit"));
        if (value <= 0 || unit == null) return null;
        String label = labelForUnit(unit);
        if (label == null) return null;
        return new UnitPrice(round(value), label);
    }

    private static UnitPrice fromVolume(JsonNode vol, BigDecimal currentPrice) {
        if (vol == null || vol.isMissingNode() || vol.isNull()) return null;
        String baseUnit = text(vol.path("baseUnit"));
        double amountPerUnit = vol.path("amountPerUnit").asDouble(0.0);
        int unitCount = vol.path("unitCount").asInt(1);
        if (baseUnit == null || amountPerUnit <= 0) return null;
        if (unitCount <= 0) unitCount = 1;

        double total = amountPerUnit * unitCount;
        double price = currentPrice.doubleValue();
        return switch (baseUnit) {
            case "ml" -> new UnitPrice(round(price / (total / 1000.0)), "€/L");
            case "g" -> new UnitPrice(round(price / (total / 1000.0)), "€/kg");
            case "wash" -> new UnitPrice(round(price / total), "€/wasbeurt");
            case "diaper" -> new UnitPrice(round(price / total), "€/luier");
            default -> null;
        };
    }

    private static String labelForUnit(String unit) {
        return switch (unit) {
            case "L" -> "€/L";
            case "kg" -> "€/kg";
            case "wash" -> "€/wasbeurt";
            case "diaper" -> "€/luier";
            default -> null;
        };
    }

    private static BigDecimal round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static String text(JsonNode v) {
        if (v == null || v.isNull() || v.isMissingNode()) return null;
        String s = v.asText();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static JsonNode parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    /** A comparable per-unit price plus its Dutch denominator label. */
    public record UnitPrice(BigDecimal value, String label) {}
}
