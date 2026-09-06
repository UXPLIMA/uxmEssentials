package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFlags;
import com.uxplima.uxmlib.item.PdcFlag;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link TeleportFlags} implementation. The {@code /tptoggle} un-teleportable switch is a per-player
 * preference that survives relog, so it is stamped in PDC under a single pre-created key. The
 * {@code /tpblock} list is transient session state. A per-player set of blocked requester uuids held
 * in-memory and dropped on {@code stop()} via {@link #clear()}.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. The block index is a {@link ConcurrentHashMap} of
 * {@link ConcurrentHashMap}-backed key sets; PDC reads/writes go through the owning {@code Player}.
 * The {@link NamespacedKey} is built once in the constructor, never on a hot path.
 */
@NullMarked
public final class PdcTeleportFlags implements TeleportFlags {

    private final NamespacedKey toggleKey;
    private final NamespacedKey autoKey;
    private final ConcurrentHashMap<UUID, Set<UUID>> blocks = new ConcurrentHashMap<>();

    public PdcTeleportFlags(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.toggleKey = new NamespacedKey(plugin, "tp-toggle-off");
        this.autoKey = new NamespacedKey(plugin, "tp-auto-accept");
    }

    @Override
    public boolean acceptsRequests(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return true; // an offline target cannot be requested anyway; default to accepting
        }
        return !PdcFlag.get(player.getPersistentDataContainer(), toggleKey);
    }

    @Override
    public boolean toggleRequests(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return true;
        }
        boolean nowAccepting = !acceptsRequests(who);
        PdcFlag.set(player.getPersistentDataContainer(), toggleKey, !nowAccepting);
        return nowAccepting;
    }

    @Override
    public void setAcceptsRequests(PlayerRef who, boolean accepting) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return; // an offline player defaults to accepting; nothing to stamp
        }
        PdcFlag.set(player.getPersistentDataContainer(), toggleKey, !accepting);
    }

    @Override
    public boolean autoAccepts(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return false; // an offline target cannot be requested anyway; default to not auto-accepting
        }
        return PdcFlag.get(player.getPersistentDataContainer(), autoKey);
    }

    @Override
    public boolean toggleAutoAccepts(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return false;
        }
        boolean nowAuto = !autoAccepts(who);
        PdcFlag.set(player.getPersistentDataContainer(), autoKey, nowAuto);
        return nowAuto;
    }

    @Override
    public boolean hasBlocked(PlayerRef target, PlayerRef requester) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(requester, "requester");
        Set<UUID> blocked = blocks.get(target.uuid());
        return blocked != null && blocked.contains(requester.uuid());
    }

    @Override
    public void block(PlayerRef blocker, PlayerRef requester) {
        Objects.requireNonNull(blocker, "blocker");
        Objects.requireNonNull(requester, "requester");
        blocks.computeIfAbsent(blocker.uuid(), id -> ConcurrentHashMap.newKeySet())
                .add(requester.uuid());
    }

    @Override
    public void unblock(PlayerRef blocker, PlayerRef requester) {
        Objects.requireNonNull(blocker, "blocker");
        Objects.requireNonNull(requester, "requester");
        Set<UUID> blocked = blocks.get(blocker.uuid());
        if (blocked != null) {
            blocked.remove(requester.uuid());
        }
    }

    /** Drop the in-memory block index on module stop. */
    public void clear() {
        blocks.clear();
    }
}
