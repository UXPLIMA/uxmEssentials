package com.uxplima.uxmessentials.communication.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The small, ordered token map an operator template is rendered against, {@code {player}}, {@code {count}},
 * {@code {world}}, and the handful of other tokens a channel offers. The substitution contract is the pure half
 * of rendering a template: it replaces every {@code {token}} it knows with the bound value and leaves an unknown
 * {@code {token}} untouched, so an operator typo surfaces verbatim rather than silently vanishing. MiniMessage
 * parsing happens later in the adapter, on the already-substituted string. Placeholders are resolved first so a
 * value that itself contains a tag-like sequence is not reinterpreted as markup.
 *
 * <p>The map is copied defensively and the order is preserved only for deterministic iteration in tests; it
 * carries no meaning. Values are operator/runtime content (a player name, a count), never plugin {@code
 * MessageKey} strings, so nothing here is parity-checked.
 */
public final class PlaceholderBindings {

    private final Map<String, String> tokens;

    private PlaceholderBindings(Map<String, String> tokens) {
        this.tokens = new LinkedHashMap<>(Objects.requireNonNull(tokens, "tokens"));
    }

    /** Empty bindings: every {@code {token}} is left as written. */
    public static PlaceholderBindings empty() {
        return new PlaceholderBindings(Map.of());
    }

    /** Bindings over a copy of {@code tokens}; later changes to the argument do not leak in. */
    public static PlaceholderBindings of(Map<String, String> tokens) {
        return new PlaceholderBindings(tokens);
    }

    /** This binding plus {@code token -> value}; returns a new instance, leaving this one untouched. */
    public PlaceholderBindings with(String token, String value) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(value, "value");
        Map<String, String> next = new LinkedHashMap<>(tokens);
        next.put(token, value);
        return new PlaceholderBindings(next);
    }

    /**
     * {@code template} with every known {@code {token}} replaced by its bound value. An unknown {@code {token}}
     * is left exactly as written; substitution is a single left-to-right pass, so a bound value that happens to
     * contain another token's text is never re-substituted.
     */
    public String apply(String template) {
        Objects.requireNonNull(template, "template");
        String result = template;
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /** A read-only view of the bound tokens, for inspection and tests. */
    public Map<String, String> asMap() {
        return Map.copyOf(tokens);
    }
}
