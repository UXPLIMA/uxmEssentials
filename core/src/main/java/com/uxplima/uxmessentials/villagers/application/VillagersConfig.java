package com.uxplima.uxmessentials.villagers.application;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.villagers.domain.FollowRange;
import com.uxplima.uxmessentials.villagers.domain.VillagerProtectionPolicy;

/**
 * The typed, immutable view of {@code modules/villagers/config.conf}: the module enable gate plus one sub-record per
 * trade-availability feature. Each feature carries its own {@code enabled} switch on top of the module gate, so an
 * operator turns the whole context off with {@code enabled = false} or leaves it on and picks exactly the features
 * they want. The common villager tools ship on out of the box (infinite trading, the restock timer, the staff trade
 * manager, click-to-trade, protection, and follow); the niche or disruptive ones ship off (instant restock, the
 * blanket disable-trades switch, the villager bucket, and leash).
 *
 * <p>It is resolved once from the module's scoped {@link ConfigStore} when the module starts and, per the
 * atomic-reload rule, swapped whole on reload, so a trade handled mid-reload sees one coherent snapshot. The HOCON
 * keys are kebab-case ({@code infinite-trading}, {@code interval-seconds}); the record components are the camelCase
 * views the adapter reads. Every knob carries the default the bundled config ships, so an operator who deletes a line
 * falls back to the shipped value rather than to zero.
 *
 * @param enabled the module enable gate ({@code enabled}, default {@code true})
 * @param infiniteTrading the infinite-trading feature settings
 * @param restock the restock-timer feature settings
 * @param instantRestock the instant-restock feature settings
 * @param disableTrades the disable-trades feature settings
 * @param tradeManager the trade-manager feature settings
 * @param clickToTrade the click-to-trade feature settings
 * @param protect the protection / saver feature settings
 * @param bucket the villager-in-a-bucket feature settings
 * @param follow the follow feature settings
 * @param leash the leash feature settings
 */
