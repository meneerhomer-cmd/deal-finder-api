package be.dealfinder.service;

import be.dealfinder.entity.MonthlyExtractionCost;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.YearMonth;

/**
 * Cost kill-switch for Claude extraction (launch plan C4). Tracks cumulative
 * monthly spend from token usage and lets the extractor degrade gracefully —
 * stop making new Claude calls once the monthly budget is hit, while existing
 * fingerprints keep working. Prevents a runaway backfill loop from producing a
 * surprise Anthropic bill.
 *
 * Budget + rates are coarse guardrails in Anthropic's USD billing (Haiku 4.5
 * published rates), not accounting — the point is to bound spend, not bill to
 * the cent. Tune via config / env without a redeploy.
 */
@ApplicationScoped
public class ExtractionBudgetService {

    private static final Logger LOG = Logger.getLogger(ExtractionBudgetService.class);

    @ConfigProperty(name = "extraction.budget.monthly", defaultValue = "25.0")
    double monthlyBudget;
    @ConfigProperty(name = "extraction.budget.alert", defaultValue = "15.0")
    double alertThreshold;

    // Haiku 4.5 rates, USD per million tokens.
    @ConfigProperty(name = "extraction.budget.rate-input", defaultValue = "1.0")
    double rateInput;
    @ConfigProperty(name = "extraction.budget.rate-output", defaultValue = "5.0")
    double rateOutput;
    @ConfigProperty(name = "extraction.budget.rate-cache-write", defaultValue = "1.25")
    double rateCacheWrite;
    @ConfigProperty(name = "extraction.budget.rate-cache-read", defaultValue = "0.10")
    double rateCacheRead;

    private static String currentMonth() {
        return YearMonth.now().toString(); // e.g. "2026-05"
    }

    /** True when this month's spend has reached the budget — extractor should skip the call. */
    @Transactional
    public boolean exhausted() {
        MonthlyExtractionCost m = MonthlyExtractionCost.findByMonth(currentMonth());
        double spent = m == null ? 0.0 : m.costMicros / 1_000_000.0;
        return spent >= monthlyBudget;
    }

    public double costOf(int input, int output, int cacheWrite, int cacheRead) {
        return (input * rateInput
                + output * rateOutput
                + cacheWrite * rateCacheWrite
                + cacheRead * rateCacheRead) / 1_000_000.0;
    }

    /** Adds one call's cost to the month's tally; alerts when crossing thresholds. */
    @Transactional
    public void record(int input, int output, int cacheWrite, int cacheRead) {
        double cost = costOf(input, output, cacheWrite, cacheRead);
        String ym = currentMonth();
        MonthlyExtractionCost m = MonthlyExtractionCost.findByMonth(ym);
        if (m == null) {
            m = new MonthlyExtractionCost();
            m.yearMonth = ym;
            m.costMicros = 0;
        }
        double before = m.costMicros / 1_000_000.0;
        m.costMicros += Math.round(cost * 1_000_000);
        m.persist();
        double after = m.costMicros / 1_000_000.0;

        if (before < monthlyBudget && after >= monthlyBudget) {
            LOG.errorf("Extraction monthly budget reached: $%.2f >= $%.2f (%s) — kill-switch active, "
                    + "skipping further extractions until next month.", after, monthlyBudget, ym);
        } else if (before < alertThreshold && after >= alertThreshold) {
            LOG.warnf("Extraction spend crossed alert threshold: $%.2f of $%.2f monthly budget (%s).",
                    after, monthlyBudget, ym);
        }
    }
}
