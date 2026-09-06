package com.uxplima.uxmessentials.communication.application;

import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of resolving one connection channel for a player: the raw, placeholder-substituted template the
 * adapter should render (or none), plus whether the vanilla connection line must be cleared. The string is still
 * operator content (MiniMessage tags intact, placeholders already substituted) and is parsed into a Component
 * by the adapter, not here. It is never a {@code MessageKey}.
 *
 * <p>Three shapes map to the three policy modes. A {@link com.uxplima.uxmessentials.communication.domain.PolicyMode#CUSTOM}
 * channel yields {@link #render(String)}: a template present, vanilla suppressed (the plugin's line replaces it).
 * A {@link com.uxplima.uxmessentials.communication.domain.PolicyMode#DISABLE} channel yields {@link #suppress()}
 * no template, vanilla cleared (nothing shows). A {@link com.uxplima.uxmessentials.communication.domain.PolicyMode#DEFAULT}
 * channel yields {@link #keepVanilla()}: no plugin template, vanilla left untouched.
 *
 * @param template the substituted raw template to render, empty when the plugin contributes none
 * @param suppressVanilla whether the adapter must clear the vanilla connection line
 */
public record ResolvedMessage(Optional<String> template, boolean suppressVanilla) {

    public ResolvedMessage {
        Objects.requireNonNull(template, "template");
    }

    /** A plugin-rendered line: {@code template} shows and the vanilla line is replaced by it. */
    public static ResolvedMessage render(String template) {
        return new ResolvedMessage(Optional.of(Objects.requireNonNull(template, "template")), true);
    }

    /** A suppressed channel: nothing shows and the vanilla line is cleared. */
    public static ResolvedMessage suppress() {
        return new ResolvedMessage(Optional.empty(), true);
    }

    /** A deferred channel: the plugin contributes no line and the vanilla message is left as it is. */
    public static ResolvedMessage keepVanilla() {
        return new ResolvedMessage(Optional.empty(), false);
    }

    /** Whether the adapter has a plugin template to render for this channel. */
    public boolean hasTemplate() {
        return template.isPresent();
    }
}
