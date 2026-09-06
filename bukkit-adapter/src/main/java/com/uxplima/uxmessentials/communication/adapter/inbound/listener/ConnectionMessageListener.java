package com.uxplima.uxmessentials.communication.adapter.inbound.listener;

import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.communication.adapter.CommunicationSettings;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitInfoSender;
import com.uxplima.uxmessentials.communication.adapter.outbound.ChatMetaSource;
import com.uxplima.uxmessentials.communication.application.ResolveJoinMessage;
import com.uxplima.uxmessentials.communication.application.ResolveQuitMessage;
import com.uxplima.uxmessentials.communication.application.ResolvedMessage;
import com.uxplima.uxmessentials.communication.domain.InfoPage;
import com.uxplima.uxmessentials.communication.domain.PlaceholderBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Replaces the vanilla join and quit lines per the configured policies. A {@code DISABLE} channel clears the line
 * (nothing shows), a {@code DEFAULT} channel leaves the vanilla message untouched, and a {@code CUSTOM} channel
 * swaps in the operator template the {@code ResolveJoinMessage} / {@code ResolveQuitMessage} use case selected,
 * rendered through MiniMessage with {@code {player}}, {@code {displayname}}, {@code {count}}, and {@code {world}}
 * substituted. The templates are operator content, never {@code MessageKey}s.
 *
 * <p>The join line is chosen by the joiner's rank and history. The player's LuckPerms primary group picks a
 * per-group join template when the operator authored one (else the default join policy), and a player the server
 * has never seen before ({@code !hasPlayedBefore()}) is greeted with the first-join welcome instead, when a welcome
 * is authored, so every viewer sees the welcome rather than the ordinary join line. Both inputs flow into the pure
 * {@link ResolveJoinMessage}; this listener only reads them off the live event. The events fire on the
 * joining/quitting player's region thread, so reading the live player and setting the event component is
 * region-local, and the primary-group read is a non-blocking cached-meta lookup.
 *
 * <p>A joining player is also sent their personal MOTD. The {@code motd} lines, rendered through the
 * {@link BukkitInfoSender} (which expands PlaceholderAPI per viewer), delivered only to the joiner and never
 * broadcast. When no dedicated {@code motd} list is configured the listener falls back to the legacy {@code motd}
 * info page gated by {@code motd-on-join} (the same body {@code /motd} prints). This is the personal welcome the
 * joiner sees; it is independent of the broadcast join line every viewer sees, so a server can show the MOTD, the
 * join broadcast, both, or neither. The MOTD lines are operator content, never a {@code MessageKey}.
 */
@NullMarked
public final class ConnectionMessageListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final ResolveJoinMessage resolveJoin;
    private final ResolveQuitMessage resolveQuit;
    private final CommunicationSettings settings;
    private final BukkitInfoSender infoSender;
    private final ChatMetaSource metaSource;

    public ConnectionMessageListener(
            ResolveJoinMessage resolveJoin,
            ResolveQuitMessage resolveQuit,
            CommunicationSettings settings,
            BukkitInfoSender infoSender,
            ChatMetaSource metaSource) {
        this.resolveJoin = Objects.requireNonNull(resolveJoin, "resolveJoin");
        this.resolveQuit = Objects.requireNonNull(resolveQuit, "resolveQuit");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.infoSender = Objects.requireNonNull(infoSender, "infoSender");
        this.metaSource = Objects.requireNonNull(metaSource, "metaSource");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlaceholderBindings bindings = ConnectionPlaceholders.connection(player, onlineCount(player));
        boolean firstJoin = !player.hasPlayedBefore();
        @Nullable String group = metaSource.metaFor(player.getUniqueId()).primaryGroup().orElse(null);
        apply(resolveJoin.resolve(firstJoin, group, bindings), event::joinMessage);
        sendMotd(player);
    }

    /**
     * Send the joining player their personal MOTD. A dedicated {@code motd} list is preferred: its lines deliver
     * through the {@link BukkitInfoSender} (which hops to the player's region thread per line and expands
     * PlaceholderAPI), so this is safe from the join handler. When no list is configured the legacy {@code motd}
     * info page is sent instead, gated by {@code motd-on-join}. Only the joiner is sent the MOTD; the broadcast join
     * line above is set independently.
     */
    private void sendMotd(Player player) {
        List<String> motd = settings.motdLines();
        if (!motd.isEmpty()) {
            infoSender.send(BukkitRefs.toRef(player), motd);
            return;
        }
        if (!settings.motdOnJoin()) {
            return;
        }
        settings.motdPage().ifPresent(page -> sendPage(player, page));
    }

    private void sendPage(Player player, InfoPage page) {
        infoSender.send(BukkitRefs.toRef(player), page);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlaceholderBindings bindings = ConnectionPlaceholders.connection(player, onlineCount(player) - 1);
        apply(resolveQuit.resolve(bindings), event::quitMessage);
    }

    private static void apply(
            ResolvedMessage resolved,
            java.util.function.Consumer<@org.jspecify.annotations.Nullable Component> setter) {
        if (resolved.hasTemplate()) {
            setter.accept(MINI.deserialize(resolved.template().orElseThrow()));
        } else if (resolved.suppressVanilla()) {
            setter.accept(null);
        }
    }

    private static int onlineCount(Player joining) {
        // PlayerJoinEvent fires with the joining player already in the online set; for quit it is still counted,
        // so the caller subtracts one. The joining argument is unused beyond pinning the server reference.
        return joining.getServer().getOnlinePlayers().size();
    }
}
