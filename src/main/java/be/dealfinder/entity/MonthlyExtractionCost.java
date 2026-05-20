package be.dealfinder.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

/**
 * Running tally of Claude extraction spend for a single calendar month
 * (key = "2026-05"). Persisted so the cost kill-switch survives restarts —
 * an in-memory counter would reset mid-backfill and let spend run past the cap.
 * Cost is stored in micro-units of the billing currency (USD × 1e6) to keep it
 * an exact integer.
 */
@Entity
@Table(name = "monthly_extraction_cost", indexes = {
    @Index(name = "idx_mec_month", columnList = "yearMonth", unique = true)
})
public class MonthlyExtractionCost extends PanacheEntity {

    @Column(nullable = false, unique = true, length = 7)
    public String yearMonth;

    @Column(nullable = false)
    public long costMicros;

    public static MonthlyExtractionCost findByMonth(String yearMonth) {
        return find("yearMonth", yearMonth).firstResult();
    }
}
