package com.uxplima.uxmessentials.homes.application.port;

import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * A pre-create check the {@code CreateHomeAtSlot} use case runs before it asks the aggregate to create a
 * home: a seam later slices register against without re-opening the use case. The use case runs every
 * registered guard in order and stops at the first {@link Result#isErr() failure}, returning its
 * {@link HomeError} (and the matching feedback) without ever touching the aggregate.
 *
 * <p>The default registry is empty. A plain {@code /sethome} is gated only by the slot/limit invariants the
 * aggregate already owns. Later slices register guards here: S2 a region/world-blacklist and a near-other-
 * home spacing check, S3 a land-claim membership check, and S5 a charge-on-create economy guard. Each is
 * additive and independently disableable, so wiring zero guards keeps the original behaviour exactly.
 */
public interface SethomeGuard {

    /**
     * Decide whether {@code owner} may create a home in {@code slot} at {@code at}. Return {@code
     * Result.ok()} to allow, or {@code Result.err(...)} with the {@link HomeError} the caller should report.
     */
    Result<Unit, HomeError> check(PlayerRef owner, HomeSlot slot, Position at);
}
