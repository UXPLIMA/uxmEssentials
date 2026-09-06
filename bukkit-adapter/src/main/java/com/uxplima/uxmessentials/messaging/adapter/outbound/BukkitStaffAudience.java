package com.uxplima.uxmessentials.messaging.adapter.outbound;

import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.messaging.application.port.StaffAudience;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link StaffAudience} implementation: every online player holding a permission node, the
 * {@code /helpop} staff audience. The application layer never iterates {@code Bukkit.getOnlinePlayers()}
 * itself; it asks this adapter, which maps each matching online player to a {@link PlayerRef}. A help-op
 * fan-out is an infrequent action, so the per-call scan of the online set is acceptable.
 *
 * <p>The enumeration must run on the global region thread. The one thread where the roster is consistently
 * readable on Folia. Two callers already own it: {@code HelpOp} is reached only from the {@code /helpop}
 * Brigadier handler and the staff-chat fan-out only from the {@code /staffchat} handler, both of which Paper
 * dispatches on the global region thread. The third, the staff-roster enter/exit alert, is driven by the
 * {@code /staffmode} enter/exit domain events, which fire synchronously on the <i>target's entity</i> region
 * thread, not global; so {@code MessagingStaffAlerts} wraps its own call to {@link #onlineWith} (and the
 * per-recipient delivery) in a {@code scheduler.onGlobal} hop. With every call thus arriving on the global
 * thread, the read and permission check here need no hop of their own.
 */
@NullMarked
public final class BukkitStaffAudience implements StaffAudience {

    @Override
    public List<PlayerRef> onlineWith(String permissionNode) {
        Objects.requireNonNull(permissionNode, "permissionNode");
        java.util.List<PlayerRef> audience = new java.util.ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(permissionNode)) {
                audience.add(BukkitRefs.toRef(player));
            }
        }
        return List.copyOf(audience);
    }
}
