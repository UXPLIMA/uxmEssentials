package com.uxplima.uxmessentials.commandcontrol.adapter.outbound;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A {@link PlayerGroupSource} backed by LuckPerms. Bound only when LuckPerms is installed (the wiring probes the
 * plugin before constructing it), so the {@code net.luckperms} symbols are never loaded on a server without it
 * mirroring {@code LuckPermsChatMetaSource} in the chat renderer.
 *
 * <p>The primary group is read from the loaded user's cached data. The same snapshot the platform resolves
 * permission checks against, so the read is non-blocking and safe on the tick thread inside the preprocess gate. A
 * group is reported only for a currently-loaded user; an unloaded user (an edge case for an online player) falls back
 * to empty, so the gate uses the {@code default} command list rather than blocking to load.
 */
@NullMarked
public final class LuckPermsPlayerGroupSource implements PlayerGroupSource {

    private final LuckPerms luckPerms;

    public LuckPermsPlayerGroupSource(LuckPerms luckPerms) {
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
    }

    @Override
    public Optional<String> groupOf(UUID who) {
        Objects.requireNonNull(who, "who");
        User user = luckPerms.getUserManager().getUser(who);
        if (user == null) {
            return Optional.empty();
        }
        return group(user.getCachedData().getMetaData().getPrimaryGroup());
    }

    private static Optional<String> group(@Nullable String primaryGroup) {
        return primaryGroup == null || primaryGroup.isBlank() ? Optional.empty() : Optional.of(primaryGroup);
    }
}
