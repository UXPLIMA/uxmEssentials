package com.uxplima.uxmessentials.shared.display;

import java.util.List;
import java.util.Objects;

/**
 * A pure description of a named animation a display element can reference: a name, a {@link AnimationType type},
 * the source {@code frames}, and how many server ticks each step lasts. Animations are declared once in operator
 * config and referenced by name from scoreboard/tablist/nametag lines, so the same catalog drives every module.
 *
 * <p>This is the <em>spec</em> only: it carries no rendering library. The {@link AnimationType#FRAMES} kind is
 * fully resolved here by {@link #frameAt(long)} (plain frame cycling, which is pure tick arithmetic). The
 * {@link AnimationType#SCROLL} and {@link AnimationType#GRADIENT} kinds are <strong>adapter-bound</strong>: this
 * class models their declaration and exposes the raw source string, but the actual scrolling/gradient effect is
 * produced in the bukkit adapter by binding uxmLib's {@code TextAnimator}/{@code ScrollingText}/{@code
 * GradientText} (which depend on Adventure and so cannot live in {@code :core}). For those kinds
 * {@link #frameAt(long)} therefore returns the first source frame unchanged; the adapter takes it from there.
 *
 * @param name the catalog key elements reference; non-blank
 * @param type how the frames are animated
 * @param frames the source frames; non-empty (the single source string for SCROLL/GRADIENT lives at index 0)
 * @param intervalTicks server ticks per animation step; at least 1
 */
public record AnimationSpec(String name, AnimationType type, List<String> frames, int intervalTicks) {

    /** The supported animation kinds. {@link #FRAMES} is core-resolved; the others are adapter-bound. */
    public enum AnimationType {
        /** Cycle through {@code frames} one per {@code intervalTicks}; fully resolved in core. */
        FRAMES,
        /** Scroll a single source string; rendering is bound to uxmLib in the adapter. */
        SCROLL,
        /** Animate a gradient over a single source string; rendering is bound to uxmLib in the adapter. */
        GRADIENT
    }

    public AnimationSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(frames, "frames");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        frames = List.copyOf(frames);
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("frames must not be empty");
        }
        if (intervalTicks < 1) {
            throw new IllegalArgumentException("intervalTicks must be >= 1: " + intervalTicks);
        }
    }

    /**
     * The source string for the given absolute server {@code tick}. For {@link AnimationType#FRAMES} this cycles
     * the frame list ({@code frames.get((tick / intervalTicks) % size)}). For {@link AnimationType#SCROLL} and
     * {@link AnimationType#GRADIENT} this returns the raw first frame. Those kinds are rendered by the adapter
     * (see the class docs), and this method only supplies their source string.
     *
     * @param tick a non-negative absolute server tick count
     * @return the source string to render at {@code tick}
     * @throws IllegalArgumentException if {@code tick} is negative
     */
    public String frameAt(long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0: " + tick);
        }
        return switch (type) {
            case FRAMES -> frames.get((int) ((tick / intervalTicks) % frames.size()));
            case SCROLL, GRADIENT -> frames.get(0);
        };
    }
}
