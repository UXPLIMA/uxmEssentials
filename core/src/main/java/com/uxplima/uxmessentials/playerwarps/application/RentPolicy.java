package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.domain.RentDecision;
import com.uxplima.uxmessentials.playerwarps.domain.RentState;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import org.jspecify.annotations.NullMarked;

/**
 * The pure rent state machine (the {@code BankInterestPolicy} idiom): given a warp's lifecycle status, its rent
 * state, the current instant, and the configured term, it decides what the sweep should do this pass: nothing
 * more. It touches no repository, no clock, and no economy; the {@code SettleRent} use case owns the money moves
 * and the persisted transition, so this class stays trivially and exhaustively testable.
 *
 * <p>The decision, per {@link RentDecision}:
 *
 * <ul>
 *   <li>the sub-group off ⇒ {@link RentDecision#NONE}. The exempt short-circuit, so a disabled rent config
 *       charges nothing.
 *   <li>{@link WarpStatus#ACTIVE} with the paid term lapsed ({@code paidUntil ≤ now}) ⇒ {@link RentDecision#DUE};
 *       otherwise {@link RentDecision#NONE} (still paid through).
 *   <li>{@link WarpStatus#SUSPENDED} past its {@code archiveAfter} grace ⇒ {@link RentDecision#ARCHIVE};
 *       otherwise {@link RentDecision#RETRY}. A suspended warp whose owner has since found the funds is
 *       restored by the retry pass.
 *   <li>{@link WarpStatus#ARCHIVED} ⇒ {@link RentDecision#NONE}: archived warps are inert until an admin restore.
 * </ul>
 */
@NullMarked
public final class RentPolicy {

    /**
     * Decide what to do with a warp holding {@code status} and {@code rent} at {@code now} under {@code cfg}. Never
     * returns {@code null}; a warp the policy has nothing to say about is {@link RentDecision#NONE}.
     */
    public RentDecision decide(WarpStatus status, RentState rent, Instant now, RentConfig cfg) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(rent, "rent");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(cfg, "cfg");
        if (!cfg.enabled()) {
            return RentDecision.NONE;
        }
        return switch (status) {
            case ACTIVE -> lapsed(now, rent.paidUntil()) ? RentDecision.DUE : RentDecision.NONE;
            case SUSPENDED -> archiveDue(now, rent) ? RentDecision.ARCHIVE : RentDecision.RETRY;
            case ARCHIVED -> RentDecision.NONE;
        };
    }

    /** The paid term has lapsed once {@code now} reaches or passes {@code paidUntil}. */
    private static boolean lapsed(Instant now, Instant paidUntil) {
        return !now.isBefore(paidUntil);
    }

    /** A suspended warp is archived once {@code now} reaches or passes its scheduled {@code archiveAfter}. */
    private static boolean archiveDue(Instant now, RentState rent) {
        return rent.archiveAfter().map(after -> !now.isBefore(after)).orElse(false);
    }
}
