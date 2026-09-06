package com.uxplima.uxmessentials.vanish.adapter.outbound;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishState;
import org.jspecify.annotations.NullMarked;

/**
 * The transient vanish authority backing {@link VanishStore}: a {@code ConcurrentHashMap<UUID, VanishLevel>} holding
 * only the currently-vanished players. Reads and writes are lock-free map operations, so a sync command, the async
 * messaging resolution, and the nametag viewer cull all observe a coherent value from any thread.
 *
 * <p>Nothing here is persisted. A player is added by {@code /vanish}, read by every consumer, and dropped on quit
 * so a fresh join is never vanished (the state was forgotten), and the cross-server persist edge is a later phase. On
 * module stop {@link #clear()} drops every entry so a disable or reload leaves zero residual state.
 */
@NullMarked
public final class InMemoryVanishStore implements VanishStore {

    private final ConcurrentHashMap<UUID, VanishLevel> vanished = new ConcurrentHashMap<>();

    @Override
    public boolean isVanished(UUID who) {
        Objects.requireNonNull(who, "who");
        return vanished.containsKey(who);
    }

    @Override
    public void vanish(UUID who, VanishLevel level) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(level, "level");
        vanished.put(who, level);
    }

    @Override
    public void reveal(UUID who) {
        Objects.requireNonNull(who, "who");
        vanished.remove(who);
    }

    @Override
    public Optional<VanishLevel> levelOf(UUID who) {
        Objects.requireNonNull(who, "who");
        return Optional.ofNullable(vanished.get(who));
    }

    @Override
    public Set<UUID> vanished() {
        return Set.copyOf(vanished.keySet());
    }

    @Override
    public VanishState snapshot() {
        return new VanishState(vanished);
    }

    /** Drop every vanished player; called on module stop so a disabled context holds zero runtime state. */
    public void clear() {
        vanished.clear();
    }
}
