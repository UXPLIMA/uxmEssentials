package com.uxplima.uxmessentials.survival.adapter.outbound;

import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import com.uxplima.uxmlib.item.PdcFlag;
import org.jspecify.annotations.NullMarked;

/**
 * The per-player on/off state of the survival mechanics that carry a {@code /command} toggle. Each toggle survives
 * relog, so it is stamped in the player's PDC under a single pre-created key. The sanctioned use for transient
 * per-holder state, like the poses and teleport toggles. The stored byte holds the <em>active</em> state directly: an
 * absent key defaults to on (the mechanic ships enabled, so a player who never touched it has it on), so a first
 * toggle writes an off {@code (byte) 0}.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>per-holder PDC</b>. Reads and writes go through the owning live {@link Player} on its region thread
 * (the block-break event thread or the command thread). Both {@link NamespacedKey}s are created once as constants,
 * never on a hot path.
 */
@NullMarked
public final class PdcSurvivalToggles {

    private static final NamespacedKey TREE_FELLER = Objects.requireNonNull(
            NamespacedKey.fromString("uxmessentials:survival_treefeller"), "survival_treefeller key");
    private static final NamespacedKey VEINMINER = Objects.requireNonNull(
            NamespacedKey.fromString("uxmessentials:survival_veinminer"), "survival_veinminer key");
    private static final NamespacedKey FARM_PROTECT = Objects.requireNonNull(
            NamespacedKey.fromString("uxmessentials:survival_farmprotect"), "survival_farmprotect key");
    private static final NamespacedKey AUTO_PICKUP = Objects.requireNonNull(
            NamespacedKey.fromString("uxmessentials:survival_autopickup"), "survival_autopickup key");
    private static final NamespacedKey AUTO_SMELT = Objects.requireNonNull(
            NamespacedKey.fromString("uxmessentials:survival_autosmelt"), "survival_autosmelt key");
    private static final NamespacedKey AUTO_SELL = Objects.requireNonNull(
            NamespacedKey.fromString("uxmessentials:survival_autosell"), "survival_autosell key");
    private static final NamespacedKey AUTO_TOOL = Objects.requireNonNull(
            NamespacedKey.fromString("uxmessentials:survival_autotool"), "survival_autotool key");

    /** Whether tree-feller is active for {@code player}, defaulting to {@code defaultOn} when never toggled. */
    public boolean treeFellerActive(Player player, boolean defaultOn) {
        Objects.requireNonNull(player, "player");
        return PdcFlag.getOrDefault(player.getPersistentDataContainer(), TREE_FELLER, defaultOn);
    }

    /** Whether veinminer is active for {@code player}, defaulting to {@code defaultOn} when never toggled. */
    public boolean veinminerActive(Player player, boolean defaultOn) {
        Objects.requireNonNull(player, "player");
        return PdcFlag.getOrDefault(player.getPersistentDataContainer(), VEINMINER, defaultOn);
    }

    /** Flip tree-feller for {@code player} relative to its current effective state and return the new state. */
    public boolean toggleTreeFeller(Player player, boolean defaultOn) {
        Objects.requireNonNull(player, "player");
        boolean nowActive = !treeFellerActive(player, defaultOn);
        PdcFlag.set(player.getPersistentDataContainer(), TREE_FELLER, nowActive);
        return nowActive;
    }

    /** Flip veinminer for {@code player} relative to its current effective state and return the new state. */
    public boolean toggleVeinminer(Player player, boolean defaultOn) {
        Objects.requireNonNull(player, "player");
        boolean nowActive = !veinminerActive(player, defaultOn);
        PdcFlag.set(player.getPersistentDataContainer(), VEINMINER, nowActive);
        return nowActive;
    }

    /** Whether farmprotect is active for {@code player}, defaulting to {@code defaultOn} when never toggled. */
    public boolean farmProtectActive(Player player, boolean defaultOn) {
        Objects.requireNonNull(player, "player");
        return PdcFlag.getOrDefault(player.getPersistentDataContainer(), FARM_PROTECT, defaultOn);
    }

    /** Flip farmprotect for {@code player} relative to its current effective state and return the new state. */
    public boolean toggleFarmProtect(Player player, boolean defaultOn) {
        Objects.requireNonNull(player, "player");
        boolean nowActive = !farmProtectActive(player, defaultOn);
        PdcFlag.set(player.getPersistentDataContainer(), FARM_PROTECT, nowActive);
        return nowActive;
    }

    /** Whether auto-pickup is active for {@code player}, defaulting to {@code defaultOn} when never toggled. */
    public boolean autoPickupActive(Player player, boolean defaultOn) {
        Objects.requireNonNull(player, "player");
        return PdcFlag.getOrDefault(player.getPersistentDataContainer(), AUTO_PICKUP, defaultOn);
    }

    /** Flip auto-pickup for {@code player} relative to its current effective state and return the new state. */
    public boolean toggleAutoPickup(Player player, boolean defaultOn) {
        Objects.requireNonNull(player, "player");
        boolean nowActive = !autoPickupActive(player, defaultOn);
        PdcFlag.set(player.getPersistentDataContainer(), AUTO_PICKUP, nowActive);
        return nowActive;
    }

    /** Whether auto-smelt is active for {@code player}, defaulting to {@code defaultOn} when never toggled. */
    public boolean autoSmeltActive(Player player, boolean defaultOn) {
        Objects.requireNonNull(player, "player");
        return PdcFlag.getOrDefault(player.getPersistentDataContainer(), AUTO_SMELT, defaultOn);
    }

    /** Flip auto-smelt for {@code player} relative to its current effective state and return the new state. */
    public boolean toggleAutoSmelt(Player player, boolean defaultOn) {
        Objects.requireNonNull(player, "player");
        boolean nowActive = !autoSmeltActive(player, defaultOn);
        PdcFlag.set(player.getPersistentDataContainer(), AUTO_SMELT, nowActive);
        return nowActive;
    }

    /** Whether auto-sell is active for {@code player}, defaulting to {@code defaultOn} when never toggled. */
    public boolean autoSellActive(Player player, boolean defaultOn) {
        Objects.requireNonNull(player, "player");
        return PdcFlag.getOrDefault(player.getPersistentDataContainer(), AUTO_SELL, defaultOn);
    }

    /** Flip auto-sell for {@code player} relative to its current effective state and return the new state. */
    public boolean toggleAutoSell(Player player, boolean defaultOn) {
        Objects.requireNonNull(player, "player");
        boolean nowActive = !autoSellActive(player, defaultOn);
        PdcFlag.set(player.getPersistentDataContainer(), AUTO_SELL, nowActive);
        return nowActive;
    }

    /** Whether auto-tool is active for {@code player}, defaulting to {@code defaultOn} when never toggled. */
    public boolean autoToolActive(Player player, boolean defaultOn) {
        Objects.requireNonNull(player, "player");
        return PdcFlag.getOrDefault(player.getPersistentDataContainer(), AUTO_TOOL, defaultOn);
    }

    /** Flip auto-tool for {@code player} relative to its current effective state and return the new state. */
    public boolean toggleAutoTool(Player player, boolean defaultOn) {
        Objects.requireNonNull(player, "player");
        boolean nowActive = !autoToolActive(player, defaultOn);
        PdcFlag.set(player.getPersistentDataContainer(), AUTO_TOOL, nowActive);
        return nowActive;
    }
}
