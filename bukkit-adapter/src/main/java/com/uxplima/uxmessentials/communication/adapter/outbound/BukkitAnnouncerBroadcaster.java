package com.uxplima.uxmessentials.communication.adapter.outbound;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

import com.uxplima.uxmessentials.communication.application.port.BroadcastOptOutStore;
import com.uxplima.uxmessentials.communication.domain.Announcement;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRegistryKeys;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelBroadcaster;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Delivers a rotating {@link Announcement} across its configured channels, and the one-off {@code /broadcast} /
 * {@code /broadcastworld} chat lines, to every online player who has not opted out via {@code /broadcasttoggle}.
 *
 * <p>A rotating announcement is delivered through the shared {@link ChannelBroadcaster}: for each online player the
 * broadcaster hops to their entity thread and asks this class's per-viewer render function for the text. The render
 * function gates the recipient three ways. It returns {@code null} (skipping the player) when they have opted out
 * <em>or</em> when the announcement's {@link com.uxplima.uxmessentials.shared.display.DisplayCondition} does not
 * match that viewer, and otherwise renders each operator line through {@link HudText} (per-viewer PlaceholderAPI
 * then MiniMessage) and joins them with newlines. The lines are operator content, never a {@code MessageKey} and
 * never parity-checked. The announcement's optional sound is resolved once through {@link BukkitRegistryKeys} and
 * passed to the broadcaster.
 *
 * <p>The legacy one-off {@code /broadcast} path stays on the {@link MessageSink}: a single raw MiniMessage string
 * delivered to every opted-in viewer (optionally restricted to one world), with the sink performing the per-viewer
 * region hop and parse.
 *
 * <p><b>Centering</b> (the {@link Announcement#centered()} flag) is not yet applied: chat centering needs glyph-width
 * measurement that Adventure does not expose, so the flag is parsed and carried but the lines render left-aligned
 * for now. The flag is honoured by the model so a later width-aware centering pass needs no config change.
 */
@NullMarked
public final class BukkitAnnouncerBroadcaster {

    private final MessageSink sink;
    private final BroadcastOptOutStore optOut;
    private final ChannelBroadcaster channels;
    private final Function<Player, ConditionContext> conditionContext;
    private final Scheduler scheduler;

    public BukkitAnnouncerBroadcaster(
            MessageSink sink,
            BroadcastOptOutStore optOut,
            ChannelBroadcaster channels,
            Function<Player, ConditionContext> conditionContext,
            Scheduler scheduler) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.optOut = Objects.requireNonNull(optOut, "optOut");
        this.channels = Objects.requireNonNull(channels, "channels");
        this.conditionContext = Objects.requireNonNull(conditionContext, "conditionContext");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Broadcast {@code announcement} across its channels to every online, opted-in player it matches. The sound is
     * resolved once here (a blank or unknown key is a silent no-op) and the per-viewer render is handed to the
     * shared {@link ChannelBroadcaster}.
     */
    public void broadcast(Announcement announcement) {
        Objects.requireNonNull(announcement, "announcement");
        @Nullable Sound sound = announcement.sound().map(BukkitRegistryKeys::resolveSound).orElse(null);
        channels.broadcast(player -> render(announcement, player), announcement.channels(), sound);
    }

    /**
     * Show {@code announcement} to {@code viewer} alone, across its channels, the {@code /announce preview} path.
     * Unlike {@link #broadcast(Announcement)} this bypasses the opt-out and condition gates (a preview should always
     * render for the previewer) and fans out to no one else; the lines still expand PlaceholderAPI for the viewer.
     */
    public void preview(Announcement announcement, Player viewer) {
        Objects.requireNonNull(announcement, "announcement");
        Objects.requireNonNull(viewer, "viewer");
        @Nullable Sound sound = announcement.sound().map(BukkitRegistryKeys::resolveSound).orElse(null);
        Component component = renderLines(announcement, viewer.getUniqueId());
        channels.deliverOne(BukkitRefs.toRef(viewer), component, announcement.channels(), sound);
    }

    /** The per-viewer text, or {@code null} to exclude this viewer (opted out, or the condition does not match). */
    private @Nullable Component render(Announcement announcement, Player player) {
        PlayerRef who = BukkitRefs.toRef(player);
        if (!optOut.receivesBroadcasts(who)) {
            return null;
        }
        if (!announcement.condition().matches(conditionContext.apply(player))) {
            return null;
        }
        return renderLines(announcement, player.getUniqueId());
    }

    /** Render every operator line for {@code viewer} (PlaceholderAPI then MiniMessage) and join them with newlines. */
    private static Component renderLines(Announcement announcement, UUID viewer) {
        return Component.join(
                JoinConfiguration.newlines(),
                announcement.lines().stream()
                        .map(line -> HudText.render(viewer, line))
                        .toList());
    }

    /** Send {@code line} to every online, opted-in player. */
    public void broadcast(String line) {
        Objects.requireNonNull(line, "line");
        scheduler.onGlobal(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerRef who = BukkitRefs.toRef(player);
                if (optOut.receivesBroadcasts(who)) {
                    sink.deliver(who, line);
                }
            }
        });
    }

    /**
     * Send {@code line} only to online, opted-in players in the world identified by {@code worldId}, the
     * fan-out a {@code /broadcastworld} restricts to the sender's world. Same opt-out and per-viewer region
     * hop as {@link #broadcast(String)}; a player who left the world between the scan and the send is simply
     * skipped.
     */
    public void broadcastToWorld(String line, UUID worldId) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(worldId, "worldId");
        scheduler.onGlobal(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getWorld().getUID().equals(worldId)) {
                    continue;
                }
                PlayerRef who = BukkitRefs.toRef(player);
                if (optOut.receivesBroadcasts(who)) {
                    sink.deliver(who, line);
                }
            }
        });
    }

    /**
     * Resolve the current online player count on the global region thread (Folia forbids reading the roster off it)
     * and hand it to {@code consumer} there. The announcer rotation drives this from its async tick to gate the
     * min-players check, then picks and broadcasts from the same global hop so the count and the fan-out see one
     * consistent roster.
     */
    public void withOnlineCount(java.util.function.IntConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer");
        scheduler.onGlobal(() -> consumer.accept(Bukkit.getOnlinePlayers().size()));
    }
}
