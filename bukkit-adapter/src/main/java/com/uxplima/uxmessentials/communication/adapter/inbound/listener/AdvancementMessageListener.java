package com.uxplima.uxmessentials.communication.adapter.inbound.listener;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import io.papermc.paper.advancement.AdvancementDisplay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;

import com.uxplima.uxmessentials.communication.application.port.BroadcastOptOutStore;
import com.uxplima.uxmessentials.communication.domain.AdvancementFilter;
import com.uxplima.uxmessentials.communication.domain.AdvancementNoticeConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRegistryKeys;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelBroadcaster;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Broadcasts a player earning an advancement using the operator's own template instead of (or alongside) the vanilla
 * chat line, gated by the configured {@link AdvancementNoticeConfig} filtering. It mirrors the death/connection
 * listeners: it reads the live config, decides via the pure {@link AdvancementFilter}, edits the vanilla event
 * message, and fans the custom notice out across the configured channels through the shared
 * {@link ChannelBroadcaster}.
 *
 * <p><b>Vanilla-message suppression.</b> The vanilla advancement chat line is cleared ({@code event.message(null)})
 * in exactly two cases: (1) the earner is vanished. We hide the achievement entirely; and
 * (2) we are announcing our own notice, so the player does not see a duplicate (vanilla line plus our broadcast).
 * When the filter says do-not-announce for any other reason (feature disabled, a recipe, not announce-to-chat,
 * deny-listed, outside a non-empty allow-list) the vanilla line is left untouched, so turning the feature off is
 * fully transparent.
 *
 * <p><b>Vanish.</b> Whether the earner is vanished is asked of an injected {@link Predicate} so the listener stays
 * decoupled from the presence context; the wiring supplies the soft-coupled check (Bukkit's {@code Player#canSee}
 * visibility graph, the same seam messaging and nametags use), which degrades to "never vanished" when presence is
 * absent. A vanished earner's advancement is suppressed and never broadcast. The predicate enumerates the whole
 * roster and reads every other player's {@code canSee} visibility. A cross-region read that tears on Folia off the
 * global region, so the vanish test and the broadcast decision both run inside a single {@code scheduler.onGlobal}
 * hop, where the roster and the visibility graph are consistently readable. Clearing the vanilla line is the one
 * piece that stays on the firing thread (a {@code PlayerAdvancementDoneEvent} mutation must be applied synchronously
 * before the handler returns); it is cleared unconditionally once the filter says announce, so deferring only the
 * vanish/broadcast decision keeps the exact suppression semantics.
 *
 * <p><b>Template.</b> The operator template (a per-advancement override or the global one) is rendered per viewer:
 * {@code {player}} is the earner's name, {@code {advancement}} the namespaced key, {@code {title}} and
 * {@code {description}} the advancement display's title/description flattened to plain text via
 * {@link PlainTextComponentSerializer} so they compose cleanly inside the MiniMessage template, then the result runs
 * through {@link HudText} (per-viewer PlaceholderAPI then MiniMessage). A viewer who opted out of broadcasts via
 * {@code /broadcasttoggle} renders {@code null} and is skipped: advancement notices are broadcasts.
 *
 * <p><b>Translatable titles.</b> A vanilla advancement's title and description are {@link Component#translatable
 * translatable} components keyed by name (e.g. {@code advancements.end.kill_dragon.title}). Flattening those straight
 * to plain text yields the raw translation key rather than readable text, so each is first resolved through
 * {@link GlobalTranslator#render(Component, Locale)} in the server-default locale before it is serialized, the project
 * is English-only, so that locale is {@link Locale#ENGLISH}. A title already built from literal text passes through the
 * render unchanged.
 *
 * <p>The event fires on the earner's region thread. The handler clears the vanilla line there, then hops the vanish
 * test and the broadcast decision onto {@code scheduler.onGlobal} (the only thread that can read the roster's
 * {@code canSee} graph consistently on Folia); the {@link ChannelBroadcaster} then performs its own per-entity hops
 * for delivery, so no region thread ever blocks waiting on a cross-region read.
 */
@NullMarked
public final class AdvancementMessageListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String RECIPE_INFIX = ":recipes/";

    private final Supplier<AdvancementNoticeConfig> config;
    private final ChannelBroadcaster channels;
    private final BroadcastOptOutStore optOut;
    private final Predicate<Player> vanished;
    private final Scheduler scheduler;
    private final Locale locale;

    public AdvancementMessageListener(
            Supplier<AdvancementNoticeConfig> config,
            ChannelBroadcaster channels,
            BroadcastOptOutStore optOut,
            Predicate<Player> vanished,
            Scheduler scheduler,
            Locale locale) {
        this.config = Objects.requireNonNull(config, "config");
        this.channels = Objects.requireNonNull(channels, "channels");
        this.optOut = Objects.requireNonNull(optOut, "optOut");
        this.vanished = Objects.requireNonNull(vanished, "vanished");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.locale = Objects.requireNonNull(locale, "locale");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        AdvancementNoticeConfig cfg = config.get();
        Player earner = event.getPlayer();
        String key = event.getAdvancement().getKey().toString();
        @Nullable AdvancementDisplay display = event.getAdvancement().getDisplay();
        boolean announceable = display != null && display.doesAnnounceToChat();
        if (!AdvancementFilter.shouldAnnounce(cfg, key, isRecipe(key), announceable)) {
            return;
        }
        // The vanilla line is cleared in both surviving outcomes (vanished earner, or our own broadcast), so clear it
        // here on the firing thread: the event mutation must be applied synchronously before this handler returns.
        event.message(null);
        // The vanish check enumerates the roster and reads every other player's canSee, which only the global region
        // can read consistently on Folia; the broadcast decision rides the same hop so a vanished earner is suppressed
        // there rather than after a cross-region read. Per-recipient delivery hops onEntity inside the broadcaster.
        scheduler.onGlobal(() -> {
            if (vanished.test(earner)) {
                return;
            }
            String template =
                    substitute(AdvancementFilter.templateFor(cfg, key), earner.getName(), key, display, locale);
            @Nullable Sound sound = cfg.sound().map(BukkitRegistryKeys::resolveSound).orElse(null);
            channels.broadcast(viewer -> render(viewer, template), cfg.channels(), sound);
        });
    }

    /** The per-viewer notice, or {@code null} to skip a viewer who has opted out of broadcasts. */
    private @Nullable Component render(Player viewer, String template) {
        PlayerRef who = BukkitRefs.toRef(viewer);
        if (!optOut.receivesBroadcasts(who)) {
            return null;
        }
        return HudText.render(who.uuid(), template);
    }

    /** Whether {@code key} names a recipe-unlock advancement ({@code <namespace>:recipes/…}). */
    static boolean isRecipe(String key) {
        return key.contains(RECIPE_INFIX);
    }

    /**
     * Substitute the advancement placeholders into {@code template}: {@code {player}} → the earner's name,
     * {@code {advancement}} → the namespaced {@code key}, and {@code {title}}/{@code {description}} → the display's
     * title/description flattened to plain text. A vanilla title/description is a translatable component, so each is
     * resolved through {@link GlobalTranslator} in {@code locale} before flattening, otherwise the raw translation
     * key ({@code advancements.end.kill_dragon.title}) would surface instead of readable text. A {@code null} display
     * (recipes and some hidden advancements have none) substitutes an empty title and description rather than failing,
     * so a template referencing them still renders. Package-private so it can be exercised directly: MockBukkit cannot
     * construct a real {@code Advancement}/{@code AdvancementDisplay}, so the substitution is tested through this seam.
     */
    static String substitute(
            String template, String player, String key, @Nullable AdvancementDisplay display, Locale locale) {
        String title = display == null ? "" : flatten(display.title(), locale);
        String description = display == null ? "" : flatten(display.description(), locale);
        return template.replace("{player}", player)
                .replace("{advancement}", key)
                .replace("{title}", title)
                .replace("{description}", description);
    }

    /** Resolve any translatable parts of {@code component} in {@code locale}, then flatten to plain text. */
    private static String flatten(Component component, Locale locale) {
        return PLAIN.serialize(GlobalTranslator.render(component, locale));
    }
}
