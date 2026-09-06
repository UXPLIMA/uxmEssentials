package com.uxplima.uxmessentials.itemworld.application;

import java.util.Objects;

import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The typed, immutable view of {@code itemworld.conf} (docs/09-deployment.md §itemworld.conf): the per
 * sub-feature-group enable flags, the per-command disable flags, the {@code /give}//{@code /more} caps, the
 * enchant-level clamp, the spawn/purge caps, and the abusable-command audit toggles. It is resolved once from
 * the {@link ConfigStore} when the module starts and swapped atomically on reload, so a command dispatched
 * mid-reload sees one whole snapshot.
 *
 * <p>The enabledness of a single command is a two-level gate on top of the module switch: a command is
 * available only when both its {@link SubFeatureGroup sub-feature group} is enabled
 * ({@code modules.itemworld.groups.<group>.enabled}, default {@code true}) and the command itself is not
 * disabled ({@code modules.itemworld.commands.<literal>.disabled}, default {@code false}). This mirrors the
 * "each group independently disableable via its own flag AND per-command disable, on top of the module-level
 * switch" rule (docs/10-feature-modules.md §15.10).
 */
public final class ItemworldConfig {

    private final ConfigStore config;

    private ItemworldConfig(ConfigStore config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Resolve the itemworld config view from the module's scoped {@link ConfigStore} ({@code modules.itemworld}). */
    public static ItemworldConfig from(ConfigStore config) {
        return new ItemworldConfig(config);
    }

    /** Whether a sub-feature group is enabled ({@code groups.<group>.enabled}, default {@code true}). */
    public boolean groupEnabled(SubFeatureGroup group) {
        Objects.requireNonNull(group, "group");
        return config.getBoolean("groups." + group.configKey() + ".enabled", true);
    }

    /**
     * Whether the {@code /itemedit} held-item editor is enabled ({@code item-edit.enabled}, default {@code true}).
     * This is the item-edit sub-feature's own toggle, separate from the {@link SubFeatureGroup} gate, an operator
     * switches the whole editor off here without touching the item-utils group.
     */
    public boolean itemEditEnabled() {
        return config.getBoolean("item-edit.enabled", true);
    }

    /**
     * Whether {@code /itemedit enchant} may set a level above the enchantment's vanilla maximum
     * ({@code item-edit.allow-over-max-enchants}, default {@code false}). When off the level is clamped to the
     * enchant's vanilla max; when on it is bounded only by {@link #maxEnchantLevel()}.
     */
    public boolean itemEditAllowOverMaxEnchants() {
        return config.getBoolean("item-edit.allow-over-max-enchants", false);
    }

    /**
     * Whether opening a shulker box from the inventory is enabled ({@code shulkers.enabled}, default {@code true}).
     * This is the shulker sub-feature's own toggle, separate from the {@link SubFeatureGroup} groups, an operator
     * turns the in-inventory shulker opener off here without touching any command group.
     */
    public boolean shulkersEnabled() {
        return config.getBoolean("shulkers.enabled", true);
    }

    /**
     * Whether opening a shulker box from the inventory requires the player to be sneaking
     * ({@code shulkers.require-sneak}, default {@code true}). On (the default) a plain right-click still places the
     * box as a block and only a sneak-right-click opens it; off, any right-click with the box in hand opens it.
     */
    public boolean shulkersRequireSneak() {
        return config.getBoolean("shulkers.require-sneak", true);
    }

    /** Whether a single command is disabled ({@code commands.<literal>.disabled}, default {@code false}). */
    public boolean commandDisabled(String literal) {
        Objects.requireNonNull(literal, "literal");
        return config.getBoolean("commands." + literal + ".disabled", false);
    }

    /**
     * Whether {@code literal} is available: its {@code group} must be enabled and the command itself not
     * disabled. The single predicate the adapter consults before dispatching any itemworld command.
     */
    public boolean commandEnabled(SubFeatureGroup group, String literal) {
        return groupEnabled(group) && !commandDisabled(literal);
    }

    /** The {@code /give}//{@code /item} amount cap ({@code give-cap}, default {@code 2304}). */
    public int giveCap() {
        return config.getInt("give-cap", com.uxplima.uxmessentials.itemworld.domain.AmountSpec.DEFAULT_CAP);
    }

    /** The {@code /more} stack-fill cap ({@code more-cap}, defaults to {@link #giveCap()}). */
    public int moreCap() {
        return config.getInt("more-cap", giveCap());
    }

    /** The {@code /enchant} level ceiling ({@code max-enchant-level}, default {@code 32767}). */
    public int maxEnchantLevel() {
        return config.getInt(
                "max-enchant-level", com.uxplima.uxmessentials.itemworld.domain.EnchantSpec.DEFAULT_MAX_LEVEL);
    }

    /** The {@code /spawnmob} count cap ({@code spawnmob-cap}, default {@code 64}). */
    public int spawnMobCap() {
        return config.getInt("spawnmob-cap", com.uxplima.uxmessentials.itemworld.domain.MobSpec.DEFAULT_CAP);
    }

    /** The {@code /butcher}//{@code /remove} radius ceiling ({@code purge-max-radius}, default {@code 256}). */
    public int purgeMaxRadius() {
        return config.getInt(
                "purge-max-radius", com.uxplima.uxmessentials.itemworld.domain.PurgeSelection.DEFAULT_MAX_RADIUS);
    }

    /**
     * The {@code /give} amount above which the action is audit-logged ({@code give-audit-threshold}, default
     * {@code 64}). A normal single-stack give is not audited; a bulk give is.
     */
    public int giveAuditThreshold() {
        return config.getInt("give-audit-threshold", 64);
    }

    /**
     * Whether a named abusable-verb audit line is emitted ({@code audit.<event>}, default {@code true}). An
     * operator can quiet a high-volume verb (e.g. {@code audit.spawnmob = false}) while keeping the channel
     * for the rest.
     */
    public boolean auditEnabled(String event) {
        Objects.requireNonNull(event, "event");
        return config.getBoolean("audit." + event, true);
    }
}
