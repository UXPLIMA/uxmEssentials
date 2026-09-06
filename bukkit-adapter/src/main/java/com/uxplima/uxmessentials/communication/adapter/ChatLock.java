package com.uxplima.uxmessentials.communication.adapter;

import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.NullMarked;

/**
 * The communication context's single global chat-lock flag: when set, public chat from non-bypass players is
 * cancelled. One {@link AtomicBoolean} owned by the communication context, flipped only by {@code /togglechat}
 * and read by the {@code ChatLockListener} on the async chat thread; an {@code AtomicBoolean} (not a
 * per-player map) because the lock is a single server-wide switch, not per-player state.
 *
 * <p><b>Ownership:</b> the toggle command is the only writer; the listener and the doctor line are readers.
 * The flag is in-memory only. A server restart clears the lock (documented on {@code /togglechat}); a chat
 * freeze is a transient incident control, not durable state, so there is nothing to persist.
 */
@NullMarked
public final class ChatLock {

    private final AtomicBoolean locked = new AtomicBoolean(false);

    /** Flip the lock and return its new state ({@code true} once locked). */
    public boolean toggle() {
        boolean current;
        boolean next;
        do {
            current = locked.get();
            next = !current;
        } while (!locked.compareAndSet(current, next));
        return next;
    }

    /** True while public chat is locked for non-bypass players. */
    public boolean isLocked() {
        return locked.get();
    }
}
