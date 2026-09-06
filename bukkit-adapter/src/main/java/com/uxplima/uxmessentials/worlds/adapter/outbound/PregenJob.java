package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.time.Instant;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import net.kyori.adventure.bossbar.BossBar;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.domain.ChunkPos;
import com.uxplima.uxmessentials.worlds.domain.ChunkRegion;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The mutable state of one running pre-generation, held here so {@link BukkitWorldPregen} stays an
 * orchestrator rather than a bag of per-job fields. One instance lives in the engine's job map for the
 * lifetime of a single {@code /worlds pregen} run.
 *
 * <p><b>Field ownership / threading.</b> {@link #done} and {@link #inFlight} are the only fields the
 * chunk-generation completion callback touches, and they are atomics precisely because that callback may
 * run off the region thread. Every other field (the iterator, the boss bar, the cancel handle) is read
 * and mutated only from the engine's tick/finish/cancel methods, which run on the global region thread
 * (the iterator) or the initiator's entity thread (the boss bar), never concurrently. The {@link #handle}
 * is assigned once, immediately after the job is registered, before the first tick can fire.
 */
@NullMarked
final class PregenJob {

    private final PlayerRef initiator;
    private final WorldName world;
    private final ChunkRegion region;
    private final Iterator<ChunkPos> iterator;
    private final AtomicLong done;
    private final AtomicInteger inFlight;
    private final BossBar bossBar;
    private final Instant start;

    private @Nullable AutoCloseable handle;

    PregenJob(
            PlayerRef initiator,
            WorldName world,
            ChunkRegion region,
            Iterator<ChunkPos> iterator,
            BossBar bossBar,
            Instant start) {
        this.initiator = Objects.requireNonNull(initiator, "initiator");
        this.world = Objects.requireNonNull(world, "world");
        this.region = Objects.requireNonNull(region, "region");
        this.iterator = Objects.requireNonNull(iterator, "iterator");
        this.bossBar = Objects.requireNonNull(bossBar, "bossBar");
        this.start = Objects.requireNonNull(start, "start");
        this.done = new AtomicLong();
        this.inFlight = new AtomicInteger();
    }

    PlayerRef initiator() {
        return initiator;
    }

    WorldName world() {
        return world;
    }

    ChunkRegion region() {
        return region;
    }

    Iterator<ChunkPos> iterator() {
        return iterator;
    }

    AtomicLong done() {
        return done;
    }

    AtomicInteger inFlight() {
        return inFlight;
    }

    BossBar bossBar() {
        return bossBar;
    }

    Instant start() {
        return start;
    }

    @Nullable AutoCloseable handle() {
        return handle;
    }

    void handle(AutoCloseable value) {
        this.handle = Objects.requireNonNull(value, "value");
    }
}
