package be.dealfinder.service;

import be.dealfinder.dto.OpportunityDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic tests for the opportunity-ranking and private-label helpers in
 * {@link DealService}. No DB — these are the deterministic decision rules
 * behind the home banner and cross-retailer matching.
 */
class DealServiceLogicTest {

    private static OpportunityDTO opp(String saving, Integer discount) {
        return new OpportunityDTO("fp", "name", null, "cat", "Lidl",
                new BigDecimal("1.00"), new BigDecimal("2.00"),
                new BigDecimal(saving), discount);
    }

    // --- expectedPrice = max(30d average, 30d p90) ---------------------------

    @Test
    void expectedPrice_singlePrice_returnsThatPrice() {
        assertEquals(new BigDecimal("5.00"), DealService.expectedPrice(List.of(new BigDecimal("5.00"))));
    }

    @Test
    void expectedPrice_averageWinsWhenSpreadIsTight() {
        // all equal -> avg == p90 == the value
        assertEquals(new BigDecimal("3.00"),
                DealService.expectedPrice(List.of(new BigDecimal("3.00"), new BigDecimal("3.00"), new BigDecimal("3.00"))));
    }

    @Test
    void expectedPrice_p90WinsWhenAnOutlierIsHigh() {
        // 1..10 -> avg 5.50, p90 index = ceil(0.9*10)-1 = 8 -> 9.00, max = 9.00
        List<BigDecimal> prices = List.of(
                new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3"), new BigDecimal("4"),
                new BigDecimal("5"), new BigDecimal("6"), new BigDecimal("7"), new BigDecimal("8"),
                new BigDecimal("9"), new BigDecimal("10"));
        assertEquals(new BigDecimal("9.00"), DealService.expectedPrice(prices).stripTrailingZeros().setScale(2));
    }

    @Test
    void expectedPrice_unsortedInputIsHandled() {
        // order must not matter
        BigDecimal sorted = DealService.expectedPrice(List.of(new BigDecimal("1"), new BigDecimal("9"), new BigDecimal("5")));
        BigDecimal shuffled = DealService.expectedPrice(List.of(new BigDecimal("9"), new BigDecimal("5"), new BigDecimal("1")));
        assertEquals(sorted, shuffled);
    }

    // --- beats: rank by saving, tie-break by discount ------------------------

    @Test
    void beats_higherSavingWins() {
        assertTrue(DealService.beats(opp("2.50", 10), opp("1.00", 99)));
        assertFalse(DealService.beats(opp("1.00", 99), opp("2.50", 10)));
    }

    @Test
    void beats_equalSaving_higherDiscountWins() {
        assertTrue(DealService.beats(opp("2.00", 40), opp("2.00", 30)));
        assertFalse(DealService.beats(opp("2.00", 30), opp("2.00", 40)));
    }

    @Test
    void beats_equalSaving_nullDiscountTreatedAsZero() {
        assertTrue(DealService.beats(opp("2.00", 1), opp("2.00", null)));
        assertFalse(DealService.beats(opp("2.00", null), opp("2.00", 1)));
    }

    @Test
    void beats_identical_isNotStrictlyBetter() {
        assertFalse(DealService.beats(opp("2.00", 30), opp("2.00", 30)));
    }

    // --- isPrivateLabel ------------------------------------------------------

    @Test
    void isPrivateLabel_nullBrand_isPrivate() {
        assertTrue(DealService.isPrivateLabel(null, "Carrefour"));
    }

    @Test
    void isPrivateLabel_brandEqualsRetailer_isPrivate() {
        assertTrue(DealService.isPrivateLabel("Carrefour", "Carrefour"));
        assertTrue(DealService.isPrivateLabel("carrefour", "Carrefour")); // case-insensitive
    }

    @Test
    void isPrivateLabel_merkSuffixes_arePrivate() {
        assertTrue(DealService.isPrivateLabel("Kruidvat Merk", "Kruidvat"));
        assertTrue(DealService.isPrivateLabel("Albert Heijn Huismerk", "Albert Heijn"));
    }

    @Test
    void isPrivateLabel_realBrand_isNotPrivate() {
        assertFalse(DealService.isPrivateLabel("Dreft", "Carrefour"));
        assertFalse(DealService.isPrivateLabel("Coca-Cola", "Delhaize"));
    }

    @Test
    void externalIdFor_matchesTheFormatTheScraperPersists() {
        assertEquals("gql-kruidvat-abc-123", DealService.externalIdFor("kruidvat", "abc-123"));
        assertEquals("gql-carrefour-market-9", DealService.externalIdFor("carrefour-market", "9"));
    }
}
