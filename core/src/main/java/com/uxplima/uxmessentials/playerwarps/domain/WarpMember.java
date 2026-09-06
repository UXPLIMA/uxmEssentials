package com.uxplima.uxmessentials.playerwarps.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A player granted a management {@link WarpRole} on a warp, with the instant they were added. The membership is
 * keyed on the warp id plus this {@link #player}; re-adding the same player updates the {@link #role} in place
 * rather than creating a second row (the store owns that upsert). The owner is not stored here as a rule, the
 * aggregate's owner field is the source of truth, but the {@link WarpRole#OWNER} constant lets a use case
 * assemble the full roster when it wants owner and delegates in one list.
 *
 * @param player the delegate's player uuid
 * @param role the authority granted
 * @param addedAt when the delegate was added
 */
public record WarpMember(UUID player, WarpRole role, Instant addedAt) {

    public WarpMember {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(addedAt, "addedAt");
    }
}
