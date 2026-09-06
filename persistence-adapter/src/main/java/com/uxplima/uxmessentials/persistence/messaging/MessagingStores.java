package com.uxplima.uxmessentials.persistence.messaging;

import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the messaging context's persistence adapters, so the consuming bukkit-adapter wires the
 * {@link MailRepository} and {@link IgnoreStore} from the {@link Persistence} handle it already holds without
 * ever naming a jOOQ type (jOOQ is an {@code implementation} dependency of this module, kept off the
 * consumer's compile classpath).
 *
 * <p>The mailbox repository is the plain jOOQ adapter. A box mutates on every read (mark-all-read) and on
 * the expiry sweep, so it is left uncached for correctness rather than fighting invalidation. The ignore
 * store is the jOOQ adapter behind a Caffeine read-cache (the ignore list is read once per delivered
 * message, so caching the small per-owner list pays off; write-through at the delegate, invalidate in the
 * cache).
 */
@NullMarked
public final class MessagingStores {

    private MessagingStores() {}

    /** A jOOQ {@link MailRepository} over the shared persistence DSL. */
    public static MailRepository mail(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqMailRepository(persistence.dsl());
    }

    /** A cached jOOQ {@link IgnoreStore} over the shared persistence DSL. */
    public static IgnoreStore ignores(Persistence persistence) {
        return cachedIgnores(persistence);
    }

    /**
     * As {@link #ignores(Persistence)} but returned as its concrete decorator type, so the wiring can hand the
     * cross-server bus a per-owner invalidation hook on the same cache the {@code /msg} delivery path reads, a
     * remote {@code /ignore} drops exactly that owner's cached set. Same backing as {@link #ignores}; this
     * overload exposes the decorator only so the invalidation seam can reach it.
     */
    public static CachedIgnoreStore cachedIgnores(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedIgnoreStore(new JooqIgnoreStore(persistence.dsl()));
    }
}
