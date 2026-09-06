package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import com.google.common.base.Splitter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRegistryKeys;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * The sound slice of the menu action vocabulary: the two ways a click (or an operator's {@code /menu} spec) can
 * play a sound beyond the viewer's own single {@code sound} effect, {@code broadcast-sound}, which every online
 * player hears at their own location, and {@code rawsound}, which plays a verbatim namespaced key for a
 * resource-pack custom sound. Registered once at startup into the shared {@link MenuBindings} alongside
 * {@link MenuVocabulary}, so a disk-loaded spec resolves them the same way a code-registered feature menu does.
 *
 * <p>A sound key is operator content (a key and two optional numbers from the spec, not a code-authored string)
 * so no {@code MessageKey} is involved; the only text either action produces is a diagnostic line on the operator
 * {@code log}. Both actions share the {@code <key> [volume] [pitch]} grammar of {@link SoundArg}, which is the one
 * parser {@link MenuVocabulary}'s in-place {@code sound} action reads too, so the three sound effects speak the
 * same argument shape.
 *
 * <p>{@code broadcast-sound} resolves its key through the vanilla registry the same way {@code sound} does, so
 * either the dotted key or the UPPER_SNAKE constant names the same sound. {@code rawsound} does the opposite on purpose: it passes the key through untouched to the
 * Adventure sound API so a resource-pack key (say {@code myserver:custom.ding}) or a precise vanilla key reaches
 * the client exactly as written. Every handler is wrapped in {@link #safe}, so a malformed key, an Adventure
 * {@code Key} the client would reject throws while being built. Becomes a logged no-op rather than an exception
 * escaping into the click dispatch. A blank key is a silent no-op in either action.
 */
public final class SoundActions {

    private SoundActions() {}

    /**
     * Register the sound actions into {@code bindings}. {@code log} is the operator console logger a fail-soft or
     * skipped action warns through. Left separate from {@link MenuVocabulary#registerActions} so that method's
     * existing call-sites stay untouched: the composition root calls both.
     */
    public static void register(MenuBindings bindings, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(log, "log");
        Consumer<MenuActionContext> broadcast = safe("broadcast-sound", log, SoundActions::broadcastSound);
        bindings.action("broadcast-sound", broadcast);
        bindings.action("soundall", broadcast);
        Consumer<MenuActionContext> raw = safe("rawsound", log, SoundActions::rawSound);
        bindings.action("rawsound", raw);
        bindings.action("raw-sound", raw);
    }

    /**
     * Wrap {@code body} so any thrown {@link RuntimeException} becomes a one-line operator warning and a no-op
     * rather than escaping into the click dispatch. The same fail-soft contract {@link MovementActions} and
     * {@link MessagingActions} apply to their effect actions. This is what turns an invalid namespaced key (the
     * Adventure {@code Key} builder throws) into a logged skip instead of a stack trace on the tick thread.
     */
    private static Consumer<MenuActionContext> safe(String action, Logger log, Consumer<MenuActionContext> body) {
        return ctx -> {
            try {
                body.accept(ctx);
            } catch (RuntimeException failure) {
                log.warn("event=menu_sound_action_failed action={} value={}", action, ctx.arg());
            }
        };
    }

    /**
     * Play {@code <key> [volume] [pitch]} to every online player at their own location, so each hears it locally
     * rather than at the clicker's position. The key is resolved through the vanilla registry the same way the
     * viewer-only {@code sound} action does, so both name forms work; a blank key is a silent no-op.
     */
    private static void broadcastSound(MenuActionContext ctx) {
        SoundArg sound = SoundArg.parse(ctx.arg());
        if (sound.key().isBlank()) {
            return;
        }
        String key = BukkitRegistryKeys.soundKeyName(sound.key());
        for (Player online : Bukkit.getOnlinePlayers()) {
            var at = Objects.requireNonNull(online.getLocation(), "player location");
            online.playSound(at, key, sound.volume(), sound.pitch());
        }
    }

    /**
     * Play {@code <namespaced-key> [volume] [pitch]} to the viewer verbatim through the Adventure sound API, no
     * lowercasing, no vanilla-registry lookup, so a resource-pack custom sound or a precise vanilla key is sent to
     * the client exactly as written. A blank key is a silent no-op; an invalid key throws while the Adventure
     * {@link Key} is built and is caught by {@link #safe} as a fail-soft skip.
     */
    private static void rawSound(MenuActionContext ctx) {
        SoundArg sound = SoundArg.parse(ctx.arg());
        if (sound.key().isBlank()) {
            return;
        }
        Sound played = Sound.sound(Key.key(sound.key()), Sound.Source.MASTER, sound.volume(), sound.pitch());
        ctx.player().playSound(played);
    }

    /**
     * A parsed {@code <key> [volume] [pitch]} sound argument shared by all three sound actions. The key is the
     * first whitespace-delimited token; the volume and pitch are the optional second and third tokens, each a
     * float defaulting to {@code 1f}. A malformed number falls back to that default rather than throwing, so a
     * typo in an operator spec plays at the default gain instead of aborting the effect. A blank value yields a
     * blank key, which every caller treats as a no-op. Parsing is Bukkit-free so a plain-JUnit grammar test can
     * exercise it; the caller decides whether to lowercase the key ({@code sound}/{@code broadcast-sound} do,
     * {@code rawsound} does not).
     */
    public record SoundArg(String key, float volume, float pitch) {

        private static final float DEFAULT = 1f;

        public SoundArg {
            Objects.requireNonNull(key, "key");
        }

        public static SoundArg parse(String value) {
            Objects.requireNonNull(value, "value");
            // omitEmptyStrings collapses runs of whitespace and drops the leading/trailing empties, so a blank
            // value yields no tokens (a blank key) and token 0 is always a real key when present.
            List<String> tokens = Splitter.onPattern("\\s+").omitEmptyStrings().splitToList(value.strip());
            if (tokens.isEmpty()) {
                return new SoundArg("", DEFAULT, DEFAULT);
            }
            float volume = tokens.size() > 1 ? floatOrDefault(tokens.get(1)) : DEFAULT;
            float pitch = tokens.size() > 2 ? floatOrDefault(tokens.get(2)) : DEFAULT;
            return new SoundArg(tokens.get(0), volume, pitch);
        }

        /** The token as a float, or the {@code 1f} default when it is blank or not a number. */
        private static float floatOrDefault(String token) {
            try {
                return Float.parseFloat(token.strip());
            } catch (NumberFormatException notANumber) {
                return DEFAULT;
            }
        }
    }
}
