package be.dealfinder.scraper;

import be.dealfinder.entity.Deal;
import be.dealfinder.extraction.ProductExtraction.FlyerUnitPrice;
import be.dealfinder.extraction.ProductExtraction.VolumeStructure;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic tests for the scraper's stateless transforms — the cashback
 * override (which regressed twice because a re-scrape kept resetting cashback
 * deals to "GRATIS / -100%") and the quantity / unit-price formatters. No
 * GraphQL, no DB.
 */
class GraphQLScraperLogicTest {

    private static Deal cashbackRawDeal(String extractionJson, BigDecimal original) {
        Deal d = new Deal();
        d.originalPrice = original;
        d.currentPrice = BigDecimal.ZERO; // raw flyer value for a cashback deal
        d.discountPercentage = 100;
        d.dealType = "korting";
        d.extractionJson = extractionJson;
        return d;
    }

    // --- reapplyCashbackOverride --------------------------------------------

    @Test
    void cashbackTrap_restoresFullPriceAndRelabels() {
        Deal d = cashbackRawDeal("{\"trapDetected\":\"cashback\"}", new BigDecimal("15.49"));
        GraphQLScraper.reapplyCashbackOverride(d);
        assertEquals(new BigDecimal("15.49"), d.currentPrice); // pays full price upfront
        assertEquals(0, d.discountPercentage);                 // not a -100% "GRATIS"
        assertEquals("100% terugbetaald", d.dealType);
    }

    @Test
    void noTrap_leavesDealUntouched() {
        Deal d = cashbackRawDeal("{\"trapDetected\":null}", new BigDecimal("15.49"));
        d.currentPrice = new BigDecimal("10.00");
        d.discountPercentage = 30;
        GraphQLScraper.reapplyCashbackOverride(d);
        assertEquals(new BigDecimal("10.00"), d.currentPrice);
        assertEquals(30, d.discountPercentage);
        assertEquals("korting", d.dealType);
    }

    @Test
    void differentTrap_isNotTreatedAsCashback() {
        Deal d = cashbackRawDeal("{\"trapDetected\":\"loyalty-card-only\"}", new BigDecimal("9.99"));
        GraphQLScraper.reapplyCashbackOverride(d);
        assertEquals(BigDecimal.ZERO, d.currentPrice); // unchanged
        assertEquals(100, d.discountPercentage);
    }

    @Test
    void nullOriginalPrice_returnsEarly() {
        Deal d = cashbackRawDeal("{\"trapDetected\":\"cashback\"}", null);
        GraphQLScraper.reapplyCashbackOverride(d);
        assertEquals(BigDecimal.ZERO, d.currentPrice); // can't restore without an original
        assertEquals(100, d.discountPercentage);
    }

    // --- formatQuantity -----------------------------------------------------

    @Test
    void formatQuantity_singleUnit_mlAndGrams() {
        assertEquals("500ml", GraphQLScraper.formatQuantity(new VolumeStructure(1, 500.0, "ml")));
        assertEquals("250g", GraphQLScraper.formatQuantity(new VolumeStructure(null, 250.0, "g")));
    }

    @Test
    void formatQuantity_multiPack_prefixesCount() {
        assertEquals("6 x 33ml", GraphQLScraper.formatQuantity(new VolumeStructure(6, 33.0, "ml")));
    }

    @Test
    void formatQuantity_washAndDiaperUnits() {
        // one pack of N (unitCount=1, amountPerUnit=N)
        assertEquals("40 wash", GraphQLScraper.formatQuantity(new VolumeStructure(1, 40.0, "wash")));
        assertEquals("50 stuks", GraphQLScraper.formatQuantity(new VolumeStructure(1, 50.0, "diaper")));
        // multi-pack keeps the "N x amount" shape
        assertEquals("2 x 20 wash", GraphQLScraper.formatQuantity(new VolumeStructure(2, 20.0, "wash")));
    }

    @Test
    void formatQuantity_nullOrIncomplete_isNull() {
        assertNull(GraphQLScraper.formatQuantity(null));
        assertNull(GraphQLScraper.formatQuantity(new VolumeStructure(1, null, "ml")));
        assertNull(GraphQLScraper.formatQuantity(new VolumeStructure(1, 500.0, null)));
    }

    // --- formatFlyerUnitPrice -----------------------------------------------

    @Test
    void formatFlyerUnitPrice_formatsValueAndUnit() {
        assertEquals("1.25/L", GraphQLScraper.formatFlyerUnitPrice(new FlyerUnitPrice(1.25, "L")));
        assertEquals("0.20/wash", GraphQLScraper.formatFlyerUnitPrice(new FlyerUnitPrice(0.2, "wash")));
    }

    @Test
    void formatFlyerUnitPrice_nullOrIncomplete_isNull() {
        assertNull(GraphQLScraper.formatFlyerUnitPrice(null));
        assertNull(GraphQLScraper.formatFlyerUnitPrice(new FlyerUnitPrice(null, "L")));
        assertNull(GraphQLScraper.formatFlyerUnitPrice(new FlyerUnitPrice(1.0, null)));
    }
}
