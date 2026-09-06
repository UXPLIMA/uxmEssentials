package com.uxplima.uxmessentials.poses.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.poses.application.port.Snores;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link Snores} loop: one repeating {@code repeatGlobal} pass that, at an interval, plays a soft snore sound at
 * each laying player, no resource pack, just a vanilla sound key. It follows the {@code StaffFollowService} idiom:
 * the pass iterates the snoring set and hops each player onto their own entity thread, where the sound is played, so
 * nothing touches an entity off its owning region under Folia.
 *
 * <p>The snoring set rides a {@link ConcurrentHashMap}-backed set mutated only through {@code add}/{@code remove};
 * {@code StartPose} adds a player when a {@code /lay} begins and the {@code snore} config is on, and {@code StopPose}
 * removes them on every exit. A player who has gone offline is dropped on the next pass. {@link #shutdown()} cancels
 * the pass on module stop.
 */
@NullMarked
public final class BukkitSnores implements Snores {

    private final Server server;
    private final Scheduler scheduler;
    private final Logger log;
    private final String soundKey;
    private final float volume;
    private final float pitch;
    private final Set<UUID> snoring = ConcurrentHashMap.newKeySet();
    private final AutoCloseable task;

    public BukkitSnores(
            Server server,
            Scheduler scheduler,
            Logger log,
            String soundKey,
            float volume,
            float pitch,
            int intervalTicks) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.soundKey = Objects.requireNonNull(soundKey, "soundKey");
        this.volume = volume;
        this.pitch = pitch;
        Duration period = Duration.ofMillis(Math.max(1L, intervalTicks) * 50L);
        this.task = scheduler.repeatGlobal(this::tick, period, period);
    }

    @Override
    public void startSnoring(PlayerRef who) {
        snoring.add(Objects.requireNonNull(who, "who").uuid());
    }

    @Override
    public void stopSnoring(PlayerRef who) {
        snoring.remove(Objects.requireNonNull(who, "who").uuid());
    }

    /** Whether {@code id} is currently in the snore loop. */
    public boolean isSnoring(UUID id) {
        return snoring.contains(Objects.requireNonNull(id, "id"));
    }

    /** One snore pass: hop each snorer onto their own entity thread and play the sound there. */
    public void tick() {
        for (UUID id : snoring) {
            scheduler.onEntity(new PlayerRef(id, id.toString()), () -> snore(id));
        }
    }

    /** Cancel the snore pass and forget every snorer, so a disable or reload leaves no running loop. */
    public void shutdown() {
        try {
            task.close();
        } catch (Exception e) {
            // A cancel failure must not strand the caller (the wiring disables the module after this); log and
            // carry on so the snoring set is still cleared and the disable completes.
            log.error("failed to cancel the poses snore task", e);
        }
        snoring.clear();
    }

    private void snore(UUID id) {
        Player player = server.getPlayer(id);
        if (player == null) {
            snoring.remove(id); // a snorer who left between the pass dispatch and this hop is dropped
            return;
        }
        Location at = Objects.requireNonNull(player.getLocation(), "snorer location");
        player.playSound(at, soundKey, SoundCategory.PLAYERS, volume, pitch);
    }
}
