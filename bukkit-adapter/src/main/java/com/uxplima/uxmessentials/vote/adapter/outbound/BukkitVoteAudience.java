package com.uxplima.uxmessentials.vote.adapter.outbound;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Bukkit {@link VoteAudience}: snapshots the currently online players as {@link PlayerRef}s so a vote
 * party can reward everyone connected and the thank-you can broadcast to them. This is the single place the
 * vote context reads {@code Bukkit.getOnlinePlayers()}, the application asks the port.
 *
 * <p>Both callers reach this off any region tick thread: {@code ForceParty} runs inside a
 * {@code Scheduler.async} hop from {@code /voteparty}, and {@code HandleVote} runs inside a
 * {@code Scheduler.async} hop from the Votifier event (itself an async network-thread event). Reading the
 * Bukkit roster from those threads is a torn read on Folia, where {@code getOnlinePlayers()} is only
 * consistently readable on the global region thread. So the enumeration marshals onto the global thread and
 * snapshots each player to an immutable {@link PlayerRef} before returning; the use case then iterates the
 * returned copy and re-targets each per-recipient delivery to that recipient's own thread. When the caller
 * already owns the global thread the read runs inline, since scheduling and then blocking on the global
 * thread would deadlock. A marshal that times out yields an empty roster (logged, throttled) rather than
 * hanging the worker.
 */
@NullMarked
public final class BukkitVoteAudience implements VoteAudience {

    private static final Logger LOG = LoggerFactory.getLogger(BukkitVoteAudience.class);
    private static final Duration MARSHAL_TIMEOUT = Duration.ofSeconds(2);

    private final Scheduler scheduler;

    public BukkitVoteAudience(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public Collection<PlayerRef> online() {
        if (scheduler.onGlobalThread()) {
            return snapshot();
        }
        CompletableFuture<List<PlayerRef>> future = new CompletableFuture<>();
        scheduler.onGlobal(() -> {
            try {
                future.complete(snapshot());
            } catch (RuntimeException failure) {
                future.completeExceptionally(failure);
            }
        });
        return await(future);
    }

    /** Snapshot the online roster to refs, only legal on the global region thread. */
    private static List<PlayerRef> snapshot() {
        List<PlayerRef> refs = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            refs.add(BukkitRefs.toRef(player));
        }
        return List.copyOf(refs);
    }

    private static List<PlayerRef> await(CompletableFuture<List<PlayerRef>> future) {
        try {
            return future.get(MARSHAL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (TimeoutException timeout) {
            LOG.warn("vote audience roster marshalling timed out after {}ms", MARSHAL_TIMEOUT.toMillis());
            return List.of();
        } catch (ExecutionException failure) {
            LOG.error("vote audience roster snapshot failed", failure);
            return List.of();
        }
    }
}
