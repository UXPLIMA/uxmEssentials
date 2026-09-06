package com.uxplima.uxmessentials.kits.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.kits.application.port.KitStockStore;
import com.uxplima.uxmessentials.kits.application.port.KitUnlockStore;
import com.uxplima.uxmessentials.kits.application.port.RequirementEvaluator;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitError;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownKind;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownStartPhase;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The claim gate a {@code /kit} passes before the items are granted, applying the claim rules in one place
 * so {@link ClaimKit} stays a thin orchestrator. The order is deliberate: the per-kit permission first
 * (cheapest, and the most informative refusal), then the one-time stamp (a consumed one-time kit is a
 * permanent no), then the cooldown (a repeatable kit's rate limit), then the placeholder requirements, then the
 * global stock reservation, and only last the charge, so a kit whose requirements the player fails is never
 * charged, an over-cooldown kit never burns their money, and a sold-out kit is refused before any charge.
 *
 * <p>This is where the economy and requirement <em>soft couplings</em> live. Each is an {@link Optional}
 * injected at wiring time. When the economy provider is absent, a kit's recorded cost is ignored and the kit is
 * claimable for free; when present, the cost is charged through the narrow {@link KitEconomy} seam after every
 * other gate passes. When the {@link RequirementEvaluator} is absent (PlaceholderAPI not installed), a kit with
 * no requirements is claimable but a kit that <em>declares</em> requirements fails closed: its conditions
 * cannot be checked, so it cannot be claimed. The kits context therefore never hard-depends on the economy
 * context or on any placeholder engine.
 *
 * <p>The cooldown resolves through the shared {@link Cooldowns} port against the {@code kit} tier node
 * ({@code uxmessentials.kit.cooldown.<seconds>}, lowest wins) while keying its stamp per kit id, so each kit
 * rate-limits independently. The {@code uxmessentials.kit.cooldown.bypass} node both skips the cooldown and,
 * by convention, lets a holder re-claim a one-time kit: that bypass is checked here for the one-time gate.
 */
public final class KitAccess {

    private static final String COOLDOWN_BYPASS_NODE = "uxmessentials.kit.cooldown.bypass";

    private final Permissions permissions;
    private final Cooldowns cooldowns;
    private final KitClaimStore claims;
    private final Optional<KitEconomy> economy;
    private final Optional<RequirementEvaluator> requirements;
    private final Optional<KitStockStore> stock;
    private final Optional<KitUnlockStore> unlocks;

    public KitAccess(Permissions permissions, Cooldowns cooldowns, KitClaimStore claims, Optional<KitEconomy> economy) {
        this(permissions, cooldowns, claims, economy, Optional.empty());
    }

    public KitAccess(
            Permissions permissions,
            Cooldowns cooldowns,
            KitClaimStore claims,
            Optional<KitEconomy> economy,
            Optional<RequirementEvaluator> requirements) {
        this(permissions, cooldowns, claims, economy, requirements, Optional.empty());
    }

    public KitAccess(
            Permissions permissions,
            Cooldowns cooldowns,
            KitClaimStore claims,
            Optional<KitEconomy> economy,
            Optional<RequirementEvaluator> requirements,
            Optional<KitStockStore> stock) {
        this(permissions, cooldowns, claims, economy, requirements, stock, Optional.empty());
    }

    public KitAccess(
            Permissions permissions,
            Cooldowns cooldowns,
            KitClaimStore claims,
            Optional<KitEconomy> economy,
            Optional<RequirementEvaluator> requirements,
            Optional<KitStockStore> stock,
            Optional<KitUnlockStore> unlocks) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.requirements = Objects.requireNonNull(requirements, "requirements");
        this.stock = Objects.requireNonNull(stock, "stock");
        this.unlocks = Objects.requireNonNull(unlocks, "unlocks");
    }

    /**
     * Check whether {@code who} may claim {@code kit} and, when a cost applies and a provider is present,
     * charge it. Returns the first failing gate, or success once the player has been admitted (and charged,
     * if applicable). Side effects beyond the charge, stamping the cooldown and the one-time mark, are the
     * caller's job, run only after the grant succeeds.
     */
    public Result<Unit, KitError> admit(PlayerRef who, KitDefinition kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        Result<Unit, KitError> gated = admissible(who, kit);
        if (gated.isErr()) {
            return gated;
        }
        return reserveStockAndCharge(who, kit);
    }

    /**
     * Run only the side-effect-free claim gates, permission, the one-time stamp, the cooldown, and the
     * requirements: returning the first failing one or success. Unlike {@link #admit}, this reserves no stock
     * and charges no cost, so the claim use case can interpose its own check (an {@code on-full: deny} inventory
     * pre-check) between the gates and the irreversible reserve-and-charge step.
     */
    public Result<Unit, KitError> admissible(PlayerRef who, KitDefinition kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        if (kit.requiresPermission() && !permissions.has(who, kit.permissionNode())) {
            return Result.err(KitError.NO_PERMISSION);
        }
        if (kit.isOneTime() && claims.hasClaimed(who, kit.id()) && !permissions.has(who, COOLDOWN_BYPASS_NODE)) {
            return Result.err(KitError.ALREADY_CLAIMED);
        }
        if (cooldowns.check(who, cooldownKind(who, kit)).isErr()) {
            return Result.err(KitError.ON_COOLDOWN);
        }
        if (!meetsRequirements(who, kit)) {
            return Result.err(KitError.REQUIREMENTS_NOT_MET);
        }
        return Result.ok();
    }

    /** Reserve stock and charge for a kit already past {@link #admissible}; the irreversible part of {@link #admit}. */
    public Result<Unit, KitError> reserveAndCharge(PlayerRef who, KitDefinition kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        return reserveStockAndCharge(who, kit);
    }

    /**
     * Reserve one unit of a stock-limited kit, then charge any cost. Stock is taken first so a player who cannot
     * afford the kit never holds a reservation: a failed charge releases the unit back. An unstocked or unlimited
     * kit reserves nothing and charges normally.
     */
    private Result<Unit, KitError> reserveStockAndCharge(PlayerRef who, KitDefinition kit) {
        if (!reserveStock(kit)) {
            return Result.err(KitError.OUT_OF_STOCK);
        }
        Result<Unit, KitError> charged = charge(who, kit);
        if (charged.isErr()) {
            releaseStock(kit);
        }
        return charged;
    }

    private boolean reserveStock(KitDefinition kit) {
        if (!kit.hasStockLimit()) {
            return true;
        }
        return stock.map(store -> store.tryConsume(kit.id(), kit.stockLimit())).orElse(true);
    }

    private void releaseStock(KitDefinition kit) {
        if (kit.hasStockLimit()) {
            stock.ifPresent(store -> store.release(kit.id()));
        }
    }

    /**
     * Whether {@code who} satisfies {@code kit}'s claim requirements. A kit with no requirements always passes.
     * A kit that declares requirements passes only when an evaluator is present and every condition holds
     * with no evaluator wired (PlaceholderAPI absent) it <em>fails closed</em>, because the conditions cannot
     * be checked. Used by the gate and by the menu renderer to pick the requirements-not-met display state.
     */
    public boolean meetsRequirements(PlayerRef who, KitDefinition kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        if (!kit.hasRequirements()) {
            return true;
        }
        return requirements
                .map(evaluator -> evaluator.passesAll(who, kit.requirements()))
                .orElse(false);
    }

    /** Whether {@code who} individually satisfies {@code requirement}; fails closed when no evaluator is wired. */
    public boolean meetsRequirement(PlayerRef who, com.uxplima.uxmessentials.kits.domain.KitRequirement requirement) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(requirement, "requirement");
        return requirements.map(evaluator -> evaluator.passes(who, requirement)).orElse(false);
    }

    /** Start the cooldown clock and record the one-time stamp and the buy-to-unlock mark after a successful grant. */
    public void recordClaim(PlayerRef who, KitDefinition kit) {
        cooldowns.stamp(who, cooldownKind(who, kit));
        if (kit.isOneTime()) {
            claims.markClaimed(who, kit.id());
        }
        if (kit.unlockOnce()) {
            unlocks.ifPresent(store -> store.markUnlocked(who, kit.id()));
        }
    }

    /** The remaining cooldown for {@code who} on {@code kit}; ok when ready. */
    public Result<Unit, java.time.Duration> remaining(PlayerRef who, KitDefinition kit) {
        return cooldowns.check(who, cooldownKind(who, kit));
    }

    private Result<Unit, KitError> charge(PlayerRef who, KitDefinition kit) {
        com.uxplima.uxmessentials.kits.domain.KitCost cost = effectiveCost(who, kit);
        if (cost.isFree() || economy.isEmpty() || alreadyUnlocked(who, kit)) {
            return Result.ok();
        }
        KitEconomy provider = economy.get();
        if (!provider.withdraw(who, cost.amount(), cost.currencyId())) {
            return Result.err(KitError.CANNOT_AFFORD);
        }
        return Result.ok();
    }

    /** Whether {@code kit} is a buy-to-unlock kit {@code who} has already purchased, so its price is now waived. */
    private boolean alreadyUnlocked(PlayerRef who, KitDefinition kit) {
        return kit.unlockOnce()
                && unlocks.map(store -> store.hasUnlocked(who, kit.id())).orElse(false);
    }

    private CooldownKind cooldownKind(PlayerRef who, KitDefinition kit) {
        long seconds = resolveCooldownSeconds(who, kit);
        return CooldownKind.scoped("kit", "kit." + kit.id().value(), seconds, CooldownStartPhase.TELEPORT);
    }

    public boolean hasPermission(PlayerRef who, KitDefinition kit) {
        return !kit.requiresPermission() || permissions.has(who, kit.permissionNode());
    }

    /**
     * Resolve the variant of {@code kit} that applies to {@code who}: the first variant, in definition order
     * (best-first), whose permission the viewer holds, or the base kit when they hold none. Used at claim,
     * list, and preview time so the items granted and the icon shown reflect the viewer's rank. The returned
     * definition carries the resolved variant's items and, when the variant overrides them, its cooldown
     * and cost, with every other setting inherited from the base kit.
     */
    public KitDefinition resolveVariant(PlayerRef who, KitDefinition kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        for (com.uxplima.uxmessentials.kits.domain.KitVariant variant : kit.variants()) {
            if (permissions.has(who, variant.permission())) {
                return applyVariant(kit, variant);
            }
        }
        return kit;
    }

    private KitDefinition applyVariant(KitDefinition kit, com.uxplima.uxmessentials.kits.domain.KitVariant variant) {
        KitDefinition resolved = kit.withItems(variant.items());
        if (variant.cooldown().isPresent()) {
            resolved = resolved.withCooldown(variant.cooldown().get());
        }
        if (variant.cost().isPresent()) {
            resolved = resolved.withCost(variant.cost().get());
        }
        return resolved;
    }

    private com.uxplima.uxmessentials.kits.domain.KitCost effectiveCost(PlayerRef who, KitDefinition kit) {
        return resolveVariant(who, kit).cost();
    }

    public boolean hasClaimedOneTime(PlayerRef who, KitDefinition kit) {
        return kit.isOneTime() && claims.hasClaimed(who, kit.id()) && !permissions.has(who, COOLDOWN_BYPASS_NODE);
    }

    public boolean isOnCooldown(PlayerRef who, KitDefinition kit) {
        return cooldowns.check(who, cooldownKind(who, kit)).isErr();
    }

    /** Whether {@code kit}'s global stock limit has been reached; false for an unlimited kit or with no store wired. */
    public boolean isOutOfStock(KitDefinition kit) {
        Objects.requireNonNull(kit, "kit");
        if (!kit.hasStockLimit()) {
            return false;
        }
        return stock.map(store -> store.claimed(kit.id()) >= kit.stockLimit()).orElse(false);
    }

    public boolean canAfford(PlayerRef who, KitDefinition kit) {
        com.uxplima.uxmessentials.kits.domain.KitCost cost = effectiveCost(who, kit);
        if (cost.isFree() || economy.isEmpty() || alreadyUnlocked(who, kit)) {
            return true;
        }
        return economy.get().canAfford(who, cost.amount(), cost.currencyId());
    }

    private long resolveCooldownSeconds(PlayerRef who, KitDefinition kit) {
        long shortest = resolveVariant(who, kit).cooldownSeconds();
        if (kit.permissionCooldowns().isEmpty()) {
            return shortest;
        }
        for (java.util.Map.Entry<String, java.time.Duration> entry :
                kit.permissionCooldowns().entrySet()) {
            String group = entry.getKey();
            if (permissions.has(who, "uxmessentials.kit.cooldown." + group)) {
                long durationSecs = entry.getValue().toSeconds();
                if (durationSecs < shortest) {
                    shortest = durationSecs;
                }
            }
        }
        return shortest;
    }
}
