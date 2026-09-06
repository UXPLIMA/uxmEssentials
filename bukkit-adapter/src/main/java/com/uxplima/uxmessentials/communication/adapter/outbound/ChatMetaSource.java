package com.uxplima.uxmessentials.communication.adapter.outbound;

import java.util.UUID;

import org.jspecify.annotations.NullMarked;

/**
 * Reads a speaker's {@link ChatMeta} (prefix, suffix, and primary group) for the public chat renderer. This is
 * the seam that keeps a permission plugin a soft dependency: the default {@link #empty()} source reports no meta,
 * so a server without LuckPerms renders the format with empty affixes and the default (non-group) format, and
 * {@code LuckPermsChatMetaSource} binds only when LuckPerms is installed (probed in the wiring).
 *
 * <p>The read happens on the async chat thread, so an implementation must only read already-cached data, never
 * block or touch unsafe Bukkit state.
 */
@NullMarked
public interface ChatMetaSource {

    /** The chat meta for {@code who}, or {@link ChatMeta#empty()} when none is available. */
    ChatMeta metaFor(UUID who);

    /** A source that never reports meta: the default when no permission plugin exposes prefix/suffix/group. */
    static ChatMetaSource empty() {
        return who -> ChatMeta.empty();
    }
}
