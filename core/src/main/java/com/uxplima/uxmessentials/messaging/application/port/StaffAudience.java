package com.uxplima.uxmessentials.messaging.application.port;

import java.util.List;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port that enumerates the online players holding a given permission node, the {@code /helpop}
 * staff audience. The send path asks for everyone with {@code uxmessentials.helpop.receive} and fans the
 * request out to them; the adapter resolves online players through the kernel without the application
 * iterating {@code Bukkit.getOnlinePlayers()} itself.
 */
public interface StaffAudience {

    /** Every online player who currently holds {@code permissionNode}. */
    List<PlayerRef> onlineWith(String permissionNode);
}
