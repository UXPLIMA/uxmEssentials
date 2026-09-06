package com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmlib.pipeline.PacketPipeline;
import org.jspecify.annotations.NullMarked;

/**
 * Injects the plugin-channel hider's packet interceptor into a joining player's connection, unless they hold the
 * channel-hide bypass permission. It mirrors the tablist-suppression connection wiring: a single shared
 * {@link ChannelHideListener} is registered once on the pipeline's registry, and this listener only splices the
 * per-connection interceptor that dispatches through it. The channel closing on quit removes the interceptor with it, so
 * no explicit eject is needed.
 *
 * <p><strong>Timing caveat.</strong> The interceptor is injected at {@link PlayerJoinEvent}, so it filters the
 * {@code minecraft:register} / {@code minecraft:unregister} advertisements the server sends from join onward. Any
 * registration burst the server emits during the login/configuration phase, before the join event, is not intercepted -
 * a documented limitation of a join-time splice over our current packet layer (the durable fix is an earlier,
 * connection-phase seam in uxmLib). The bypass check runs on the joining player's region thread, where reading their
 * permissions is safe.
 */
@NullMarked
public final class ChannelHideConnectionListener implements Listener {

    private final PacketPipeline pipeline;
    private final String bypassPermission;

    public ChannelHideConnectionListener(PacketPipeline pipeline, String bypassPermission) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        if (bypassPermission == null || bypassPermission.isBlank()) {
            throw new IllegalArgumentException("bypassPermission must be non-blank");
        }
        this.bypassPermission = bypassPermission;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission(bypassPermission)) {
            pipeline.inject(player);
        }
    }
}
