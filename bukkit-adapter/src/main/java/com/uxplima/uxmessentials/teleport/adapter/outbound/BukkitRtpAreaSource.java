package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.RtpAreaSource;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;
import org.jspecify.annotations.NullMarked;

/**
 * The single owner of a world's live random-teleport zone. It reads the world border and the current per-world
 * radii to build the {@link SafeSearchArea} both the pre-warmed queue refills against and the {@code /rtp biome}
 * search draws from, so the two paths share one source of truth and one {@code /settpr} swap. The radii sit behind
 * an {@link AtomicReference} so {@code /settpr} can reset the zone at runtime with a single whole-record swap, an
 * in-flight read sees either the whole previous tuning or the whole new one, never a torn mix.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>atomic-reference</b>. {@code settings} is seeded non-null at construction and only ever replaced
 * with a non-null value, read lock-free on every serve/refill/search and replaced whole by {@code /settpr}.
 */
@NullMarked
public final class BukkitRtpAreaSource implements RtpAreaSource {

    private final Server server;
    private final AtomicReference<RtpWorldSettings> settings;

    public BukkitRtpAreaSource(Server server, RtpWorldSettings settings) {
        this.server = Objects.requireNonNull(server, "server");
        this.settings = new AtomicReference<>(Objects.requireNonNull(settings, "settings"));
    }

    /** The live per-world tuning (radii, queue sizing, and the search budget) read lock-free. */
    public RtpWorldSettings current() {
        // Seeded non-null and only ever replaced with a non-null value; requireNonNull makes that explicit.
        return Objects.requireNonNull(settings.get(), "settings");
    }

    /**
     * Swap the live search radii (the {@code /settpr} runtime path), keeping the queue sizing and search budget
     * untouched, and return the new tuning. A single {@link AtomicReference#set} so an in-flight read is coherent.
     */
    public RtpWorldSettings updateRadii(double minRadius, double maxRadius) {
        RtpWorldSettings updated = current().withRadii(minRadius, maxRadius);
        settings.set(updated);
        return updated;
    }

    @Override
    public Optional<SafeSearchArea> areaFor(WorldRef worldRef) {
        Objects.requireNonNull(worldRef, "worldRef");
        World world = server.getWorld(worldRef.uid());
        if (world == null) {
            return Optional.empty();
        }
        WorldBorder border = world.getWorldBorder();
        double borderRadius = border.getSize() / 2.0;
        double centerX = border.getCenter().getX();
        double centerZ = border.getCenter().getZ();
        RtpWorldSettings current = current();
        double max = Math.min(current.maxRadius(), borderRadius);
        if (max < current.minRadius()) {
            return Optional.empty();
        }
        return Optional.of(
                new SafeSearchArea(worldRef, centerX, centerZ, current.minRadius(), current.maxRadius(), borderRadius));
    }
}
