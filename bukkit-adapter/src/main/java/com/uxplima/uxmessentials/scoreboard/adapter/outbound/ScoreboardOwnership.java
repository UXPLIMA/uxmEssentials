package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardPacketEvents;
import com.uxplima.uxmlib.pipeline.ChannelResolver;
import com.uxplima.uxmlib.pipeline.PacketAction;
import com.uxplima.uxmlib.pipeline.PacketListenerRegistry;
import com.uxplima.uxmlib.pipeline.PacketPipeline;
import org.jspecify.annotations.NullMarked;

/** Connects the renderer's ownership state machine to outbound scoreboard packets without cancelling foreign traffic. */
@NullMarked
public final class ScoreboardOwnership {

    private static final Duration REORDER_DELAY = Duration.ofSeconds(1);

    private final ScoreboardRenderer renderer;
    private final Scheduler scheduler;
    private final PacketPipeline pipeline;

    public ScoreboardOwnership(ScoreboardRenderer renderer, Scheduler scheduler, Logger log, ChannelResolver channels) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(log, "log");
        PacketListenerRegistry registry = new PacketListenerRegistry();
        registry.register((viewer, packet) -> {
            if (viewer != null) {
                for (var event : ScoreboardPacketEvents.decodeAll(packet)) {
                    if (renderer.observe(viewer, event)) {
                        scheduleRender(viewer);
                    }
                }
            }
            return PacketAction.PASS;
        });
        this.pipeline = new PacketPipeline(
                Objects.requireNonNull(channels, "channels"),
                registry,
                "uxmessentials:scoreboard-ownership",
                fault -> scheduler.async(() -> log.error("scoreboard packet observer failed", fault)));
    }

    /** Inject immediately and run one delayed watchdog pass after other plugins have finished join choreography. */
    public void inject(Player player) {
        Objects.requireNonNull(player, "player");
        pipeline.inject(player);
        PlayerRef ref = BukkitRefs.toRef(player);
        scheduler.asyncAfter(
                REORDER_DELAY,
                () -> scheduler.onEntity(ref, () -> {
                    Player live = Bukkit.getPlayer(ref.uuid());
                    if (live != null && live.isOnline()) {
                        pipeline.reorder(live);
                    }
                }));
    }

    public void eject(Player player) {
        pipeline.eject(Objects.requireNonNull(player, "player"));
    }

    private void scheduleRender(UUID viewer) {
        scheduler.onGlobal(() -> {
            Player player = Bukkit.getPlayer(viewer);
            if (player == null || !player.isOnline()) {
                return;
            }
            PlayerRef ref = BukkitRefs.toRef(player);
            scheduler.onEntity(ref, () -> {
                Player live = Bukkit.getPlayer(viewer);
                if (live != null && live.isOnline()) {
                    renderer.renderFor(live);
                }
            });
        });
    }
}
