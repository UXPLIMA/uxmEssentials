package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.moderation.application.port.JailDirectory;
import com.uxplima.uxmessentials.moderation.application.port.JailLocationStore;
import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link JailDirectory} that merges the read-only config jails ({@code moderation.conf}) with the
 * DB-backed jails an operator defines in-game with {@code /setjail}. A jail exists when either source knows it,
 * {@link #names()} is the sorted distinct union, and the countdown mode comes from the config: a stored jail
 * with no config override defaults to the module's countdown mode (the config directory answers {@code false}
 * for a name it does not know, which is the online-only default).
 *
 * <p>Name validation flows through here so {@code /jail <player> <name>} accepts a stored jail the same way it
 * accepts a config one; the location is resolved by {@link BukkitSanctions}, store first so an in-game
 * {@code /setjail} overrides a same-named config entry.
 */
@NullMarked
public final class CombinedJailDirectory implements JailDirectory {

    private final JailDirectory config;
    private final JailLocationStore store;

    public CombinedJailDirectory(JailDirectory config, JailLocationStore store) {
        this.config = Objects.requireNonNull(config, "config");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public boolean exists(String jail) {
        Objects.requireNonNull(jail, "jail");
        return config.exists(jail) || store.exists(StoredJail.normalise(jail));
    }

    @Override
    public boolean isWallClock(String jail) {
        Objects.requireNonNull(jail, "jail");
        return config.isWallClock(jail);
    }

    @Override
    public List<String> names() {
        Set<String> union = new LinkedHashSet<>(config.names());
        union.addAll(store.names());
        return union.stream().sorted().toList();
    }

    @Override
    public List<String> peekNames() {
        // Config-only union deliberately, so tab-completion stays non-blocking; the DB-backed store.names()
        // can hit the database and only the off-tick names() pays that cost.
        return config.peekNames();
    }
}
