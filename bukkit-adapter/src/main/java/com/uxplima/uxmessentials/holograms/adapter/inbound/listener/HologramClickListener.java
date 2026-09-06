package com.uxplima.uxmessentials.holograms.adapter.inbound.listener;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramClickKey;
import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramPageCycler;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickActionRunner;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Acts on a clickable hologram when a player clicks its Interaction hitbox. The hitbox carries the hologram's name
 * in its persistent data (stamped by the renderer under {@link HologramClickKey#PDC_KEY}, with {@code
 * setResponsive(true)} so attacks register); this reads it back, looks the hologram up in the
 * in-memory-authoritative repository (no DB read on the click), and acts on the clicking player's region thread.
 *
 * <p>Both clicks are handled, mirroring the npc context's precedence:
 *
 * <ul>
 *   <li>a right-click arrives as {@link PlayerInteractEntityEvent} (the legacy clickable path);
 *   <li>a left-click (attack) arrives as Paper's {@link PrePlayerAttackEntityEvent}, which fires before any damage
 *       logic for the attacked entity, including a non-damageable {@link Interaction} box, and carries the box
 *       as its attacked entity. This is the one clean Bukkit/Paper event surfacing a left-click attack, so no
 *       packet path is fabricated for it.
 * </ul>
 *
 * <p>On a resolved click the legacy single click command runs first on a right-click (kept for backward-compat,
 * and right-click-only exactly as an NPC's single click command is); a multi-page hologram with no command cycles
 * the viewer's page instead. Then the ordered action chain, every
 * {@link com.uxplima.uxmessentials.shared.domain.action.ClickAction} whose trigger matches the click, runs through
 * the shared {@link ClickActionRunner}, so a hologram may carry a legacy click command <em>and</em> an action
 * chain (additive, exactly as an NPC keeps its click command alongside its actions). The chain is given
 * {@code attack = true} for a left-click, {@code false} for a right-click. A short per-player cooldown debounces
 * the rapid duplicate interactions a single click can produce.
 */
@NullMarked
public final class HologramClickListener implements Listener {

    private static final long COOLDOWN_MS = 250L;

    private final NamespacedKey clickKey;
    private final HologramRepository repository;
    private final HologramPageCycler pageCycler;
    private final ClickActionRunner actionRunner;
    private final Scheduler scheduler;
    private final ConcurrentHashMap<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public HologramClickListener(
            Plugin plugin,
            HologramRepository repository,
            HologramPageCycler pageCycler,
            ClickActionRunner actionRunner,
            Scheduler scheduler) {
        Objects.requireNonNull(plugin, "plugin");
        this.clickKey = new NamespacedKey(plugin, HologramClickKey.PDC_KEY);
        this.repository = Objects.requireNonNull(repository, "repository");
        this.pageCycler = Objects.requireNonNull(pageCycler, "pageCycler");
        this.actionRunner = Objects.requireNonNull(actionRunner, "actionRunner");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Interaction box)) {
            return; // main hand only; the off-hand fires its own duplicate event we ignore
        }
        handle(event.getPlayer(), box, false);
    }

    @EventHandler
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (!(event.getAttacked() instanceof Interaction box)) {
            return;
        }
        handle(event.getPlayer(), box, true);
    }

    /** Resolve the clicked hologram, then run the primary behaviour and the action chain on the region thread. */
    private void handle(Player player, Interaction box, boolean attack) {
        String name = box.getPersistentDataContainer().get(clickKey, PersistentDataType.STRING);
        if (name == null) {
            return; // not one of our hitboxes
        }
        if (onCooldown(player.getUniqueId())) {
            return;
        }
        Hologram hologram = repository.find(HologramName.of(name)).orElse(null);
        if (hologram == null) {
            return;
        }
        // Hop onto the clicking player's entity region: the runner requires the viewer's region thread (where
        // performCommand / page packets are safe), and a Folia attack/interact event may not already own it.
        scheduler.onEntity(BukkitRefs.toRef(player), () -> act(player, hologram, attack));
    }

    private void act(Player player, Hologram hologram, boolean attack) {
        switch (primaryBehaviour(hologram.clickCommand(), hologram.isMultiPage(), attack)) {
            case RUN_COMMAND -> player.performCommand(Objects.requireNonNull(hologram.clickCommand(), "clickCommand"));
            case CYCLE_PAGE -> pageCycler.cyclePage(player, hologram.name());
            case NONE -> {
                // No legacy command and not a paged hologram: the action chain below is the only behaviour.
            }
        }
        // The action chain always runs after the legacy behaviour, so a hologram may have both (additive); it
        // filters itself by per-action trigger, so a left-click and a right-click each fire only their actions.
        if (hologram.hasActions()) {
            actionRunner.run(player, hologram.actions(), attack);
        }
    }

    /** The legacy single-click behaviour, decided independently of the action chain (which always runs after). */
    enum Primary {
        RUN_COMMAND,
        CYCLE_PAGE,
        NONE
    }

    /**
     * Decide the legacy primary behaviour for a click. A non-blank click command runs on a right-click only (it is
     * the legacy right-click binding, matching the NPC precedence where the single click command is
     * right-click-only); otherwise a multi-page hologram cycles its page on either click. Pure so it is
     * unit-testable without a live event.
     */
    static Primary primaryBehaviour(@Nullable String clickCommand, boolean multiPage, boolean attack) {
        boolean hasCommand = clickCommand != null && !clickCommand.isBlank();
        if (hasCommand && !attack) {
            return Primary.RUN_COMMAND;
        }
        if (multiPage) {
            return Primary.CYCLE_PAGE;
        }
        return Primary.NONE;
    }

    /** True when this player clicked within the debounce window; records the click time otherwise. */
    private boolean onCooldown(UUID player) {
        long now = System.currentTimeMillis();
        Long previous = lastClick.put(player, now);
        return previous != null && now - previous < COOLDOWN_MS;
    }
}
