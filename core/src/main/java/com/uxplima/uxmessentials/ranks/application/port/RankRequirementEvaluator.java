package com.uxplima.uxmessentials.ranks.application.port;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.ranks.domain.RankRequirement;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The seam the rankup use case resolves a rank's {@link RankRequirement requirements} through, so the pure
 * ordering logic (requirements → charge → advance → actions) never learns how any one condition is checked. The
 * adapter supplies the single implementation that dispatches per {@link com.uxplima.uxmessentials.ranks.domain.RankRequirementType
 * type}: a money check through the economy seam, a playtime lookup through the playtime port, a permission check
 * through the shared {@code Permissions} port, the stored rank through the {@code PlayerRankRepository}, a
 * placeholder comparison through the shared display-condition model, and an inventory count against the live
 * player. The ranks context never imports any of those engines: it only asks this port whether a condition holds.
 *
 * <p>Threading: a placeholder or inventory check is a main-thread (owning-region-thread) operation. Rankup runs
 * on the player's own region thread, the {@code /rankup} command thread, so an adapter resolves these on that
 * thread and never off it.
 */
public interface RankRequirementEvaluator {

    /**
     * True when {@code who} satisfies the single {@code requirement}. An unresolvable condition (a placeholder
     * that cannot be expanded because PlaceholderAPI is absent, a malformed value) fails closed: the player does
     * not rank up on a condition that cannot be checked.
     */
    boolean passes(PlayerRef who, RankRequirement requirement);

    /**
     * True when {@code who} satisfies every requirement in {@code requirements} (vacuously true for an empty
     * list). Short-circuits on the first failing condition so a later, costlier check is not resolved once the
     * rankup is already denied.
     */
    default boolean passesAll(PlayerRef who, List<RankRequirement> requirements) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(requirements, "requirements");
        for (RankRequirement requirement : requirements) {
            if (!passes(who, requirement)) {
                return false;
            }
        }
        return true;
    }
}
