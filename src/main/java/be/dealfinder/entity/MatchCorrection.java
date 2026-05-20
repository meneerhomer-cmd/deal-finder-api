package be.dealfinder.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A user-reported "wrong match" on a cross-retailer / substitute suggestion.
 * Each row is a labelled failure case: the algorithm claimed source and target
 * are the same product (or a valid substitute) and the shopper disagreed.
 *
 * Admins review the queue and feed confirmed mistakes back into the extraction
 * prompt as few-shot negatives (launch plan C2). No FK to Deal — deals churn
 * daily and we want the report to survive the deal being purged; we snapshot
 * the fingerprints so the pair stays interpretable after the fact.
 */
@Entity
@Table(name = "match_corrections", indexes = {
    @Index(name = "idx_match_correction_source", columnList = "sourceDealId"),
    @Index(name = "idx_match_correction_reviewed", columnList = "reviewed")
})
public class MatchCorrection extends PanacheEntity {

    @Column(nullable = false)
    public Long sourceDealId;

    @Column(nullable = false)
    public Long targetDealId;

    @Column(length = 500)
    public String sourceFingerprint;

    @Column(length = 500)
    public String targetFingerprint;

    @Column(nullable = false)
    public boolean reviewed;

    @Column(nullable = false)
    public LocalDateTime reportedAt;

    public static MatchCorrection report(Long sourceDealId, Long targetDealId,
                                         String sourceFingerprint, String targetFingerprint) {
        MatchCorrection mc = new MatchCorrection();
        mc.sourceDealId = sourceDealId;
        mc.targetDealId = targetDealId;
        mc.sourceFingerprint = sourceFingerprint;
        mc.targetFingerprint = targetFingerprint;
        mc.reviewed = false;
        mc.reportedAt = LocalDateTime.now();
        return mc;
    }
}
