package com.uxplima.uxmessentials.communication.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;

/**
 * A speaker's chat-relevant permission meta, captured for one public chat line: the prefix and suffix strings the
 * format's {@code <prefix>}/{@code <suffix>} placeholders render, and the primary permission group the
 * {@code ChatFormatPolicy} selects a per-group format by. Every field is a plain value safe to read off the async
 * chat thread once captured. A prefix/suffix that the permission plugin does not set is an empty string (never
 * null); the primary group is absent when no permission plugin exposes one.
 *
 * @param prefix the speaker's chat prefix, empty when unset
 * @param suffix the speaker's chat suffix, empty when unset
 * @param primaryGroup the speaker's primary permission group, empty when none is exposed
 */
@NullMarked
public record ChatMeta(String prefix, String suffix, Optional<String> primaryGroup) {

    private static final ChatMeta EMPTY = new ChatMeta("", "", Optional.empty());

    public ChatMeta {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(suffix, "suffix");
        Objects.requireNonNull(primaryGroup, "primaryGroup");
    }

    /** Empty meta (no prefix, no suffix, no group) used when no permission plugin backs the source. */
    public static ChatMeta empty() {
        return EMPTY;
    }
}
