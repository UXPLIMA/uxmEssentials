package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmlib.hud.Titles;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Flashes a short on-screen title or other configurable message types (BossBar, Chat, ActionBar, Subtitle)
 * when a player-initiated teleport lands, through uxmLib's {@link Titles}. The message can be custom or
 * resolved from the message catalog. Gated by the {@code teleport.arrival-title} toggle and configured
 * per-verb under {@code arrival-messages}.
 *
 * <p>{@link #arrived} touches the live player, so the caller must invoke it on the player's region thread
 * the teleport executor does, from its arrival callback.
 */
@NullMarked
public final class TeleportArrivalHud {

    private final Messages messages;
    private final MiniMessage miniMessage;
    private final Server server;
    private final Titles titles;
    private final TeleportSettings settings;
    private final Scheduler scheduler;

    public TeleportArrivalHud(Messages messages, Server server, TeleportSettings settings, Scheduler scheduler) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.server = Objects.requireNonNull(server, "server");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.miniMessage = MiniMessage.miniMessage();
        this.titles = new Titles();
    }

    /** Show the arrival messages/titles to {@code who} for a {@code kind} teleport if enabled. */
    public void arrived(PlayerRef who, TeleportKind kind) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kind, "kind");
        if (!settings.arrivalTitle()) {
            return;
        }
        @Nullable Player player = server.getPlayer(who.uuid());
        if (player == null || !player.isOnline()) {
            return;
        }

        Component titleComp = Component.empty();
        Component subtitleComp = Component.empty();
        boolean hasTitle = false;
        boolean hasSubtitle = false;
        int titleFadeIn = 150;
        int titleStay = 700;
        int titleFadeOut = 300;

        java.util.List<String> tokens = settings.getArrivalMessages(kind);
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            ArrivalMessage msg = parse(token);
            if (msg.message().isBlank()) {
                continue;
            }

            Component component;
            if (msg.message().equals(TeleportMessageKey.ARRIVED_TITLE.key())) {
                component = miniMessage.deserialize(
                        messages.resolve(who, TeleportMessageKey.ARRIVED_TITLE, Map.of()), StyleTags.resolver());
            } else if (msg.message().contains(".")
                    && !msg.message().contains(" ")
                    && !msg.message().contains("<")) {
                component =
                        miniMessage.deserialize(messages.resolve(who, msg::message, Map.of()), StyleTags.resolver());
            } else {
                component = miniMessage.deserialize(msg.message());
            }

            switch (msg.type()) {
                case "TITLE" -> {
                    titleComp = component;
                    hasTitle = true;
                    titleFadeIn = msg.fadeInMs();
                    titleStay = msg.stayMs();
                    titleFadeOut = msg.fadeOutMs();
                }
                case "SUBTITLE" -> {
                    subtitleComp = component;
                    hasSubtitle = true;
                    if (!hasTitle) {
                        titleFadeIn = msg.fadeInMs();
                        titleStay = msg.stayMs();
                        titleFadeOut = msg.fadeOutMs();
                    }
                }
                case "ACTION_BAR" -> player.sendActionBar(component);
                case "CHAT" -> player.sendMessage(component);
                case "BOSS_BAR" -> {
                    net.kyori.adventure.bossbar.BossBar bar = net.kyori.adventure.bossbar.BossBar.bossBar(
                            component,
                            1.0f,
                            net.kyori.adventure.bossbar.BossBar.Color.PURPLE,
                            net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS);
                    player.showBossBar(bar);
                    scheduler.asyncAfter(Duration.ofSeconds(msg.durationSecs()), () -> {
                        scheduler.onEntity(who, () -> {
                            Player live = server.getPlayer(who.uuid());
                            if (live != null && live.isOnline()) {
                                live.hideBossBar(bar);
                            }
                        });
                    });
                }
                default -> player.sendMessage(component);
            }
        }

        if (hasTitle || hasSubtitle) {
            titles.show(
                    player,
                    titleComp,
                    subtitleComp,
                    Duration.ofMillis(titleFadeIn),
                    Duration.ofMillis(titleStay),
                    Duration.ofMillis(titleFadeOut));
        }
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException expected) {
            // Ignored, fallback to default value
            return defaultValue;
        }
    }

    private static ArrivalMessage parse(String token) {
        String[] parts = token.split(";", -1);
        String type = parts[0].trim().toUpperCase(java.util.Locale.ROOT);
        String message = parts.length > 1 ? parts[1] : "";
        int fadeIn = 150;
        int stay = 700;
        int fadeOut = 300;
        int duration = 3;

        if (type.equals("TITLE") || type.equals("SUBTITLE")) {
            if (parts.length > 2) {
                fadeIn = parseIntOrDefault(parts[2], fadeIn);
            }
            if (parts.length > 3) {
                stay = parseIntOrDefault(parts[3], stay);
            }
            if (parts.length > 4) {
                fadeOut = parseIntOrDefault(parts[4], fadeOut);
            }
        } else if (type.equals("BOSS_BAR")) {
            if (parts.length > 2) {
                duration = parseIntOrDefault(parts[2], duration);
            }
        }
        return new ArrivalMessage(type, message, fadeIn, stay, fadeOut, duration);
    }

    // Named immutable because the message-lookup path takes its accessor as a MessageKey, and a key has to be one.
    // It already is: six components, all of them strings and ints.
    @com.google.errorprone.annotations.Immutable
    private record ArrivalMessage(
            String type, String message, int fadeInMs, int stayMs, int fadeOutMs, int durationSecs) {}
}
