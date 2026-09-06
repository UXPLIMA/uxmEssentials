package com.uxplima.uxmessentials.persistence.teleport;

import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.teleport.application.port.RtpPoolStore;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the teleport context's durable RTP pool store, so the consuming bukkit-adapter wires an
 * {@link RtpPoolStore} from the {@link Persistence} handle it already holds without ever naming a jOOQ type (jOOQ
 * is an {@code implementation} dependency of this module, kept off the consumer's compile classpath). The returned
 * store is the Caffeine-fronted jOOQ adapter: write-through at the database, count cached and invalidated on write.
 */
@NullMarked
public final class RtpPoolStores {

    private RtpPoolStores() {}

    /** A cached jOOQ {@link RtpPoolStore} over the shared persistence DSL, bounded to {@code maxPerWorld} per world. */
    public static RtpPoolStore cached(Persistence persistence, int maxPerWorld, Clock clock) {
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(clock, "clock");
        return new CachedRtpPoolStore(new JooqRtpPoolRepository(persistence.dsl(), maxPerWorld, clock));
    }
}
