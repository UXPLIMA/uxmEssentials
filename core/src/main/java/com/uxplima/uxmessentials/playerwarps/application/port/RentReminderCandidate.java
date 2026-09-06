package com.uxplima.uxmessentials.playerwarps.application.port;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The minimal projection the reminder pass reads for one warp whose paid term is approaching: its id and name, its
 * owner (the mail recipient), when its rent falls due, and the highest reminder stage it has already been mailed.
 * This is a persistence read model, not the full {@link com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp}
 * aggregate. The {@code rent_reminded_stage} dedup counter lives only on the row, never on the aggregate, so the
 * reminder pass reads exactly these columns rather than materialising every warp fact just to send a heads-up.
 *
 * @param id the warp's surrogate key
 * @param owner the warp owner the reminder mail is addressed to
 * @param warp the warp's name, shown in the reminder text
 * @param paidUntil the instant the current paid term lapses
 * @param remindedStage the highest reminder window already mailed for this term (0 = none yet)
 */
public record RentReminderCandidate(
        PlayerWarpId id, PlayerRef owner, PlayerWarpName warp, Instant paidUntil, int remindedStage) {

    public RentReminderCandidate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(paidUntil, "paidUntil");
        if (remindedStage < 0) {
            throw new IllegalArgumentException("reminded stage must not be negative: " + remindedStage);
        }
    }
}
