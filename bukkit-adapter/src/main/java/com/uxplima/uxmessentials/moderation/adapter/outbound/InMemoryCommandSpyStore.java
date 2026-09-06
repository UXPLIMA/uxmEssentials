package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.moderation.application.port.CommandSpyStore;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link CommandSpyStore} implementation: which staff currently watch the commands other players run.
 * Spying is session state. A per-player flag held in-memory and dropped on {@code stop()} via
 * {@link #clear()}, so a relog clears it, which is the staff-tool default. {@link #activeSpies()} resolves
 * each spying uuid to a currently-online {@link PlayerRef}, skipping any who logged off, so the
 * command-watch fan-out never targets an offline observer. Mirrors the messaging in-memory socialspy store.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. The spy set is a {@link ConcurrentHashMap}-backed key set, read
 * from the command-watch listener (a region thread) and written by {@code /commandspy}.
 */
@NullMarked
public final class InMemoryCommandSpyStore implements CommandSpyStore {

    private final Set<UUID> spies = ConcurrentHashMap.newKeySet();

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
    public List<PlayerRef> activeSpies() {
        List<PlayerRef> online = new ArrayList<>();
        for (UUID id : spies) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                online.add(BukkitRefs.toRef(player));
            }
        }
        return List.copyOf(online);
    }

    /** Drop every active spy on module stop. */
    public void clear() {
        spies.clear();
    }
}
