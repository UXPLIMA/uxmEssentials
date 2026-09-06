package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Reads the {@code input} config tree into an immutable {@link Parsed} snapshot: the global default mode, the
 * per-key mode overrides, and the cancel keywords. Total and forgiving. An absent file, a missing block, or an
 * unparseable mode token degrades to the shipped defaults (anvil default, no overrides, {@code cancel}/{@code iptal})
 * rather than failing the load, matching the inert-default contract every settings codec in this codebase follows.
 */
@NullMarked
final class InputContentCodec {

    private static final InputMode DEFAULT_MODE = InputMode.ANVIL;
    private static final List<String> DEFAULT_CANCEL_KEYWORDS = List.of("cancel", "iptal");

    private InputContentCodec() {}

    static Parsed read(ConfigurationNode root, Logger log) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(log, "log");
        InputMode defaultMode = readDefaultMode(root.node("default-mode"), log);
        Map<String, InputMode> overrides = readOverrides(root.node("modes"), log);
        List<String> cancelKeywords = readCancelKeywords(root.node("cancel-keywords"), log);
        return new Parsed(defaultMode, overrides, cancelKeywords);
    }

    private static InputMode readDefaultMode(ConfigurationNode node, Logger log) {
        String token = node.getString();
        if (token == null || token.isBlank()) {
            return DEFAULT_MODE;
        }
        return InputMode.parse(token).orElseGet(() -> {
            log.warn("input default-mode '" + token + "' is not anvil or chat; using " + DEFAULT_MODE);
            return DEFAULT_MODE;
        });
    }

    private static Map<String, InputMode> readOverrides(ConfigurationNode node, Logger log) {
        Map<String, InputMode> overrides = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                node.childrenMap().entrySet()) {
            String key = String.valueOf(entry.getKey());
            String token = entry.getValue().getString("");
            InputMode.parse(token)
                    .ifPresentOrElse(
                            mode -> overrides.put(key.toLowerCase(Locale.ROOT), mode),
                            () -> log.warn("input mode override for '" + key + "' is '" + token
                                    + "'; expected anvil or chat, ignoring"));
        }
        return Map.copyOf(overrides);
    }

    private static List<String> readCancelKeywords(ConfigurationNode node, Logger log) {
        if (node.virtual() || node.empty()) {
            return DEFAULT_CANCEL_KEYWORDS;
        }
        try {
            List<String> raw = node.getList(String.class, List.of());
            List<String> cleaned =
                    raw.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
            return cleaned.isEmpty() ? DEFAULT_CANCEL_KEYWORDS : cleaned;
        } catch (SerializationException malformed) {
            log.warn("input cancel-keywords is not a list of strings; using the default keywords");
            return DEFAULT_CANCEL_KEYWORDS;
        }
    }

    /**
     * An immutable read of the input config: the global {@code defaultMode}, the {@code overrides} keyed by
     * lower-cased input-point key, and the {@code cancelKeywords} both backends accept.
     */
    record Parsed(InputMode defaultMode, Map<String, InputMode> overrides, List<String> cancelKeywords) {

        Parsed {
            Objects.requireNonNull(defaultMode, "defaultMode");
            overrides = Map.copyOf(overrides);
            cancelKeywords = List.copyOf(cancelKeywords);
        }

        InputMode modeFor(String key) {
            return overrides.getOrDefault(key.toLowerCase(Locale.ROOT), defaultMode);
        }
    }
}
