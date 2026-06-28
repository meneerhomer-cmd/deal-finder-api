package be.dealfinder.extraction;

import be.dealfinder.extraction.ExtractionReader.UnitPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic characterisation tests for {@link ExtractionReader}. No Quarkus
 * boot, no DB — just the JSON-reading and per-unit-price derivation that the
 * DTO factory and substitute matcher depend on.
 */
class ExtractionReaderTest {

    // --- confidence ---------------------------------------------------------

    @Test
    void confidence_nullOrBlank_isZero() {
        assertEquals(0.0, ExtractionReader.confidence(null));
        assertEquals(0.0, ExtractionReader.confidence(""));
        assertEquals(0.0, ExtractionReader.confidence("   "));
    }

    @Test
    void confidence_unparseable_isZero() {
        assertEquals(0.0, ExtractionReader.confidence("{not valid json"));
    }

    @Test
    void confidence_missingField_isZero() {
        assertEquals(0.0, ExtractionReader.confidence("{\"brand\":\"Dreft\"}"));
    }

    @Test
    void confidence_present_isParsed() {
        assertEquals(0.92, ExtractionReader.confidence("{\"confidence\":0.92}"));
    }

    // --- trapDetected -------------------------------------------------------

    @Test
    void trapDetected_present_isReturned() {
        assertEquals("cashback", ExtractionReader.trapDetected("{\"trapDetected\":\"cashback\"}"));
    }

    @Test
    void trapDetected_absentOrNullLiteral_isNull() {
        assertNull(ExtractionReader.trapDetected("{}"));
        assertNull(ExtractionReader.trapDetected("{\"trapDetected\":null}"));
        assertNull(ExtractionReader.trapDetected("{\"trapDetected\":\"null\"}"));
        assertNull(ExtractionReader.trapDetected("{\"trapDetected\":\"\"}"));
    }

    @Test
    void trapDetected_nullJson_isNull() {
        assertNull(ExtractionReader.trapDetected(null));
    }

    // --- unitPrice: guards --------------------------------------------------

    @Test
    void unitPrice_nullJsonOrPrice_isNull() {
        assertNull(ExtractionReader.unitPrice(null, new BigDecimal("5.00")));
        assertNull(ExtractionReader.unitPrice("{\"volumeStructure\":{\"baseUnit\":\"ml\",\"amountPerUnit\":1000}}", null));
    }

    @Test
    void unitPrice_zeroOrNegativePrice_isNull() {
        String json = "{\"volumeStructure\":{\"baseUnit\":\"ml\",\"amountPerUnit\":1000,\"unitCount\":1}}";
        assertNull(ExtractionReader.unitPrice(json, BigDecimal.ZERO));
        assertNull(ExtractionReader.unitPrice(json, new BigDecimal("-1.00")));
    }

    @Test
    void unitPrice_noFlyerNoVolume_isNull() {
        assertNull(ExtractionReader.unitPrice("{\"brand\":\"x\"}", new BigDecimal("3.00")));
    }

    // --- unitPrice: from flyer (printed) ------------------------------------

    @Test
    void unitPrice_fromFlyer_litres() {
        UnitPrice up = ExtractionReader.unitPrice(
                "{\"flyerUnitPrice\":{\"value\":1.25,\"unit\":\"L\"}}", new BigDecimal("99.00"));
        assertNotNull(up);
        assertEquals(new BigDecimal("1.25"), up.value());
        assertEquals("€/L", up.label());
    }

    @Test
    void unitPrice_fromFlyer_allUnits() {
        assertEquals("€/kg", ExtractionReader.unitPrice("{\"flyerUnitPrice\":{\"value\":2,\"unit\":\"kg\"}}", BigDecimal.TEN).label());
        assertEquals("€/wasbeurt", ExtractionReader.unitPrice("{\"flyerUnitPrice\":{\"value\":0.2,\"unit\":\"wash\"}}", BigDecimal.TEN).label());
        assertEquals("€/luier", ExtractionReader.unitPrice("{\"flyerUnitPrice\":{\"value\":0.3,\"unit\":\"diaper\"}}", BigDecimal.TEN).label());
    }

    @Test
    void unitPrice_fromFlyer_unknownUnitOrZeroValue_fallsThroughToNull() {
        assertNull(ExtractionReader.unitPrice("{\"flyerUnitPrice\":{\"value\":1.0,\"unit\":\"each\"}}", BigDecimal.TEN));
        assertNull(ExtractionReader.unitPrice("{\"flyerUnitPrice\":{\"value\":0,\"unit\":\"L\"}}", BigDecimal.TEN));
    }

