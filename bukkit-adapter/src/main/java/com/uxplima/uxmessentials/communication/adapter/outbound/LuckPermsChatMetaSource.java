package com.uxplima.uxmessentials.communication.adapter.outbound;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A {@link ChatMetaSource} backed by LuckPerms. Bound only when LuckPerms is installed (the wiring probes the
 * plugin before constructing it), so the {@code net.luckperms} symbols are never loaded on a server without it.
 *
 * <p>Prefix, suffix, and primary group are read from the loaded user's cached data, the same snapshot the
 * platform resolves permission checks against, so the read is non-blocking and safe on the async chat thread. A
 * value is reported only for a currently-loaded user; an unloaded user (an edge case for an online speaker) falls
 * back to empty meta, so the renderer uses empty affixes and the default format rather than blocking to load.
 */
@NullMarked
public final class LuckPermsChatMetaSource implements ChatMetaSource {

    private final LuckPerms luckPerms;

    public LuckPermsChatMetaSource(LuckPerms luckPerms) {
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
    }

    @Override
    public ChatMeta metaFor(UUID who) {
        Objects.requireNonNull(who, "who");
        User user = luckPerms.getUserManager().getUser(who);
        if (user == null) {
            return ChatMeta.empty();
        }
        CachedMetaData meta = user.getCachedData().getMetaData();
        return new ChatMeta(orEmpty(meta.getPrefix()), orEmpty(meta.getSuffix()), group(meta.getPrimaryGroup()));
    }

    private static String orEmpty(@Nullable String affix) {
        return affix == null ? "" : affix;
    }

    private static Optional<String> group(@Nullable String primaryGroup) {
        return primaryGroup == null || primaryGroup.isBlank() ? Optional.empty() : Optional.of(primaryGroup);
    }
}
