package com.uxplima.uxmessentials.shared.adapter.outbound.hud;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.hud.Titles;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The shared multi-channel broadcaster: fans a per-viewer {@link Component} out to every online player across a set
 * of {@link BroadcastChannel surfaces}, CHAT to {@code sendMessage}, ACTION_BAR to {@code sendActionBar},
 * TITLE/SUBTITLE through uxmLib's {@link Titles} with the configured {@link ChannelDisplay} timing, and BOSS_BAR via
 * an Adventure {@link BossBar} hidden after the configured seconds, and optionally plays a sound. Both the vote
 * thank-you/party broadcasts and the rotating communication announcer drive it, so the channel dispatch lives in one
 * place.
 *
 * <p>The caller supplies a {@code render} function rather than a finished component so the text can be built per
 * viewer (PlaceholderAPI, the viewer's locale) and so a recipient can be excluded: {@code render.apply(player)}
 * returning {@code null} skips that player entirely, which is how opt-out, a per-viewer display condition, and a
 * vanish/visibility check gate the broadcast.
 *
 * <h2>Concurrency</h2>
 * The online view is enumerated on the global region thread (broadcasts are driven off-tick, and
 * {@code Bukkit.getOnlinePlayers()} is not safe to iterate off-thread), then each recipient is delivered on their
 * own entity region thread so every live API touch is region-correct under Folia. The boss-bar hide is scheduled
 * off-tick via {@link Scheduler#asyncAfter} and hops back onto the recipient's entity thread to call
 * {@code hideBossBar}, never the legacy {@code BukkitScheduler}.
 */
@NullMarked
public final class ChannelBroadcaster {

    private final Scheduler scheduler;
    private final ChannelDisplay display;
    private final Titles titles;

    public ChannelBroadcaster(Scheduler scheduler, ChannelDisplay display) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.display = Objects.requireNonNull(display, "display");
        this.titles = new Titles();
    }

    /**
     * Fan {@code render} out to every online player across {@code channels}, playing {@code sound} when non-null.
     * For each online player the loop hops to that player's entity region thread, builds the per-viewer component
     * via {@code render}, skips the player when it returns {@code null}, and otherwise delivers it on every
     * requested channel. An empty channel set is a no-op.
     *
     * @param render builds the per-viewer component, or returns {@code null} to exclude that recipient
     * @param channels the surfaces the component is pushed to
     * @param sound the sound played alongside the broadcast, or {@code null} for none
     */
    public void broadcast(
            Function<Player, @Nullable Component> render, Set<BroadcastChannel> channels, @Nullable Sound sound) {
        Objects.requireNonNull(render, "render");
        Objects.requireNonNull(channels, "channels");
        if (channels.isEmpty()) {
            return;
        }
        Set<BroadcastChannel> safeChannels = Set.copyOf(channels);
        scheduler.onGlobal(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerRef who = new PlayerRef(player.getUniqueId(), player.getName());
                scheduler.onEntity(who, () -> deliverTo(who, render, safeChannels, sound));
            }
        });
    }

    /**
     * Deliver an already-built {@code component} to one specific online player across {@code channels}, playing
     * {@code sound} when non-null. Hops onto that player's entity region thread first, so it is safe to call off the
     * tick thread. Used by {@code /announce preview} to show one viewer an announcement without fanning it out or
     * applying the opt-out/condition gates. An empty channel set is a no-op.
     */
    public void deliverOne(PlayerRef who, Component component, Set<BroadcastChannel> channels, @Nullable Sound sound) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(channels, "channels");
        if (channels.isEmpty()) {
            return;
        }
        Set<BroadcastChannel> safeChannels = Set.copyOf(channels);
        scheduler.onEntity(who, () -> {
            @Nullable Player player = Bukkit.getPlayer(who.uuid());
            if (player == null || !player.isOnline()) {
                return;
            }
            for (BroadcastChannel channel : safeChannels) {
                deliverChannel(who, player, channel, component);
            }
            playSound(player, sound);
        });
    }

    private void deliverTo(
            PlayerRef who,
            Function<Player, @Nullable Component> render,
            Set<BroadcastChannel> channels,
            @Nullable Sound sound) {
        @Nullable Player player = Bukkit.getPlayer(who.uuid());
        if (player == null || !player.isOnline()) {
            return;
        }
        @Nullable Component component = render.apply(player);
        if (component == null) {
            return;
        }
        for (BroadcastChannel channel : channels) {
            deliverChannel(who, player, channel, component);
        }
        playSound(player, sound);
    }

    private void deliverChannel(PlayerRef who, Player player, BroadcastChannel channel, Component component) {
        switch (channel) {
            case CHAT -> player.sendMessage(component);
            case ACTION_BAR -> player.sendActionBar(component);
            case TITLE -> showTitle(player, component, Component.empty());
            case SUBTITLE -> showTitle(player, Component.empty(), component);
            case BOSS_BAR -> showBossBar(who, player, component);
        }
    }

    private void showTitle(Player player, Component title, Component subtitle) {
        titles.show(
                player,
                title,
                subtitle,
                Duration.ofMillis(display.titleFadeInMs()),
                Duration.ofMillis(display.titleStayMs()),
                Duration.ofMillis(display.titleFadeOutMs()));
    }

    private void showBossBar(PlayerRef who, Player player, Component component) {
        BossBar bar = BossBar.bossBar(component, 1.0f, display.bossBarColor(), display.bossBarOverlay());
        player.showBossBar(bar);
        scheduler.asyncAfter(
                Duration.ofSeconds(display.bossBarSeconds()),
                () -> scheduler.onEntity(who, () -> {
                    @Nullable Player live = Bukkit.getPlayer(who.uuid());
                    if (live != null && live.isOnline()) {
                        live.hideBossBar(bar);
                    }
                }));
    }

    private void playSound(Player player, @Nullable Sound sound) {
        if (sound == null) {
            return;
        }
        player.playSound(Objects.requireNonNull(player.getLocation(), "player location"), sound, 1.0f, 1.0f);
    }
}