    @Test
    void unitPrice_flyerPreferredOverVolume() {
        // both present — the printed flyer price wins
        String json = "{\"flyerUnitPrice\":{\"value\":1.10,\"unit\":\"L\"},"
                + "\"volumeStructure\":{\"baseUnit\":\"ml\",\"amountPerUnit\":1000,\"unitCount\":1}}";
        UnitPrice up = ExtractionReader.unitPrice(json, new BigDecimal("3.00"));
        assertEquals(new BigDecimal("1.10"), up.value());
    }

    // --- unitPrice: derived from volume structure ---------------------------

    @Test
    void unitPrice_fromVolume_millilitresToPerLitre() {
        // 1500 ml at €3.00 -> €2.00 / L
        UnitPrice up = ExtractionReader.unitPrice(
                "{\"volumeStructure\":{\"baseUnit\":\"ml\",\"amountPerUnit\":1500,\"unitCount\":1}}",
                new BigDecimal("3.00"));
        assertEquals(new BigDecimal("2.00"), up.value());
        assertEquals("€/L", up.label());
    }

    @Test
    void unitPrice_fromVolume_gramsToPerKilo() {
        // 250 g at €1.00 -> €4.00 / kg
        UnitPrice up = ExtractionReader.unitPrice(
                "{\"volumeStructure\":{\"baseUnit\":\"g\",\"amountPerUnit\":250,\"unitCount\":1}}",
                new BigDecimal("1.00"));
        assertEquals(new BigDecimal("4.00"), up.value());
        assertEquals("€/kg", up.label());
    }

    @Test
    void unitPrice_fromVolume_unitCountMultiplies() {
        // 2 packs x 500 ml = 1000 ml total at €4.00 -> €4.00 / L
        UnitPrice up = ExtractionReader.unitPrice(
                "{\"volumeStructure\":{\"baseUnit\":\"ml\",\"amountPerUnit\":500,\"unitCount\":2}}",
                new BigDecimal("4.00"));
        assertEquals(new BigDecimal("4.00"), up.value());
        assertEquals("€/L", up.label());
    }

    @Test
    void unitPrice_fromVolume_washAndDiaper() {
        // 40 washes at €8.00 -> €0.20 / wasbeurt
        UnitPrice wash = ExtractionReader.unitPrice(
                "{\"volumeStructure\":{\"baseUnit\":\"wash\",\"amountPerUnit\":40,\"unitCount\":1}}",
                new BigDecimal("8.00"));
        assertEquals(new BigDecimal("0.20"), wash.value());
        assertEquals("€/wasbeurt", wash.label());

        // 50 diapers at €12.50 -> €0.25 / luier
        UnitPrice diaper = ExtractionReader.unitPrice(
                "{\"volumeStructure\":{\"baseUnit\":\"diaper\",\"amountPerUnit\":50,\"unitCount\":1}}",
                new BigDecimal("12.50"));
        assertEquals(new BigDecimal("0.25"), diaper.value());
        assertEquals("€/luier", diaper.label());
    }

    @Test
    void unitPrice_fromVolume_missingBaseUnitOrZeroAmount_isNull() {
        assertNull(ExtractionReader.unitPrice(
                "{\"volumeStructure\":{\"amountPerUnit\":1000,\"unitCount\":1}}", new BigDecimal("3.00")));
        assertNull(ExtractionReader.unitPrice(
                "{\"volumeStructure\":{\"baseUnit\":\"ml\",\"amountPerUnit\":0,\"unitCount\":1}}", new BigDecimal("3.00")));
    }

    @Test
    void unitPrice_fromVolume_unknownBaseUnit_isNull() {
        assertNull(ExtractionReader.unitPrice(
                "{\"volumeStructure\":{\"baseUnit\":\"piece\",\"amountPerUnit\":6,\"unitCount\":1}}",
                new BigDecimal("3.00")));
    }

    @Test
    void unitPrice_fromVolume_missingUnitCountDefaultsToOne() {
        // no unitCount -> treated as 1: 1000 ml at €2.00 -> €2.00 / L
        UnitPrice up = ExtractionReader.unitPrice(
                "{\"volumeStructure\":{\"baseUnit\":\"ml\",\"amountPerUnit\":1000}}",
                new BigDecimal("2.00"));
        assertEquals(new BigDecimal("2.00"), up.value());
    }

    @Test
    void unitPrice_rounding_isHalfUpTwoDecimals() {
        // 333 ml at €1.00 -> 3.003... -> €3.00 / L (HALF_UP, 2 dp)
        UnitPrice up = ExtractionReader.unitPrice(
                "{\"volumeStructure\":{\"baseUnit\":\"ml\",\"amountPerUnit\":333,\"unitCount\":1}}",
                new BigDecimal("1.00"));
        assertEquals(2, up.value().scale());
        assertEquals(new BigDecimal("3.00"), up.value());
    }
}
