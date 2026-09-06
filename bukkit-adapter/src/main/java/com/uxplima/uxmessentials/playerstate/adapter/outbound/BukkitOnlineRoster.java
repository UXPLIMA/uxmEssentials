package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Snapshots the online roster as plain {@link PlayerRef}s for the {@link PlaytimeSampler}. The sampler only ever
 * invokes {@link #get()} from inside a {@code scheduler.onGlobal(...)} lambda, so the {@code getOnlinePlayers()}
 * read here runs on the global region thread: the only thread on which the roster is coherent under Folia. The
 * snapshot copies uuid/name only and touches no other live player state, so the refs are safe to hand to the
 * sampler's off-tick writes.
 *
 * <h2>Concurrency</h2>
 * Ownership: stateless; the single read is global-region-confined by its caller. Listed in
 * {@code FoliaThreadingDriftTest} under GLOBAL for that reason.
 */
@NullMarked
public final class BukkitOnlineRoster implements Supplier<List<PlayerRef>> {

    private final Plugin plugin;

    public BukkitOnlineRoster(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public List<PlayerRef> get() {
        List<PlayerRef> refs = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            refs.add(BukkitRefs.toRef(player));
        }
        return refs;
    }
}
