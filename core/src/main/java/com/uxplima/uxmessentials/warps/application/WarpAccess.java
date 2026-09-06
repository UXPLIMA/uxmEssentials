package com.uxplima.uxmessentials.warps.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpError;

/**
 * The access gate a {@code /warp} passes before the hop is delegated: the per-warp permission node, the
 * warp's optional extra permission, and, only when an economy provider is present, the per-warp cost.
 * Extracted from {@code UseWarp} so the use case stays a thin orchestrator and the gate ordering
 * (permission first, then charge) lives in one place.
 *
 * <p>This is where the economy <em>soft coupling</em> is expressed. The economy provider is an
 * {@link Optional} injected at wiring time: when it is absent, a warp's recorded cost is ignored and the
 * warp is usable for free; when it is present, the cost is charged through the narrow {@link WarpEconomy}
 * seam after the permission gates pass. The warps context therefore never hard-depends on the economy
 * context: economy landing in P3 only flips this {@code Optional} from empty to present.
 */
public final class WarpAccess {

    private final Permissions permissions;
    private final Optional<WarpEconomy> economy;

    public WarpAccess(Permissions permissions, Optional<WarpEconomy> economy) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    /**
     * Check whether {@code who} may use {@code warp} and, when a cost applies and a provider is present,
     * charge it. Returns {@link WarpError#NO_PERMISSION} when a gate is missing,
     * {@link WarpError#CANNOT_AFFORD} when the charge fails, and success once the player has been admitted
     * (and charged, if applicable).
     */
    public Result<Unit, WarpError> admit(PlayerRef who, Warp warp) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        if (!hasPermission(who, warp)) {
            return Result.err(WarpError.NO_PERMISSION);
        }
        return charge(who, warp);
    }

    private boolean hasPermission(PlayerRef who, Warp warp) {
        if (!permissions.has(who, warp.name().useNode())) {
            return false;
        }
        return warp.requiredPermission().map(node -> permissions.has(who, node)).orElse(true);
    }

    private Result<Unit, WarpError> charge(PlayerRef who, Warp warp) {
        if (!warp.hasCost() || economy.isEmpty()) {
            // No charge to make, or no economy provider wired. The cost is recorded but ignored at use
            // time so warps never hard-depends on the economy context.
            return Result.ok();
        }
        WarpEconomy provider = economy.get();
        if (!provider.withdraw(who, warp.cost().amount(), warp.cost().currencyId())) {
            return Result.err(WarpError.CANNOT_AFFORD);
        }
        return Result.ok();
    }
}
