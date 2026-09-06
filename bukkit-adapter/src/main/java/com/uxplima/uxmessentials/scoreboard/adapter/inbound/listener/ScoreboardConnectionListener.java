package com.uxplima.uxmessentials.scoreboard.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardOwnership;
import com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardRenderer;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Renders a player's scoreboard display the moment they join so they do not wait a full refresh interval for the
 * first paint, forgets their sidebar bookkeeping on quit so a dropped player leaves no stale board behind, and
 * re-renders them immediately when they change world or game mode so world-blacklist suppression takes effect at
 * once rather than lagging up to a full refresh tick.
 *
 * <p>Join and quit fire on the joining/quitting player's region thread, so reading the live player and touching their
 * board is region-local: no scheduler hop is needed there. The world- and game-mode-change events also fire on the
 * player's region thread, but the re-render is still routed through the {@code Scheduler} port onto the owning entity
 * thread (re-fetching the live player by UUID) so it stays valid on Folia, mirroring the playerstate listeners.
 */
@NullMarked
public final class ScoreboardConnectionListener implements Listener {

    private final ScoreboardRenderer renderer;
    private final Scheduler scheduler;
    private final java.util.Optional<ScoreboardOwnership> ownership;

    public ScoreboardConnectionListener(ScoreboardRenderer renderer, Scheduler scheduler) {
        this(renderer, scheduler, null);
    }

    public ScoreboardConnectionListener(
            ScoreboardRenderer renderer,
            Scheduler scheduler,
            @org.jspecify.annotations.Nullable ScoreboardOwnership ownership) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.ownership = java.util.Optional.ofNullable(ownership);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ownership.ifPresent(value -> value.inject(player));
        renderer.renderFor(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ownership.ifPresent(value -> value.eject(player));
        renderer.forget(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        rerender(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        rerender(event.getPlayer());
    }

    private void rerender(Player player) {
        PlayerRef who = BukkitRefs.toRef(player);
        scheduler.onEntity(who, () -> {
            Player live = Bukkit.getPlayer(who.uuid());
            if (live != null) {
                renderer.renderFor(live);
            }
        });
    }
}
