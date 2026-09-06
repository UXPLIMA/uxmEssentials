package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code poses_*} placeholders. It is an adapter over the poses context's
 * {@code PoseSessions} registry, wired during poses wiring; when the poses module is disabled the seam is absent
 * and the placeholder degrades to the dash.
 *
 * <p>{@code sitting} is whether the requesting player is currently sitting; {@code posing} is whether they hold a
 * free pose (a {@code /lay}, {@code /bellyflop}, or {@code /spin}, as opposed to a plain sit); {@code pose} is the
 * name of their current pose ({@code sit}/{@code lay}/{@code bellyflop}/{@code spin}/{@code none}); and
 * {@code allowsSitting} is whether they let other players sit on them (the {@code /poses toggle} opt-out). All are
 * live per-player reads, so the resolver's offline guard degrades them to the dash for an offline requester.
 */
public interface PosesPlaceholders {

    /** Whether {@code who} is currently sitting. */
    boolean sitting(PlayerRef who);

    /** Whether {@code who} currently holds a free pose, a {@code /lay}, {@code /bellyflop}, or {@code /spin}. */
    boolean posing(PlayerRef who);

    /** {@code who}'s current pose name, {@code sit}/{@code lay}/{@code bellyflop}/{@code spin}, or {@code none}. */
    String pose(PlayerRef who);

    /** Whether {@code who} currently allows other players to sit on them (the {@code /poses toggle} state). */
    boolean allowsSitting(PlayerRef who);
}
