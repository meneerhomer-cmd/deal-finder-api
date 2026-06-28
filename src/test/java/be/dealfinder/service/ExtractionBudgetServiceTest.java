package be.dealfinder.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-arithmetic test for {@link ExtractionBudgetService#costOf}. The
 * persistence-backed {@code exhausted()/record()} paths need a DB and are left
 * to integration coverage; the cost model itself is the part worth pinning.
 */
class ExtractionBudgetServiceTest {

    private ExtractionBudgetService svc;

    @BeforeEach
    void setUp() {
        svc = new ExtractionBudgetService();
        // Haiku 4.5 published rates (USD per million tokens) — same as the config defaults.
        svc.rateInput = 1.0;
        svc.rateOutput = 5.0;
        svc.rateCacheWrite = 1.25;
        svc.rateCacheRead = 0.10;
    }

    @Test
    void costOf_zeroTokens_isZero() {
        assertEquals(0.0, svc.costOf(0, 0, 0, 0), 1e-9);
    }

    @Test
    void costOf_inputOnly_usesInputRate() {
        // 1M input tokens at $1.0/M -> $1.00
        assertEquals(1.0, svc.costOf(1_000_000, 0, 0, 0), 1e-9);
    }

    @Test
    void costOf_outputDominatesAtFiveTimesInput() {
        // 1M output tokens at $5.0/M -> $5.00
        assertEquals(5.0, svc.costOf(0, 1_000_000, 0, 0), 1e-9);
    }

    @Test
    void costOf_cacheReadIsCheapest() {
        // 1M cache-read tokens at $0.10/M -> $0.10
        assertEquals(0.10, svc.costOf(0, 0, 0, 1_000_000), 1e-9);
    }

    @Test
    void costOf_cacheWriteRate() {
        // 1M cache-write tokens at $1.25/M -> $1.25
        assertEquals(1.25, svc.costOf(0, 0, 1_000_000, 0), 1e-9);
    }

    @Test
    void costOf_combinesAllFourBuckets() {
        // matches the launch-plan's first cached call: cacheCreated=11375, then live input/output
        double expected = (200 * 1.0 + 80 * 5.0 + 11375 * 1.25 + 0 * 0.10) / 1_000_000.0;
        assertEquals(expected, svc.costOf(200, 80, 11375, 0), 1e-9);
    }

    @Test
    void costOf_cachedReadCallIsFarCheaperThanCacheWrite() {
        // a warm-cache call (read 11375) costs much less than the cold one (write 11375)
        double warm = svc.costOf(200, 80, 0, 11375);
        double cold = svc.costOf(200, 80, 11375, 0);
        org.junit.jupiter.api.Assertions.assertTrue(warm < cold);
    }
}
