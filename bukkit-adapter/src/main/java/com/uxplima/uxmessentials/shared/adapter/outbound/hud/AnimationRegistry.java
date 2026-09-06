package com.uxplima.uxmessentials.shared.adapter.outbound.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.display.AnimationSpec;
import com.uxplima.uxmlib.hud.anim.GradientText;
import com.uxplima.uxmlib.hud.anim.ScrollingText;
import com.uxplima.uxmlib.hud.anim.TextAnimator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Expands {@code %anim_<name>%} tokens in operator-authored HUD sources (scoreboard sidebar lines, tablist
 * header/footer) to the current frame of a named animation. The catalog is built once at wiring time from the parsed
 * {@code animations { … }} block ({@link AnimationDef}s) and the registry then drives one animation frame per name on
 * the shared render tick:
 *
 * <ul>
 *   <li>{@link AnimationSpec.AnimationType#FRAMES FRAMES} are resolved purely by {@link AnimationSpec#frameAt(long)}
 *       a plain frame index from the tick, with no state to advance.</li>
 *   <li>{@link AnimationSpec.AnimationType#SCROLL SCROLL} and {@link AnimationSpec.AnimationType#GRADIENT GRADIENT} bind
 *       a stateful uxmLib {@link TextAnimator} ({@code ScrollingText}/{@code GradientText}); its frame is a
 *       {@link Component} re-serialised back to MiniMessage so it survives the later {@link HudText} parse.</li>
 * </ul>
 *
 * <h2>One global tick, captured once, read by every viewer</h2>
 *
 * The render loop hops to each player's region/entity thread to render them, but the animation clock is global: the
 * render task calls {@link #advance()} exactly once per loop tick (on the loop thread) to step the stateful animators to
 * the tick's absolute frame, then every per-player {@link #resolve(String, long)} merely <em>reads</em> the current
 * frame for the captured {@link #tick()}. So no matter how many players render on a given tick, each animator advances
 * at most once and every viewer sees the same frame. The uxmLib animators publish their frame through a {@code volatile}
 * field for exactly this advance-on-one-thread / read-on-many pattern, so the resolve reads need no lock.
 *
 * <p>An unknown {@code %anim_x%} token is deliberately left in place rather than blanked, so an operator's typo is
 * visible on the board instead of silently vanishing.
 */
@NullMarked
public final class AnimationRegistry {

    /** The token prefix and suffix: {@code %anim_<name>%}. */
    private static final String PREFIX = "%anim_";

    private static final char SUFFIX = '%';

    private final MiniMessage miniMessage;
    private final AtomicReference<Map<String, Bound>> animations;
    private final AtomicLong tick = new AtomicLong(0L);

    public AnimationRegistry(List<AnimationDef> defs) {
        this(defs, MiniMessage.miniMessage());
    }

    AnimationRegistry(List<AnimationDef> defs, MiniMessage miniMessage) {
        Objects.requireNonNull(defs, "defs");
        this.miniMessage = Objects.requireNonNull(miniMessage, "miniMessage");
        this.animations = new AtomicReference<>(bindAll(defs));
    }

    /** True when nothing is configured: no token will ever expand. */
    public boolean isEmpty() {
        return snapshot().isEmpty();
    }

    /** The current global animation tick, the value the renderer passes to {@link #resolve(String, long)}. */
    public long tick() {
        return tick.get();
    }

    /**
     * Step the global animation clock one tick forward and advance every stateful animator to the new tick's absolute
     * frame. Called once per render-loop tick on the loop thread, before the per-player fan-out. FRAMES animations carry
     * no state and are resolved purely from the tick, so only SCROLL/GRADIENT are stepped here.
     */
    public void advance() {
        long now = tick.incrementAndGet();
        for (Bound bound : snapshot().values()) {
            bound.advanceTo(now);
        }
    }

    /**
     * Replace every {@code %anim_<name>%} token in {@code source} with that animation's frame at {@code tick}. A token
     * naming an animation the catalog does not hold is left untouched (a visible typo). Cheap when {@code source} holds
     * no token (the common case): the scan returns the source unchanged.
     */
    public String resolve(String source, long tick) {
        Objects.requireNonNull(source, "source");
        int start = source.indexOf(PREFIX);
        if (start < 0) {
            return source;
        }
        StringBuilder out = new StringBuilder(source.length());
        Map<String, Bound> current = snapshot();
        int cursor = 0;
        while (start >= 0) {
            int close = source.indexOf(SUFFIX, start + PREFIX.length());
            if (close < 0) {
                break;
            }
            String name = source.substring(start + PREFIX.length(), close);
            Bound bound = current.get(name);
            out.append(source, cursor, start);
            if (bound == null) {
                // Unknown animation: leave the token verbatim so the operator sees their typo.
                out.append(source, start, close + 1);
            } else {
                out.append(bound.frame(tick));
            }
            cursor = close + 1;
            start = source.indexOf(PREFIX, cursor);
        }
        out.append(source, cursor, source.length());
        return out.toString();
    }

    /** Atomically replace the complete animation catalog and restart its global clock. */
    public void replace(List<AnimationDef> defs) {
        Map<String, Bound> replacement = bindAll(Objects.requireNonNull(defs, "defs"));
        tick.set(0L);
        animations.set(replacement);
    }

    private Map<String, Bound> bindAll(List<AnimationDef> defs) {
        Map<String, Bound> built = new LinkedHashMap<>();
        for (AnimationDef def : defs) {
            built.put(def.spec().name(), bind(def));
        }
        return Map.copyOf(built);
    }

    private Map<String, Bound> snapshot() {
        return Objects.requireNonNull(animations.get(), "animations");
    }

    private Bound bind(AnimationDef def) {
        AnimationSpec spec = def.spec();
        return switch (spec.type()) {
            case FRAMES -> new Bound(spec, null);
            case SCROLL -> new Bound(spec, scrolling(def));
            case GRADIENT -> new Bound(spec, gradient(def));
        };
    }

    private TextAnimator scrolling(AnimationDef def) {
        AnimationDef.Scroll params = def.scroll().orElseGet(() -> new AnimationDef.Scroll(16, " "));
        return ScrollingText.of(def.spec().frames().get(0), params.window(), params.separator());
    }

    private TextAnimator gradient(AnimationDef def) {
        AnimationDef.Gradient params =
                def.gradient().orElseThrow(() -> new IllegalStateException("gradient animation without stops"));
        List<TextColor> stops = new ArrayList<>(params.colors().size());
        for (String colour : params.colors()) {
            stops.add(colour(colour));
        }
        return GradientText.of(def.spec().frames().get(0), stops, params.steps());
    }

    private static TextColor colour(String raw) {
        String trimmed = raw.trim();
        TextColor hex = TextColor.fromHexString(trimmed.startsWith("#") ? trimmed : "#" + trimmed);
        if (hex != null) {
            return hex;
        }
        NamedTextColor named = NamedTextColor.NAMES.value(trimmed.toLowerCase(Locale.ROOT));
        return named != null ? named : NamedTextColor.WHITE;
    }

    /**
     * One bound animation: the pure spec plus, for SCROLL/GRADIENT, the stateful uxmLib animator. {@link #advanceTo}
     * steps the animator to a tick's absolute frame (driven on the loop thread only, so {@code appliedStep} needs no
     * lock); {@link #frame} reads the current frame (called per-viewer on region threads, lock-free against the
     * volatile frame the animator publishes).
     */
    private final class Bound {

        private final AnimationSpec spec;
        private final @Nullable TextAnimator animator;
        private long appliedStep;

        Bound(AnimationSpec spec, @Nullable TextAnimator animator) {
            this.spec = spec;
            this.animator = animator;
        }

        void advanceTo(long tick) {
            if (animator == null) {
                return;
            }
            long target = tick / spec.intervalTicks();
            if (target < appliedStep) {
                // Defensive: a clock that ran backwards (only on a reset) re-homes to the first frame.
                animator.reset();
                appliedStep = 0L;
            }
            while (appliedStep < target) {
                animator.advance();
                appliedStep++;
            }
        }

        String frame(long tick) {
            if (animator == null) {
                return spec.frameAt(tick);
            }
            return miniMessage.serialize(animator.frame());
        }
    }
}
