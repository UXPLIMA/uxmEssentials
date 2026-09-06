package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.List;
import java.util.Objects;

/**
 * The two action steps that break a gesture's synchronous action chain into a before/after split, because their
 * outcome arrives on a later callback rather than inline. A ref carries one of these (via {@link Ref#continuation()})
 * only when the loader parsed it from an {@code input:} or {@code confirm:} map entry; every other ref carries none
 * and dispatches inline as before.
 *
 * <ul>
 *   <li>{@link Input}, an {@code input:<key>} step: prompt the viewer for a line of text, then run the gesture's
 *       remaining refs as a continuation with the typed line exposed as {@code %input%}. On a cancel the
 *       {@code onCancel} refs run and the remaining refs are abandoned. It is only meaningful as a step in a flat
 *       action list (the case player-warps needs, {@code [input:…, action-reading-%input%]}); inside an else-ladder
 *       or a deny list it is unsupported.</li>
 *   <li>{@link Confirm}, a {@code confirm:<key>} step: open a yes/no confirmation, run {@code onYes} on accept and
 *       {@code onNo} on decline. It has no continuation of the remaining chain. Its two branches carry everything
 *       that should follow either decision.</li>
 * </ul>
 *
 * <p>The {@code prompt}/{@code defaultText}/{@code title} strings are carried verbatim (a {@code @key} or an inline
 * MiniMessage token), resolved to a component against the open context by the engine at dispatch time exactly as an
 * item name is: never resolved here.
 */
public sealed interface Continuation permits Continuation.Input, Continuation.Confirm {

    /**
     * An {@code input:} step.
     *
     * @param key the input-point key the operator's per-key anvil/chat/sign mode is looked up by
     * @param prompt the prompt label shown to the viewer, resolved to a component at dispatch time
     * @param defaultText the pre-fill for the field, resolved at dispatch time; blank means an empty field
     * @param onCancel the refs run when the viewer cancels the prompt; the remaining chain is abandoned
     */
    record Input(String key, String prompt, String defaultText, List<Ref> onCancel) implements Continuation {

        public Input {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(prompt, "prompt");
            Objects.requireNonNull(defaultText, "defaultText");
            onCancel = List.copyOf(onCancel);
        }
    }

    /**
     * A {@code confirm:} step.
     *
     * @param title the confirmation title, resolved to a component at dispatch time
     * @param onYes the refs run when the viewer accepts
     * @param onNo the refs run when the viewer declines
     */
    record Confirm(String title, List<Ref> onYes, List<Ref> onNo) implements Continuation {

        public Confirm {
            Objects.requireNonNull(title, "title");
            onYes = List.copyOf(onYes);
            onNo = List.copyOf(onNo);
        }
    }
}
