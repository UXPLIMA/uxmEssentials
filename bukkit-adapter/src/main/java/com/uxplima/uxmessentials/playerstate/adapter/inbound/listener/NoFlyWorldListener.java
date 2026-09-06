package com.uxplima.uxmessentials.playerstate.adapter.inbound.listener;

import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import com.uxplima.uxmessentials.playerstate.application.NoFlyWorldPolicy;
import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Strips plugin-granted flight when a flying player enters a no-fly world ({@code no-fly-worlds}). On
 * {@link PlayerChangedWorldEvent} into a listed world, a player who is currently flying has
 * {@code setAllowFlight(false)}/{@code setFlying(false)} applied on their owning entity thread via the
 * {@code Scheduler} port and is told flight is disabled here, unless they hold the bypass node
 * {@code uxmessentials.playerstate.fly.allworlds}.
 *
 * <p>Gamemode flight is left to the gamemode: a creative or spectator player is never touched, so the
 * listener only removes flight the {@code /fly} toggle would have granted. The world change fires on the
 * player's region thread, but the Bukkit mutation is still routed through the {@code Scheduler} so it lands
 * on the owning entity thread, valid on Folia. When the configured list is empty the listener short-circuits
 * before any work.
 */
@NullMarked
public final class NoFlyWorldListener implements Listener {

    /** The node that exempts a player from no-fly worlds. */
    public static final String BYPASS_NODE = "uxmessentials.playerstate.fly.allworlds";

    private final NoFlyWorldPolicy policy;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink sink;

    public NoFlyWorldListener(NoFlyWorldPolicy policy, Scheduler scheduler, Messages messages, MessageSink sink) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (policy.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (!shouldStrip(player)) {
            return;
        }
        PlayerRef who = BukkitRefs.toRef(player);
        scheduler.onEntity(who, () -> strip(who));
    }

    private boolean shouldStrip(Player player) {
        return player.getAllowFlight()
                && !grantsFlight(player.getGameMode())
                && !player.hasPermission(BYPASS_NODE)
                && policy.isNoFly(player.getWorld().getName());
    }

    private void strip(PlayerRef who) {
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null
                || !player.getAllowFlight()
                || grantsFlight(player.getGameMode())
                || player.hasPermission(BYPASS_NODE)) {
            return;
        }
        player.setFlying(false);
        player.setAllowFlight(false);
        sink.deliver(who, messages.resolve(who, PlayerstateMessageKey.FLY_WORLD_DISABLED, Map.of()));
    }

    private static boolean grantsFlight(@Nullable GameMode mode) {
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }
}
