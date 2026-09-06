package com.uxplima.uxmessentials.vanish.adapter.outbound;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.VanishConfig;
import com.uxplima.uxmessentials.vanish.application.VanishMessageKey;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The persistent "you are vanished" action-bar indicator. A vanilla action bar fades after a few seconds, so the module
 * wiring re-arms {@link #refresh()} on a fixed cadence through the {@code Scheduler.repeatGlobal} port: each tick
 * re-sends the {@code vanish.actionbar} line to every currently-vanished online player, resolved in that player's own
 * locale. {@link #clear(PlayerRef)} wipes the lingering bar the moment a player reappears rather than waiting for it to
 * fade. Both are a no-op when {@code action-bar} is off.
 *
 * <p>The vanished roster is enumerated on the global region thread (Folia forbids reading {@code getOnlinePlayers()}
 * and here the online view of the store's keys, off it), then each {@code sendActionBar} hops to that player's own
 * entity thread, where the per-player send is valid under Folia. An offline player on either hop is a silent no-op.
 */
@NullMarked
public final class VanishActionBar {

    private final Server server;
    private final Scheduler scheduler;
    private final Messages messages;
    private final VanishStore store;
    private final VanishConfig config;
    private final MiniMessage miniMessage;

    public VanishActionBar(
            Server server, Scheduler scheduler, Messages messages, VanishStore store, VanishConfig config) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.store = Objects.requireNonNull(store, "store");
        this.config = Objects.requireNonNull(config, "config");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** Re-send the indicator to every currently-vanished online player; the repeating task drives this each tick. */
    public void refresh() {
        if (!config.actionBar()) {
            return;
        }
        scheduler.onGlobal(() -> {
            for (UUID id : store.vanished()) {
                @Nullable Player player = server.getPlayer(id);
                if (player != null && player.isOnline()) {
                    PlayerRef who = BukkitRefs.toRef(player);
                    scheduler.onEntity(who, () -> send(who));
                }
            }
        });
    }

    /** Send the indicator to {@code who} at once, so it appears the instant they vanish rather than on the next tick. */
    public void show(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (!config.actionBar()) {
            return;
        }
        scheduler.onEntity(who, () -> send(who));
    }

    /** Wipe the lingering indicator from {@code who} the moment they reappear. */
    public void clear(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (!config.actionBar()) {
            return;
        }
        scheduler.onEntity(who, () -> {
            @Nullable Player player = server.getPlayer(who.uuid());
            if (player != null && player.isOnline()) {
                player.sendActionBar(Component.empty());
            }
        });
    }

    private void send(PlayerRef who) {
        @Nullable Player player = server.getPlayer(who.uuid());
        if (player == null || !player.isOnline()) {
            return;
        }
        Component line = miniMessage.deserialize(
                messages.resolve(who, VanishMessageKey.VANISH_ACTIONBAR, Map.of()), StyleTags.resolver());
        player.sendActionBar(line);
    }
}
