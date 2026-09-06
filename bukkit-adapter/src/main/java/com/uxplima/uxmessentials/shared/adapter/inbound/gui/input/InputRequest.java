package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One text-input point: a stable {@code key} the operator config keys its per-point mode override off, the
 * {@code label} catalog line shown as the prompt (anvil hint or chat message) with its {@code placeholders}, and an
 * optional {@code initialText} the anvil backend pre-fills the rename field with (the chat backend cannot pre-fill, so
 * it ignores it).
 *
 * <p>The key is the contract between code and config: it is the dotted token an operator writes under
 * {@code input.modes} to flip this point between anvil and chat (e.g. {@code home.rename}, {@code loan.amount}).
 * It is descriptive and stable: renaming it is a config-breaking change, so it reads like the action it captures.
 *
 * @param key the stable input-point identifier the per-key mode override is looked up by
 * @param label the prompt catalog line, resolved in the viewer's locale
 * @param placeholders the {@code {name}} substitutions for the label (e.g. the currency, the entry being edited)
 * @param initialText the anvil pre-fill, or {@code null} for an empty field
 */
@NullMarked
public record InputRequest(
        String key,
        MessageKey label,
        Map<String, String> placeholders,
        @Nullable String initialText) {

    public InputRequest {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        placeholders = Map.copyOf(placeholders);
        if (key.isBlank()) {
            throw new IllegalArgumentException("input-point key must not be blank");
        }
    }

    /** A request with no placeholders and no anvil pre-fill: the field starts empty. */
    public static InputRequest of(String key, MessageKey label) {
        return new InputRequest(key, label, Map.of(), null);
    }

    /** A request whose label carries {@code placeholders} but no anvil pre-fill. */
    public static InputRequest of(String key, MessageKey label, Map<String, String> placeholders) {
        return new InputRequest(key, label, placeholders, null);
    }

    /** A request whose anvil field starts with {@code initialText} (the chat backend ignores it). */
    public static InputRequest withInitial(String key, MessageKey label, String initialText) {
        return new InputRequest(key, label, Map.of(), Objects.requireNonNull(initialText, "initialText"));
    }

    /** The anvil pre-fill text, if any. */
    public Optional<String> initial() {
        return Optional.ofNullable(initialText);
    }
}
