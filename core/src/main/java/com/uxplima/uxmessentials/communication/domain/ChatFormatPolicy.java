package com.uxplima.uxmessentials.communication.domain;

import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The operator's policy for rendering a public chat line. It pairs a global {@link #defaultFormat} with an
 * optional per-group override map (keyed by a player's primary permission group) and the two switches that
 * decide how much of a message the plugin styles: {@link #enabled} (off leaves vanilla chat untouched) and
 * {@link #allowPlayerFormat} (whether a permitted speaker may write MiniMessage in their own message). The
 * policy is pure operator content. It holds the raw format strings and answers "which format applies to this
 * group?"; it neither substitutes tokens nor parses MiniMessage, both of which happen in the adapter renderer.
 *
 * <p>The format strings are MiniMessage with the placeholders the renderer resolves, {@code <prefix>},
 * {@code <suffix>}, {@code <display_name>}, {@code <name>}, {@code <world>}, and {@code <message>}. The
 * {@link #nameHover} / {@link #nameClick} strings are the optional hover text and click target the renderer
 * decorates the name span with; blank means none.
 *
 * @param enabled whether the plugin renders public chat at all (false defers to the vanilla format)
 * @param defaultFormat the format applied to a speaker with no matching group override (never blank)
 * @param groupFormats per-group format overrides, keyed by the speaker's primary group
 * @param allowPlayerFormat whether a permitted speaker's own message is parsed as MiniMessage rather than plain
 * @param nameHover the optional MiniMessage hover shown on the name span (blank = none)
 * @param nameClick the optional click target applied to the name span (blank = none)
 */
public record ChatFormatPolicy(
        boolean enabled,
        String defaultFormat,
        Map<String, String> groupFormats,
        boolean allowPlayerFormat,
        String nameHover,
        String nameClick) {

    /** A near-vanilla default so enabling the module before any format is authored still reads like vanilla chat. */
    private static final String VANILLA_FORMAT = "<prefix><display_name><suffix><gray>:</gray> <message>";

    public ChatFormatPolicy {
        Objects.requireNonNull(defaultFormat, "defaultFormat");
        if (defaultFormat.isBlank()) {
            throw new IllegalArgumentException("defaultFormat must not be blank");
        }
        groupFormats = Map.copyOf(Objects.requireNonNull(groupFormats, "groupFormats"));
        Objects.requireNonNull(nameHover, "nameHover");
        Objects.requireNonNull(nameClick, "nameClick");
    }

    /** A disabled policy that leaves vanilla chat untouched; the fallback when no {@code chat.conf} is authored. */
    public static ChatFormatPolicy disabled() {
        return new ChatFormatPolicy(false, VANILLA_FORMAT, Map.of(), false, "", "");
    }

    /**
     * The format for a speaker whose primary group is {@code group}: the group's override when one is configured
     * (matched case-insensitively, since a permission plugin reports its groups lowercased while an operator may
     * author the key in any case), else the {@link #defaultFormat}. A {@code null} or blank group takes the
     * default.
     */
    public String formatFor(@Nullable String group) {
        if (group == null || group.isBlank()) {
            return defaultFormat;
        }
        String direct = groupFormats.get(group);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> override : groupFormats.entrySet()) {
            if (override.getKey().equalsIgnoreCase(group)) {
                return override.getValue();
            }
        }
        return defaultFormat;
    }

    /** The near-vanilla default format, exposed so the config codec can fall back to it when the key is blank. */
    public static String vanillaFormat() {
        return VANILLA_FORMAT;
    }
}
