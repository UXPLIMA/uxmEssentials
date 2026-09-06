package com.uxplima.uxmessentials.communication.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The operator's policy for one connection channel, join, quit, or death. It pairs a {@link PolicyMode} with
 * the list of templates to draw from and the {@link Ordering} that selects among them. The policy is pure: it
 * holds operator content (the templates) and the selection rule, and answers "which raw template, if any,
 * applies to this index?". It neither substitutes placeholders nor parses MiniMessage, both of which happen
 * later in the rendering use case and the adapter.
 *
 * <p>Selection is index-driven so the caller owns the rotation state (an {@code AnnouncerCursor}-style counter
 * for sequential channels, or a freshly drawn random index): a {@link Ordering#SEQUENTIAL} policy wraps the
 * index modulo the template count, a {@link Ordering#RANDOM} policy trusts the caller's already-randomised
 * index. {@link PolicyMode#DISABLE} and {@link PolicyMode#DEFAULT} carry no template: {@link #select} returns
 * empty for both, and the use case distinguishes them through {@link #mode()} to decide whether to also clear
 * the vanilla line.
 *
 * @param mode whether the plugin disables, defers to vanilla, or renders a custom template
 * @param ordering how {@link #select} walks the template list when the mode is {@link PolicyMode#CUSTOM}
 * @param templates the operator-authored raw templates (MiniMessage with {@code {token}} placeholders)
 */
public record MessagePolicy(PolicyMode mode, Ordering ordering, List<String> templates) {

    public MessagePolicy {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(ordering, "ordering");
        templates = List.copyOf(Objects.requireNonNull(templates, "templates"));
        if (mode == PolicyMode.CUSTOM && templates.isEmpty()) {
            throw new IllegalArgumentException("a CUSTOM policy must declare at least one template");
        }
    }

    /** A channel the plugin leaves to the vanilla server message. */
    public static MessagePolicy vanilla() {
        return new MessagePolicy(PolicyMode.DEFAULT, Ordering.SEQUENTIAL, List.of());
    }

    /** A channel the plugin suppresses entirely (and asks the adapter to clear the vanilla line). */
    public static MessagePolicy disabled() {
        return new MessagePolicy(PolicyMode.DISABLE, Ordering.SEQUENTIAL, List.of());
    }

    /** A custom channel rendering one of {@code templates}, selected per {@code ordering}. */
    public static MessagePolicy custom(Ordering ordering, List<String> templates) {
        return new MessagePolicy(PolicyMode.CUSTOM, ordering, templates);
    }

    /** Whether this channel produces a plugin-rendered template at all (only {@link PolicyMode#CUSTOM} does). */
    public boolean rendersTemplate() {
        return mode == PolicyMode.CUSTOM;
    }

    /** Whether the channel asks the adapter to clear the vanilla connection line (only {@link PolicyMode#DISABLE}). */
    public boolean suppressesVanilla() {
        return mode == PolicyMode.DISABLE;
    }

    /** The number of templates available to select from; zero unless the mode is {@link PolicyMode#CUSTOM}. */
    public int templateCount() {
        return templates.size();
    }

    /**
     * The raw template at {@code selectionIndex}, or empty when the channel renders no template
     * ({@link PolicyMode#DISABLE} / {@link PolicyMode#DEFAULT}). For a {@link Ordering#SEQUENTIAL} policy the
     * index wraps modulo the template count, so a monotonically increasing counter rotates through the list; for
     * {@link Ordering#RANDOM} the caller is expected to pass an already-randomised in-range index, which is used
     * as-is. A negative index is rejected.
     */
    public Optional<String> select(int selectionIndex) {
        if (!rendersTemplate()) {
            return Optional.empty();
        }
        if (selectionIndex < 0) {
            throw new IllegalArgumentException("selection index must not be negative: " + selectionIndex);
        }
        int bounded = ordering == Ordering.SEQUENTIAL
                ? selectionIndex % templates.size()
                : Math.min(selectionIndex, templates.size() - 1);
        return Optional.of(templates.get(bounded));
    }
}
