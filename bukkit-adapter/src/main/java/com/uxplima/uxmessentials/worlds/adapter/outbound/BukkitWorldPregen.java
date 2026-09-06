package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.WorldsMessageKey;
import com.uxplima.uxmessentials.worlds.application.WorldsSettings;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldPregen;
import com.uxplima.uxmessentials.worlds.domain.ChunkPos;
import com.uxplima.uxmessentials.worlds.domain.ChunkRegion;
import com.uxplima.uxmessentials.worlds.domain.PregenProgress;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The rate-limited asynchronous chunk pre-generation engine behind {@link WorldPregen}. Each running
 * pregen owns a repeating task on the global region thread that, every period, tops the in-flight set
 * up to a configured cap by asking the {@link ChunkGenSource} seam for the next chunks, repaints the
 * initiator's boss bar with the live progress and ETA, and finishes when the spiral is exhausted and no
 * chunk is still in flight. At most one pregen runs per world; a second {@link #start} for a world
 * already generating is refused.
 *
 * <p><b>Threading.</b> {@link #tick} runs on the global region thread, so it is the only place that
 * advances the iterator and touches the boss bar; the boss bar is shown/hidden and the completion notice
 * delivered through {@code Scheduler#onEntity}, on the initiator's own region thread, where the live
 * {@code Player} is re-resolved from its uuid. The per-chunk completion callback registered on the seam's
 * future runs off any of those threads, so it touches <em>only</em> the job's two atomics and never the
 * boss bar, Bukkit, or the notifier: all of which are confined to tick/finish/cancel.
 */
@NullMarked
public final class BukkitWorldPregen implements WorldPregen {

    private final ChunkGenSource gen;
    private final Scheduler scheduler;
    private final WorldEngine engine;
    private final Messages messages;
    private final Notifier notifier;
    private final WorldsSettings settings;
    private final Logger log;

    private final ConcurrentHashMap<WorldName, PregenJob> jobs = new ConcurrentHashMap<>();

    public BukkitWorldPregen(
            ChunkGenSource gen,
            Scheduler scheduler,
            WorldEngine engine,
            Messages messages,
            Notifier notifier,
            WorldsSettings settings,
            Logger log) {
        this.gen = Objects.requireNonNull(gen, "gen");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public Result<Unit, WorldError> start(PlayerRef initiator, WorldName world, int radius) {
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(world, "world");
        if (jobs.containsKey(world)) {
            return Result.err(WorldError.PREGEN_ALREADY_RUNNING);
        }
        ChunkRegion region = regionAround(world, radius);
        BossBar bar =
                BossBar.bossBar(barTitle(initiator, world, 0, "-"), 0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        PregenJob job = new PregenJob(initiator, world, region, region.spiral(), bar, Instant.now());
        // Register before scheduling so the first tick, which looks the job up by world, finds it.
        jobs.put(world, job);
        scheduler.onEntity(initiator, () -> showBar(initiator, bar));
        job.handle(scheduler.repeatGlobal(() -> tick(world), Duration.ZERO, settings.pregenTickPeriod()));
        return Result.ok();
    }

    @Override
    public boolean cancel(WorldName world) {
        Objects.requireNonNull(world, "world");
        @Nullable PregenJob job = jobs.remove(world);
        if (job == null) {
            return false;
        }
        closeQuietly(job.handle());
        // No completion notice here: the use case notifies CANCELLED on the operator's behalf.
        scheduler.onEntity(job.initiator(), () -> hideBar(job.initiator(), job.bossBar()));
        return true;
    }

    @Override
    public boolean isRunning(WorldName world) {
        return jobs.containsKey(Objects.requireNonNull(world, "world"));
    }

    @Override
    public void stopAll() {
        Set.copyOf(jobs.keySet()).forEach(this::cancel);
    }

    /** Resolve the centre chunk from the world's spawn (origin chunk when unknown) and build the region. */
    private ChunkRegion regionAround(WorldName world, int radius) {
        Optional<Position> sp = engine.spawnPoint(world);
        int cx = sp.map(p -> (int) Math.floor(p.x()) >> 4).orElse(0);
        int cz = sp.map(p -> (int) Math.floor(p.z()) >> 4).orElse(0);
        return new ChunkRegion(cx, cz, radius);
    }

    /**
     * One generation pass on the global region thread: top the in-flight set up to the cap, repaint the
     * boss bar, and finish when the spiral is drained with nothing outstanding.
     */
    private void tick(WorldName world) {
        @Nullable PregenJob job = jobs.get(world);
        if (job == null) {
            return;
        }
        topUp(job, world);
        repaint(job, world);
        if (!job.iterator().hasNext() && job.inFlight().get() == 0) {
            finish(world);
        }
    }

    private void topUp(PregenJob job, WorldName world) {
        while (job.inFlight().get() < settings.pregenMaxConcurrent()
                && job.iterator().hasNext()) {
            ChunkPos p = job.iterator().next();
            request(job, world, p);
        }
    }

    /**
     * Request one chunk and arm the completion callback that closes its in-flight slot. The increment is
     * paired with the future's completion; a synchronous throw from {@link ChunkGenSource#generate} is
     * caught here so the slot is still released and the chunk counted as done. Otherwise the job would
     * never drain to {@code inFlight == 0} and the loop would wedge. The callback may run off the region
     * thread, so it touches ONLY these two atomics, never Bukkit, the boss bar, or the notifier (those
     * are confined to tick/finish/cancel).
     */
    private void request(PregenJob job, WorldName world, ChunkPos p) {
        job.inFlight().incrementAndGet();
        try {
            var ignored = gen.generate(world, p.x(), p.z()).whenComplete((c, ex) -> {
                job.inFlight().decrementAndGet();
                job.done().incrementAndGet();
            });
        } catch (RuntimeException synchronousFailure) {
            job.inFlight().decrementAndGet();
            job.done().incrementAndGet();
            log.error("pregen chunk request failed for " + world.value(), synchronousFailure);
        }
    }

    private void repaint(PregenJob job, WorldName world) {
        PregenProgress pr = new PregenProgress(
                job.done().get(), job.region().totalChunks(), Duration.between(job.start(), Instant.now()));
        job.bossBar().progress((float) pr.fraction());
        job.bossBar().name(barTitle(job.initiator(), world, percent(pr), etaText(pr)));
    }

    /** Drop the job, stop its loop, hide the bar, and notify the initiator that generation completed. */
    private void finish(WorldName world) {
        @Nullable PregenJob job = jobs.remove(world);
        if (job == null) {
            return;
        }
        closeQuietly(job.handle());
        scheduler.onEntity(job.initiator(), () -> {
            hideBar(job.initiator(), job.bossBar());
            notifier.send(job.initiator(), WorldsMessageKey.WORLD_PREGEN_DONE, Map.of("world", world.value()));
        });
    }

    private Component barTitle(PlayerRef initiator, WorldName world, int percent, String eta) {
        String resolved = messages.resolve(
                initiator,
                WorldsMessageKey.WORLD_PREGEN_BAR,
                Map.of("world", world.value(), "percent", String.valueOf(percent), "eta", eta));
        return StyledText.render(resolved);
    }

    private static int percent(PregenProgress pr) {
        return (int) Math.round(pr.fraction() * 100);
    }

    private static String etaText(PregenProgress pr) {
        return pr.eta().map(d -> d.toSeconds() + "s").orElse("-");
    }

    /** Re-resolve the live player on their own entity thread before showing the bar; no-op if offline. */
    private void showBar(PlayerRef ref, BossBar bar) {
        @Nullable Player player = Bukkit.getPlayer(ref.uuid());
        if (player != null && player.isOnline()) {
            player.showBossBar(bar);
        }
    }

    private void hideBar(PlayerRef ref, BossBar bar) {
        @Nullable Player player = Bukkit.getPlayer(ref.uuid());
        if (player != null && player.isOnline()) {
            player.hideBossBar(bar);
        }
    }

    /** Cancel a job's repeating handle, logging any failure rather than stranding the disable path. */
    private void closeQuietly(@Nullable AutoCloseable handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.close();
        } catch (Exception e) {
            log.error("failed to cancel a world pre-generation loop", e);
        }
    }
}