public record VillagersConfig(
        boolean enabled,
        InfiniteTrading infiniteTrading,
        Restock restock,
        InstantRestock instantRestock,
        DisableTrades disableTrades,
        TradeManager tradeManager,
        ClickToTrade clickToTrade,
        Protect protect,
        Bucket bucket,
        Follow follow,
        Leash leash) {

    public VillagersConfig {
        Objects.requireNonNull(infiniteTrading, "infiniteTrading");
        Objects.requireNonNull(restock, "restock");
        Objects.requireNonNull(instantRestock, "instantRestock");
        Objects.requireNonNull(disableTrades, "disableTrades");
        Objects.requireNonNull(tradeManager, "tradeManager");
        Objects.requireNonNull(clickToTrade, "clickToTrade");
        Objects.requireNonNull(protect, "protect");
        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(follow, "follow");
        Objects.requireNonNull(leash, "leash");
    }

    /** Resolve the villagers config from the module's scoped {@link ConfigStore} ({@code modules.villagers}). */
    public static VillagersConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new VillagersConfig(
                config.getBoolean("enabled", true),
                InfiniteTrading.from(config),
                Restock.from(config),
                InstantRestock.from(config),
                DisableTrades.from(config),
                TradeManager.from(config),
                ClickToTrade.from(config),
                Protect.from(config),
                Bucket.from(config),
                Follow.from(config),
                Leash.from(config));
    }

    /**
     * The infinite-trading feature under {@code infinite-trading { … }}: a villager's trades never lock out from use
     * on every trade the plugin resets the villager's recipe uses and suppresses the usual use increment, so no trade
     * ever greys out. Ships on as a common convenience; set it to {@code false} to restore vanilla trade limits.
     *
     * @param enabled whether infinite trading runs ({@code infinite-trading.enabled}, default {@code true})
     */
    public record InfiniteTrading(boolean enabled) {

        static InfiniteTrading from(ConfigStore config) {
            return new InfiniteTrading(config.getBoolean("infinite-trading.enabled", true));
        }
    }

    /**
     * The restock-timer feature under {@code restock { … }}: instead of relying on the vanilla work-station cycle, a
     * scheduled sweep restocks each loaded villager's trades once its last restock is older than {@code interval-seconds}.
     * The last-restock instant is stamped in the villager's PDC and compared against the interval by the domain
     * {@link com.uxplima.uxmessentials.villagers.domain.RestockPolicy}.
     *
     * @param enabled whether the restock sweep runs ({@code restock.enabled}, default {@code true})
     * @param intervalSeconds how long a villager's trades stay fresh between restocks, in seconds, clamped to at least
     *     one ({@code restock.interval-seconds}, default {@code 600})
     */
    public record Restock(boolean enabled, int intervalSeconds) {

        /** The default restock interval in seconds (ten minutes). */
        private static final int DEFAULT_INTERVAL_SECONDS = 600;

        public Restock {
            intervalSeconds = Math.max(1, intervalSeconds);
        }

        static Restock from(ConfigStore config) {
            return new Restock(
                    config.getBoolean("restock.enabled", true),
                    config.getInt("restock.interval-seconds", DEFAULT_INTERVAL_SECONDS));
        }

        /** The restock interval as a {@link Duration}, always at least one second. */
        public Duration interval() {
            return Duration.ofSeconds(intervalSeconds);
        }
    }

    /**
     * The instant-restock feature under {@code instant-restock { … }}: the traded recipe restocks immediately after a
     * trade, with no cooldown. It composes with the restock timer. Instant restock makes the traded recipe available
     * again at once, so it effectively wins for the recipe a player just used.
     *
     * @param enabled whether instant restock runs ({@code instant-restock.enabled}, default {@code false})
     */
    public record InstantRestock(boolean enabled) {

        static InstantRestock from(ConfigStore config) {
            return new InstantRestock(config.getBoolean("instant-restock.enabled", false));
        }
    }

    /**
     * The disable-trades feature under {@code disable-trades { … }}: with {@code enabled} on, every villager refuses to
     * open its trade GUI (a right-click on the villager is cancelled and the player is told). The listener also honours
     * a per-villager PDC flag the Phase-2 trade manager sets, so an individual villager can be disabled even while the
     * global switch is off.
     *
     * @param enabled whether trading is disabled for every villager globally ({@code disable-trades.enabled}, default
     *     {@code false})
     */
    public record DisableTrades(boolean enabled) {

        static DisableTrades from(ConfigStore config) {
            return new DisableTrades(config.getBoolean("disable-trades.enabled", false));
        }
    }

    /**
     * The trade-manager feature under {@code trade-manager { … }}: with {@code enabled} on, staff run
     * {@code /villager manager} (gated on {@code uxmessentials.villagers.manager}) to open a GUI of the villager they
     * are looking at and edit its trades. Change a recipe's buy/sell items and amounts, add a recipe, remove one, or
     * toggle the villager's per-villager disable flag. Edits apply to the live merchant and are PDC-serialised on the
     * villager so they survive a chunk reload / restart (reapplied when the villager loads). When this feature is on
     * the reapply listener registers so a managed villager keeps its custom trades. Ships on as a common staff tool;
     * set it to {@code false} to drop {@code /villager manager}.
     *
     * @param enabled whether the trade manager is available ({@code trade-manager.enabled}, default {@code true})
     */
    public record TradeManager(boolean enabled) {

        static TradeManager from(ConfigStore config) {
            return new TradeManager(config.getBoolean("trade-manager.enabled", true));
        }
    }

    /**
     * The click-to-trade feature under {@code click-to-trade { … }}: with {@code enabled} on, a permitted player
     * (gated on {@code uxmessentials.villagers.trade}) who right-clicks a villager opens its trade window directly,
     * even where the vanilla gate would refuse it (a professionless villager, one already claimed by another trader).
     * The per-villager and global disable flags are still honoured, so a disabled villager never opens. Ships on as a
     * common convenience; set it to {@code false} to leave villager access to the vanilla gate.
     *
     * @param enabled whether click-to-trade runs ({@code click-to-trade.enabled}, default {@code true})
     */
    public record ClickToTrade(boolean enabled) {

        static ClickToTrade from(ConfigStore config) {
            return new ClickToTrade(config.getBoolean("click-to-trade.enabled", true));
        }
    }

    /**
     * The protection / saver feature under {@code protect { … }}: with {@code enabled} on, a villager the operator has
     * chosen to shield never dies to the deaths a settlement loses villagers to. A zombie infection, a lightning
     * strike (and the witch transform it triggers), suffocation, or any other blow, and is kept loaded so it never
     * despawns. {@code all} widens the shield from individually-marked villagers (the {@code /villager protect} toggle)
     * to every villager. The four per-threat gates ship on, so the feature shields against every listed threat unless
     * the operator narrows it; the feature itself ships on out of the box, though only villagers marked with
     * {@code /villager protect} are shielded until {@code all} is set. The pure decision lives in
     * {@link VillagerProtectionPolicy}.
     *
     * @param enabled whether protection runs ({@code protect.enabled}, default {@code true})
     * @param all whether every villager is protected, not only flagged ones ({@code protect.all}, default {@code false})
     * @param fromZombies whether zombie conversion is prevented ({@code protect.from-zombies}, default {@code true})
     * @param fromLightning whether lightning death / transform is prevented ({@code protect.from-lightning}, default
     *     {@code true})
     * @param fromDamage whether other lethal damage is prevented ({@code protect.from-damage}, default {@code true})
     * @param noDespawn whether the villager is kept loaded rather than despawn ({@code protect.no-despawn}, default
     *     {@code true})
     */
    public record Protect(
            boolean enabled,
            boolean all,
            boolean fromZombies,
            boolean fromLightning,
            boolean fromDamage,
            boolean noDespawn) {

        static Protect from(ConfigStore config) {
            return new Protect(
                    config.getBoolean("protect.enabled", true),
                    config.getBoolean("protect.all", false),
                    config.getBoolean("protect.from-zombies", true),
                    config.getBoolean("protect.from-lightning", true),
                    config.getBoolean("protect.from-damage", true),
                    config.getBoolean("protect.no-despawn", true));
        }

        /** The pure protection rule this feature's switches decide. */
        public VillagerProtectionPolicy policy() {
            return new VillagerProtectionPolicy(enabled, all, fromZombies, fromLightning, fromDamage, noDespawn);
        }
    }

    /**
     * The villager-in-a-bucket feature under {@code bucket { … }}: with {@code enabled} on, a player holding
     * {@code uxmessentials.villagers.bucket} sneak-right-clicks a villager to pick it up into a special item that
     * PDC-encodes the villager's profession, type, level, and trades, and places it back later to restore the same
     * villager. A gameplay change, so it ships off.
     *
     * @param enabled whether villager pickup / placement runs ({@code bucket.enabled}, default {@code false})
     */
    public record Bucket(boolean enabled) {

        static Bucket from(ConfigStore config) {
            return new Bucket(config.getBoolean("bucket.enabled", false));
        }
    }

    /**
     * The follow feature under {@code follow { … }}: with {@code enabled} on, a player holding
     * {@code uxmessentials.villagers.follow} runs {@code /villager follow} to make the villager they are looking at
     * (or the nearest within reach) pathfind after them. The villager keeps up while it stays within {@code range}
     * blocks of its owner in the same world, beyond that it stops until the owner returns, and a second
     * {@code /villager follow} un-follows it. The owner is stamped in the villager's PDC so the pairing is durable,
     * and the pure range decision lives in {@link FollowRange}. Ships on as a common tool; set it to {@code false} to
     * drop {@code /villager follow}.
     *
     * @param enabled whether the follow feature runs ({@code follow.enabled}, default {@code true})
     * @param speed the villager's pathfinder speed multiplier while following ({@code follow.speed}, default
     *     {@code 1.0}), clamped to a strictly positive value
     * @param range how far, in blocks, the owner may stray before the villager stops following ({@code follow.range},
     *     default {@code 16}), clamped to at least one
     */
    public record Follow(boolean enabled, double speed, int range) {

        /** The default pathfinder speed multiplier while following (the mob's normal move speed). */
        private static final double DEFAULT_SPEED = 1.0;
        /** The default follow range in blocks. */
        private static final int DEFAULT_RANGE = 16;

        public Follow {
            if (!(speed > 0) || !Double.isFinite(speed)) {
                speed = DEFAULT_SPEED;
            }
            range = Math.max(1, range);
        }

        static Follow from(ConfigStore config) {
            return new Follow(
                    config.getBoolean("follow.enabled", true),
                    config.getDouble("follow.speed", DEFAULT_SPEED),
                    config.getInt("follow.range", DEFAULT_RANGE));
        }

        /** The pure follow-range rule this feature's {@code range} decides. */
        public FollowRange followRange() {
            return new FollowRange(range);
        }
    }

    /**
     * The leash feature under {@code leash { … }}: with {@code enabled} on, a player holding
     * {@code uxmessentials.villagers.leash} right-clicks a villager with a lead in hand to leash it, a lead vanilla
     * never lets you attach to a villager. The adapter intercepts that right-click, attaches the lead, and consumes
     * it, suppressing the trade window that click would otherwise open. A gameplay change, so it ships off.
     *
     * @param enabled whether villager leashing runs ({@code leash.enabled}, default {@code false})
     */
    public record Leash(boolean enabled) {

        static Leash from(ConfigStore config) {
            return new Leash(config.getBoolean("leash.enabled", false));
        }
    }
}
