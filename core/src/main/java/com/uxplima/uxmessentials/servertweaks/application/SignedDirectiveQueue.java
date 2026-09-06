package com.uxplima.uxmessentials.servertweaks.application;

import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.uxplima.uxmessentials.servertweaks.domain.SignedChatDirective;
import com.uxplima.uxmessentials.servertweaks.domain.SignedSource;
import com.uxplima.uxmessentials.servertweaks.domain.SignedVelocityFrame;

/**
 * The backend's side of the SignedVelocity handshake, decoupled from Bukkit: a per-player, per-source FIFO of the
 * directives the proxy has sent but the matching chat/command event has not yet consumed. The channel listener
 * {@link #offer(SignedVelocityFrame) offers} each decoded frame here; the chat and command listeners
 * {@link #poll(UUID, SignedSource) poll} it as their events fire and apply whatever the proxy decided. Keeping this
 * coordination a plain-Java structure lets the whole port be exercised with fabricated frames, no proxy in sight.
 *
 * <p><b>Ownership:</b> concurrent-collection. The outer map is a {@link ConcurrentHashMap} keyed by
 * {@code (player, source)} and mutated only through {@code computeIfAbsent}; each bucket is a thread-safe
 * {@link ConcurrentLinkedQueue}. Frames arrive on the plugin-message thread while polls happen on the chat (async) and
 * command (tick) threads, so no lock is held and no I/O is done here.
 *
 * <p>Each bucket is bounded ({@value #MAX_PENDING_PER_KEY}): directives and events pair up one-to-one in normal use,
 * so a bucket that grows past the bound signals a proxy sending faster than events consume, and the oldest directive is
 * dropped rather than pinning memory. A poll on an empty bucket yields {@link Optional#empty()}, which the listeners
 * treat as "no proxy ruling. Leave the event alone"; that is why an install with no SignedVelocity proxy present is
 * inert.
 */
public final class SignedDirectiveQueue {

    /** Per-(player, source) cap on undelivered directives before the oldest is dropped. */
    static final int MAX_PENDING_PER_KEY = 16;

    private final ConcurrentHashMap<Key, Queue<SignedChatDirective>> pending = new ConcurrentHashMap<>();

    /** Record the proxy's directive for later matching against the player's next chat/command event. */
    public void offer(SignedVelocityFrame frame) {
        Objects.requireNonNull(frame, "frame");
        Queue<SignedChatDirective> queue =
                pending.computeIfAbsent(new Key(frame.player(), frame.source()), k -> new ConcurrentLinkedQueue<>());
        queue.add(frame.directive());
        while (queue.size() > MAX_PENDING_PER_KEY) {
            queue.poll();
        }
    }

    /** Take the next directive for this player's {@code source} stream, if the proxy has sent one. */
    public Optional<SignedChatDirective> poll(UUID player, SignedSource source) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(source, "source");
        Queue<SignedChatDirective> queue = pending.get(new Key(player, source));
        return queue == null ? Optional.empty() : Optional.ofNullable(queue.poll());
    }

    /** Drop every directive buffered for a player; called when they disconnect so nothing lingers. */
    public void forget(UUID player) {
        Objects.requireNonNull(player, "player");
        for (SignedSource source : SignedSource.values()) {
            pending.remove(new Key(player, source));
        }
    }

    /** Drop everything; called when the tweak is torn down on stop or reload. */
    public void clear() {
        pending.clear();
    }

    private record Key(UUID player, SignedSource source) {}
}
