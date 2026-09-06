package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * Operator-defined text macros applied to every hologram line at render, a config map of literal {@code token →
 * replacement} pairs (for example {@code ":heart:" = "<red>❤"}) so a server can keep recurring symbols and
 * snippets in one place. The replacements are plain literal substitutions in configured order, applied to a
 * line's MiniMessage source before it is deserialised, so a replacement may itself contain MiniMessage tags.
 *
 * <p>It composes into the renderer's existing line transforms rather than adding a render step: {@link #wrap}
 * layers the substitution beneath the placeholder bridge the renderer and the per-viewer override already run,
 * and returns the bridge untouched when no symbols are configured, so a default server pays nothing. Built once
 * at wire from the holograms module's scoped config.
 *
 * @param replacements the configured token → replacement pairs, in declaration order
 */
@NullMarked
public record HologramSymbols(Map<String, String> replacements) {

    public HologramSymbols {
        Objects.requireNonNull(replacements, "replacements");
        replacements = Collections.unmodifiableMap(new LinkedHashMap<>(replacements));
    }

    /** No configured symbols: {@link #wrap} is then the identity. */
    public static HologramSymbols none() {
        return new HologramSymbols(Map.of());
    }

    /** Read the {@code symbols} map of a hologram module's scoped config; a blank token is skipped. */
    public static HologramSymbols fromConfig(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        Map<String, String> map = new LinkedHashMap<>();
        for (String token : config.getKeys("symbols")) {
            if (!token.isBlank()) {
                map.put(token, config.getString("symbols." + token, ""));
            }
        }
        return new HologramSymbols(map);
    }

    /** Apply every configured replacement to {@code source}, in declaration order. */
    public String apply(String source) {
        Objects.requireNonNull(source, "source");
        String result = source;
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            result = result.replace(replacement.getKey(), replacement.getValue());
        }
        return result;
    }

    /**
     * A transform that applies these symbols and then {@code next}, so a line's macros expand before the
     * placeholder bridge runs. Returns {@code next} unchanged when no symbols are configured.
     */
    public UnaryOperator<String> wrap(UnaryOperator<String> next) {
        Objects.requireNonNull(next, "next");
        if (replacements.isEmpty()) {
            return next;
        }
        return source -> next.apply(apply(source));
    }

    /** Whether no symbols are configured. */
    public boolean isEmpty() {
        return replacements.isEmpty();
    }
}
