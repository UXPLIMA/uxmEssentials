package com.uxplima.uxmessentials.messaging.adapter.outbound;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link SocialSpyStore} implementation: which staff currently observe private messages. Spying is
 * session state, a per-player global ALL flag and a per-player target set, both held in-memory and dropped
 * on {@code stop()} via {@link #clear()}, so a relog clears it, which is the staff-tool default.
 * {@link #activeSpies()} and {@link #observersOf} resolve each spying uuid to a currently-online
 * {@link PlayerRef}, skipping any who logged off, so the send-path fan-out never targets an offline observer.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. The ALL set is a {@link ConcurrentHashMap}-backed key set and the
 * target sets live in a {@link ConcurrentHashMap} of key sets, read from the send path (a region/command
 * thread) and written by {@code /socialspy}.
 */
@NullMarked
public final class InMemorySocialSpyStore implements SocialSpyStore {

    private final Set<UUID> spies = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<UUID>> targets = new ConcurrentHashMap<>();

    @Override
    public boolean isSpying(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return spies.contains(who.uuid());
    }

    @Override
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (spies.remove(who.uuid())) {
            return false;
        }
        spies.add(who.uuid());
        return true;
    }

    @Override
    public boolean toggleTarget(PlayerRef spy, PlayerRef target) {
        Objects.requireNonNull(spy, "spy");
        Objects.requireNonNull(target, "target");
        Set<UUID> watched = targets.computeIfAbsent(spy.uuid(), id -> ConcurrentHashMap.newKeySet());
        if (watched.remove(target.uuid())) {
            targets.computeIfPresent(spy.uuid(), (id, set) -> set.isEmpty() ? null : set);
            return false;
        }
        watched.add(target.uuid());
        return true;
    }

    @Override
    public List<PlayerRef> activeSpies() {
        List<PlayerRef> online = new ArrayList<>();
        for (UUID id : spies) {
            PlayerRef ref = onlineRef(id);
            if (ref != null) {
                online.add(ref);
            }
        }
        return List.copyOf(online);
    }

    @Override
    public Set<PlayerRef> observersOf(PlayerRef sender, PlayerRef target) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(target, "target");
        Set<PlayerRef> observers = new HashSet<>();
        for (UUID id : spies) {
            addIfOnline(observers, id);
        }
        for (Map.Entry<UUID, Set<UUID>> entry : targets.entrySet()) {
            if (entry.getValue().contains(sender.uuid()) || entry.getValue().contains(target.uuid())) {
                addIfOnline(observers, entry.getKey());
            }
        }
        return observers;
    }

    private void addIfOnline(Set<PlayerRef> observers, UUID id) {
        PlayerRef ref = onlineRef(id);
        if (ref != null) {
            observers.add(ref);
        }
    }

    private static @Nullable PlayerRef onlineRef(UUID id) {
        Player player = Bukkit.getPlayer(id);
        return player == null ? null : BukkitRefs.toRef(player);
    }

    /** Drop every active spy and target set on module stop. */
    public void clear() {
        spies.clear();
        targets.clear();
    }
}
