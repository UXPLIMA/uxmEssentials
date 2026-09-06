package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import com.google.common.base.Splitter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.advancement.AdvancementFrame;
import com.uxplima.uxmlib.advancement.Toast;
import com.uxplima.uxmlib.advancement.Toasts;
import org.jspecify.annotations.Nullable;

/**
 * The messaging slice of the menu action vocabulary: the ways a click (or an operator's {@code /menu} spec) can
 * put text in front of a player without a feature wiring it. Registered once at startup into the shared
 * {@link MenuBindings} alongside {@link MenuVocabulary}, so a disk-loaded spec resolves {@code message-to} /
 * {@code broadcast} / {@code title} / {@code toast} the same way a code-registered feature menu does.
 *
 * <p>The delivered text is operator content, not a code-authored string. It flows through {@link StyledText}
 * (MiniMessage) exactly as {@code MenuVocabulary}'s {@code message} action does, so no {@code MessageKey} is
 * involved (the catalog is the code path; this is the data path). The pipe grammar for {@code title}
 * ({@code title|subtitle|fadeIn|stay|fadeOut}) mirrors the npc/holograms click runner so an operator reads the
 * two systems consistently.
 *
 * <p>Every action is fail-soft: a malformed argument, an offline target, or an unparseable component becomes a
 * logged-or-silent no-op through {@link #safe}, never an exception thrown back into the click dispatch, one bad
 * effect must not abort the rest of a chain or spill a stack trace onto the tick thread.
 */
public final class MessagingActions {

    private static final long MILLIS_PER_TICK = 50L;

    private MessagingActions() {}

    /**
     * Register the messaging actions into {@code bindings}. {@code toasts} is the uxmLib advancement-toast service
     * the toast action pops through (it routes its own cleanup through the library scheduler, so the action is
     * Folia-safe); {@code log} is the operator console logger a fail-soft action warns through. Left separate from
     * {@link MenuVocabulary#registerActions} so that method's existing call-sites stay untouched, the composition
     * root calls both.
     */
    public static void register(MenuBindings bindings, Toasts toasts, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(toasts, "toasts");
        Objects.requireNonNull(log, "log");
        Consumer<MenuActionContext> whisper = safe("message-to", log, MessagingActions::whisper);
        bindings.action("message-to", whisper);
        bindings.action("whisper", whisper);
        bindings.action("broadcast", safe("broadcast", log, ctx -> Bukkit.broadcast(StyledText.render(ctx.arg()))));
        bindings.action("broadcast-json", safe("broadcast-json", log, MessagingActions::broadcastJson));
        bindings.action("broadcast-legacy", safe("broadcast-legacy", log, MessagingActions::broadcastLegacy));
        Consumer<MenuActionContext> actionBar =
                safe("action-bar", log, ctx -> ctx.player().sendActionBar(StyledText.render(ctx.arg())));
        bindings.action("action-bar", actionBar);
        bindings.action("actionbar", actionBar);
        bindings.action("title", safe("title", log, MessagingActions::title));
        bindings.action("toast", safe("toast", log, ctx -> toast(ctx, toasts)));
        bindings.action("log", safe("log", log, ctx -> log.info(ctx.arg())));
    }

    /**
     * Wrap {@code body} so any thrown {@link RuntimeException} becomes a one-line operator warning and a no-op
     * rather than escaping into the click dispatch. This is the same fail-soft contract the npc/holograms click
     * runner applies to its effect actions: a single malformed effect skips, the chain continues.
     */
    private static Consumer<MenuActionContext> safe(String action, Logger log, Consumer<MenuActionContext> body) {
        return ctx -> {
            try {
                body.accept(ctx);
            } catch (RuntimeException failure) {
                log.warn("event=menu_messaging_action_failed action={} value={}", action, ctx.arg());
            }
        };
    }

    /** Deliver {@code <player> <message…>} to the named target; an unknown/offline target is a silent no-op. */
    private static void whisper(MenuActionContext ctx) {
        ParsedWhisper parsed = ParsedWhisper.parse(ctx.arg());
        if (parsed.target().isEmpty()) {
            return;
        }
        Player target = Bukkit.getPlayerExact(parsed.target());
        if (target == null) {
            return; // the target is offline or unknown. Nothing to deliver to, but not an error
        }
        target.sendMessage(StyledText.render(parsed.message()));
    }

    /** Broadcast a raw Adventure-JSON component to the whole server; malformed json fails soft via {@link #safe}. */
    private static void broadcastJson(MenuActionContext ctx) {
        Bukkit.broadcast(GsonComponentSerializer.gson().deserialize(ctx.arg()));
    }

    /** Broadcast a legacy {@code &}-coded string to the whole server. */
    private static void broadcastLegacy(MenuActionContext ctx) {
        Bukkit.broadcast(LegacyComponentSerializer.legacyAmpersand().deserialize(ctx.arg()));
    }

    /** Show a {@code title|subtitle|fadeIn|stay|fadeOut} title to the viewer, defaulting the optional segments. */
    private static void title(MenuActionContext ctx) {
        ParsedTitle parsed = ParsedTitle.parse(ctx.arg());
        Component title = StyledText.render(parsed.title());
        Component subtitle = parsed.subtitle().isEmpty() ? Component.empty() : StyledText.render(parsed.subtitle());
        Title.Times times = Title.Times.times(
                Duration.ofMillis(parsed.fadeInTicks() * MILLIS_PER_TICK),
                Duration.ofMillis(parsed.stayTicks() * MILLIS_PER_TICK),
                Duration.ofMillis(parsed.fadeOutTicks() * MILLIS_PER_TICK));
        ctx.player().showTitle(Title.title(title, subtitle, times));
    }

