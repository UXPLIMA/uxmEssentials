package com.uxplima.uxmessentials.npc.adapter.inbound.listener;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uxplima.uxmessentials.npc.adapter.outbound.NpcRenderer;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickActionRunner;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickCommandRunner;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Runs an NPC's bound click command when a player interacts with it. A packet NPC has no real entity, so the
 * server never knows it was clicked through the normal interact event: instead Paper fires
 * {@link PlayerUseUnknownEntityEvent} carrying the fake entity id. The renderer resolves that id back to the NPC
 * name (the id is allocated once per NPC and shared by every viewer), the repository supplies the stored command,
 * and it runs on the clicking player's region thread (where {@code performCommand} / {@code dispatchCommand} are
 * safe).
 *
 * <p>On a click the single bound click command runs first (kept for backward-compat), then the ordered action
 * chain (every {@link com.uxplima.uxmessentials.shared.domain.action.ClickAction} whose trigger matches the click) runs
 * through the {@link ClickActionRunner}. The command may carry a routing prefix: {@code [console]} runs it as the
 * server console, {@code [player]} (or no prefix) as the clicking player; a {@code {player}} token is replaced
 * with the clicker's name. The click command alone is right-click-only; the action chain carries its own
 * per-action trigger (left/right/any), so both an attack and an interact are now acted on, only the off-hand
 * duplicate is ignored. A held button repeats and the client double-fires, so a short per-player-per-NPC cooldown
 * gates the whole interaction. The cooldown is a self-evicting Caffeine cache keyed {@code uuid:npcName} with a
 * per-entry variable {@code expireAfter}: each stamp expires after that click's effective cooldown: the NPC's own
 * {@code interactionCooldownMillis} override when set, else the module-wide default, so a present (un-expired)
 * stamp means the cooldown is still running, and an expired entry (the cooldown has elapsed) drops out on its own,
 * so the map cannot grow for offline players or deleted NPCs over the JVM lifetime. The interaction runs for
 * everyone (an NPC is a server fixture); the reserved {@code uxmessentials.npc.use} node is left for a future
 * per-NPC permission gate rather than blanket-gating the click here.
 */
@NullMarked
public final class NpcInteractionListener implements Listener {

    private static final String CONSOLE_PREFIX = "[console]";
    private static final String PLAYER_PREFIX = "[player]";
    private static final String PLAYER_TOKEN = "{player}";

    /** Cap on tracked stamps so a churn of players/NPCs cannot grow the cache even between expiries. */
    private static final long MAX_TRACKED_CLICKS = 4_096L;

    private final NpcRenderer renderer;
    private final NpcRepository repository;
    private final ClickCommandRunner runner;
    private final ClickActionRunner actionRunner;
    private final Scheduler scheduler;
    private final long cooldownMillis;
    private final LongSupplier clock;
    private final Cache<String, Long> lastClick;

    public NpcInteractionListener(
            NpcRenderer renderer,
            NpcRepository repository,
            ClickCommandRunner runner,
            ClickActionRunner actionRunner,
            Scheduler scheduler,
            Duration clickCooldown) {
        this(renderer, repository, runner, actionRunner, scheduler, clickCooldown, System::currentTimeMillis);
    }

    NpcInteractionListener(
            NpcRenderer renderer,
            NpcRepository repository,
            ClickCommandRunner runner,
            ClickActionRunner actionRunner,
            Scheduler scheduler,
            Duration clickCooldown,
            LongSupplier clock) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.actionRunner = Objects.requireNonNull(actionRunner, "actionRunner");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.cooldownMillis =
                Objects.requireNonNull(clickCooldown, "clickCooldown").toMillis();
        this.clock = Objects.requireNonNull(clock, "clock");
        // Each entry stores the millisecond deadline at which that click's cooldown elapses (now + the effective
        // cooldown), so the per-NPC override and the global default each block for their own window with no loss of
        // precision. The cache is bounded by size alone (LRU): a passed deadline is swept on access and in
        // trackedClicks(), so an idle player/NPC pair drops out and the map cannot grow over the JVM lifetime, while
        // maximumSize caps it even under churn between sweeps. The injected clock drives the deadline so tests can
        // advance it.
        this.lastClick = Caffeine.newBuilder().maximumSize(MAX_TRACKED_CLICKS).build();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUseUnknownEntity(PlayerUseUnknownEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // main hand only; the off-hand fires its own duplicate event we ignore
        }
        String npcName = renderer.npcNameAt(event.getEntityId());
        if (npcName == null) {
            return; // a genuinely unknown entity (not one of ours)
        }
        Optional<Npc> found = repository.find(NpcName.of(npcName));
        if (found.isEmpty()) {
            return;
        }
        Npc npc = found.get();
        Player player = event.getPlayer();
        if (onCooldown(player, npcName, effectiveCooldownMillis(npc))) {
            return;
        }
        boolean attack = event.isAttack();
        // The single click command is the legacy right-click-only binding; the richer action chain carries its
        // own per-action trigger and runs after it, both on the clicking player's region thread.
        scheduler.onEntity(BukkitRefs.toRef(player), () -> interact(player, npc, attack));
    }

    private void interact(Player player, Npc npc, boolean attack) {
        if (!attack && npc.hasClickCommand()) {
            run(player, Objects.requireNonNull(npc.clickCommand(), "clickCommand"));
        }
        if (npc.hasActions()) {
            actionRunner.run(player, npc.actions(), attack);
        }
    }

    /** The NPC's per-NPC cooldown override when set (positive), otherwise the module-wide default. */
    private long effectiveCooldownMillis(Npc npc) {
        long perNpc = npc.interactionCooldownMillis();
        return perNpc > 0 ? perNpc : cooldownMillis;
    }

    private boolean onCooldown(Player player, String npcName, long effectiveMillis) {
        if (effectiveMillis <= 0) {
            return false;
        }
        String key = player.getUniqueId() + ":" + npcName;
        long now = clock.getAsLong();
        Long deadline = lastClick.getIfPresent(key);
        if (deadline != null && now < deadline) {
            return true; // the previous click's cooldown is still running
        }
        // No live cooldown (none, or the prior one has elapsed): start a fresh window sized to this click's
        // effective cooldown. An elapsed entry is overwritten rather than left dangling.
        lastClick.put(key, now + effectiveMillis);
        return false;
    }

    /** The count of still-running cooldown stamps after sweeping elapsed ones: exposed so a test can assert eviction. */
    long trackedClicks() {
        long now = clock.getAsLong();
        // Drop every entry whose deadline has passed, so an elapsed cooldown leaves no residue, the same self-bound
        // the old time-expiry gave, made precise (Caffeine's variable timer wheel is too coarse for sub-second tests).
        lastClick.asMap().values().removeIf(deadline -> deadline <= now);
        lastClick.cleanUp();
        return lastClick.estimatedSize();
    }

    private void run(Player player, String rawCommand) {
        String substituted = rawCommand.replace(PLAYER_TOKEN, player.getName());
        String lower = substituted.toLowerCase(Locale.ROOT);
        // Already on the clicking player's region thread, where performCommand/dispatchCommand are safe.
        if (lower.startsWith(CONSOLE_PREFIX)) {
            runner.runAsConsole(substituted.substring(CONSOLE_PREFIX.length()).strip());
            return;
        }
        String command = lower.startsWith(PLAYER_PREFIX)
                ? substituted.substring(PLAYER_PREFIX.length()).strip()
                : substituted.strip();
        runner.runAsPlayer(player, command);
    }
}
