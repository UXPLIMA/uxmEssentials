package com.uxplima.uxmessentials.vote.adapter.outbound;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelBroadcaster;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelDisplay;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.BroadcastVisibility;
import com.uxplima.uxmessentials.vote.application.port.VoteBroadcaster;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Bukkit {@link VoteBroadcaster}: fans one of the plugin's own {@link MessageKey} strings out to every
 * receiving online player across the configured {@link BroadcastChannel surfaces} through the shared
 * {@link ChannelBroadcaster}. The broadcaster owns the channel dispatch (CHAT, ACTION_BAR, TITLE/SUBTITLE,
 * BOSS_BAR) and the Folia-safe global-enumerate / per-entity-deliver / scheduled boss-bar-hide dance; this class
 * supplies the per-viewer render and the visibility gate.
 *
 * <p>The render function checks {@link BroadcastVisibility} for the hidden flag, returning {@code null} to skip a
 * player who hid broadcasts, and otherwise resolves the catalog {@code key} in that viewer's locale, folding the
 * shared {@code <prefix>} tag in exactly as {@code CommandFeedback} does. The configured broadcast sound is passed
 * straight through to the broadcaster.
 */
@NullMarked
public final class BukkitVoteBroadcaster implements VoteBroadcaster {

    /** The catalog key for the shared chat prefix the {@code <prefix>} tag expands to. */
    private static final MessageKey PREFIX = () -> "prefix";

    private final Messages messages;
    private final BroadcastVisibility visibility;
    private final ChannelBroadcaster channels;
    private final @Nullable Sound sound;
    private final MiniMessage miniMessage;

    public BukkitVoteBroadcaster(
            Scheduler scheduler, Messages messages, BroadcastVisibility visibility, BroadcastDisplay display) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(display, "display");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.channels = new ChannelBroadcaster(scheduler, display.toChannelDisplay());
        this.sound = display.sound();
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public void broadcast(MessageKey key, Map<String, String> placeholders, Set<BroadcastChannel> targets) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        Objects.requireNonNull(targets, "channels");
        if (targets.isEmpty()) {
            return;
        }
        Map<String, String> safePlaceholders = Map.copyOf(placeholders);
        channels.broadcast(player -> render(player, key, safePlaceholders), targets, sound);
    }

    /** Build the chat-prefixed component for {@code player}, or {@code null} when they have hidden broadcasts. */
    private @Nullable Component render(Player player, MessageKey key, Map<String, String> placeholders) {
        PlayerRef who = new PlayerRef(player.getUniqueId(), player.getName());
        if (!visibility.receivesBroadcasts(who)) {
            return null;
        }
        String rendered = messages.resolve(who, key, placeholders);
        TagResolver prefix = Placeholder.parsed("prefix", messages.resolve(who, PREFIX, Map.of()));
        return miniMessage.deserialize(rendered, prefix, StyleTags.resolver());
    }

    /**
     * The adapter-side broadcast display config: the {@link ChannelDisplay} timing (title fade/stay/fade, boss-bar
     * colour, overlay, and visible-seconds) plus the optional broadcast {@link Sound}. The boss-bar colour and
     * overlay are pre-resolved (with their tolerant fallbacks) by {@code BroadcastSettingsLoader}, and the sound is
     * {@code null} when none is configured or the name does not resolve. Kept as the vote-config-facing record;
     * {@link #toChannelDisplay()} hands the timing to the shared {@link ChannelBroadcaster}.
     *
     * @param titleFadeInMs the title fade-in in milliseconds
     * @param titleStayMs the title stay in milliseconds
     * @param titleFadeOutMs the title fade-out in milliseconds
     * @param bossBarColor the boss-bar colour
     * @param bossBarOverlay the boss-bar overlay
     * @param bossBarSeconds how long the boss bar stays visible before it is hidden
     * @param sound the broadcast sound, or {@code null} for none
     */
    public record BroadcastDisplay(
            int titleFadeInMs,
            int titleStayMs,
            int titleFadeOutMs,
            BossBar.Color bossBarColor,
            BossBar.Overlay bossBarOverlay,
            long bossBarSeconds,
            @Nullable Sound sound) {

        public BroadcastDisplay {
            Objects.requireNonNull(bossBarColor, "bossBarColor");
            Objects.requireNonNull(bossBarOverlay, "bossBarOverlay");
            if (bossBarSeconds < 1) {
                bossBarSeconds = 1;
            }
        }

        /** The timing-only view handed to the shared {@link ChannelBroadcaster} (the sound is passed separately). */
        public ChannelDisplay toChannelDisplay() {
            return new ChannelDisplay(
                    titleFadeInMs, titleStayMs, titleFadeOutMs, bossBarColor, bossBarOverlay, bossBarSeconds);
        }

        /** Resolve a {@link BossBar.Color} name tolerant of case; unknown names fall back to {@code PURPLE}. */
        public static BossBar.Color color(String name) {
            try {
                return BossBar.Color.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                return BossBar.Color.PURPLE;
            }
        }

        /** Resolve a {@link BossBar.Overlay} name tolerant of case; unknown names fall back to {@code PROGRESS}. */
        public static BossBar.Overlay overlay(String name) {
            try {
                return BossBar.Overlay.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                return BossBar.Overlay.PROGRESS;
            }
        }
    }
}