    /** Pop an advancement toast; a missing title or unknown icon material is a fail-soft skip, frame defaults TASK. */
    private static void toast(MenuActionContext ctx, Toasts toasts) {
        ParsedToast parsed = ParsedToast.parse(ctx.arg());
        if (parsed.title().isBlank()) {
            return; // the title is required. Without it there is no toast to build
        }
        Material icon = Material.matchMaterial(parsed.icon());
        if (icon == null) {
            return; // unknown icon material, skip rather than throw
        }
        Toast.Builder builder = Toast.builder()
                .icon(icon)
                .title(StyledText.render(parsed.title()))
                .frame(parseFrame(parsed.frame()));
        if (!parsed.description().isBlank()) {
            builder.description(StyledText.render(parsed.description()));
        }
        toasts.show(builder.build(), ctx.player());
    }

    /** Resolve a frame name case-insensitively; blank or unknown falls back to {@link AdvancementFrame#TASK}. */
    private static AdvancementFrame parseFrame(String raw) {
        if (raw.isBlank()) {
            return AdvancementFrame.TASK;
        }
        try {
            return AdvancementFrame.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return AdvancementFrame.TASK;
        }
    }

    /**
     * A parsed {@code <player> <message…>} whisper argument: the first whitespace-delimited token is the target
     * name and everything after it is the message. A target-only argument leaves an empty message; a blank
     * argument leaves both empty (which the action treats as a no-op).
     */
    public record ParsedWhisper(String target, String message) {

        public static ParsedWhisper parse(String value) {
            String trimmed = value.strip();
            if (trimmed.isEmpty()) {
                return new ParsedWhisper("", "");
            }
            String[] parts = trimmed.split("\\s+", 2);
            return new ParsedWhisper(parts[0], parts.length > 1 ? parts[1] : "");
        }
    }

    /**
     * A parsed {@code title|subtitle|fadeIn|stay|fadeOut} title value, mirroring the npc/holograms click runner's
     * grammar. The title is always present; the subtitle and the three tick timings are optional. The timings are
     * taken as a set. Either all three trailing segments parse as whole numbers, or the whole tail is kept as the
     * subtitle and the vanilla defaults (10 fade-in, 70 stay, 20 fade-out) apply, so a partial or non-numeric tail
     * never half-applies. Only the first {@code |} splits title from the rest, and the timings are peeled off the
     * right, so a subtitle may itself contain {@code |}.
     */
    public record ParsedTitle(String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {

        private static final int DEFAULT_FADE_IN = 10;
        private static final int DEFAULT_STAY = 70;
        private static final int DEFAULT_FADE_OUT = 20;

        public static ParsedTitle parse(String value) {
            int firstBar = value.indexOf('|');
            if (firstBar < 0) {
                return new ParsedTitle(value, "", DEFAULT_FADE_IN, DEFAULT_STAY, DEFAULT_FADE_OUT);
            }
            String title = value.substring(0, firstBar);
            String rest = value.substring(firstBar + 1);
            List<String> parts = new ArrayList<>(Splitter.on('|').splitToList(rest));
            if (parts.size() >= 4) {
                Integer fadeOut = trailingInt(parts);
                Integer stay = trailingInt(parts);
                Integer fadeIn = trailingInt(parts);
                if (fadeIn != null && stay != null && fadeOut != null) {
                    return new ParsedTitle(title, String.join("|", parts), fadeIn, stay, fadeOut);
                }
            }
            return new ParsedTitle(title, rest, DEFAULT_FADE_IN, DEFAULT_STAY, DEFAULT_FADE_OUT);
        }

        /** Pop the last segment as a non-negative whole number, or leave the list and return {@code null}. */
        private static @Nullable Integer trailingInt(List<String> parts) {
            if (parts.isEmpty()) {
                return null;
            }
            try {
                int parsed = Integer.parseInt(parts.get(parts.size() - 1).strip());
                if (parsed < 0) {
                    return null;
                }
                parts.remove(parts.size() - 1);
                return parsed;
            } catch (NumberFormatException notANumber) {
                return null;
            }
        }
    }

    /**
     * A parsed {@code <icon-material>|<title>|<description>|<frame>} toast value. The icon and title are the first
     * two pipe-delimited segments; the description and frame are the optional third and fourth. Each segment is
     * trimmed and a missing one is empty. The action requires a non-blank title, resolves the icon fail-soft, and
     * defaults a blank/unknown frame to {@code TASK}.
     */
    public record ParsedToast(String icon, String title, String description, String frame) {

        public static ParsedToast parse(String value) {
            List<String> parts = Splitter.on('|').splitToList(value);
            return new ParsedToast(segment(parts, 0), segment(parts, 1), segment(parts, 2), segment(parts, 3));
        }

        /** The trimmed segment at {@code index}, or empty when the value carried fewer segments. */
        private static String segment(List<String> parts, int index) {
            return index < parts.size() ? parts.get(index).strip() : "";
        }
    }
}
