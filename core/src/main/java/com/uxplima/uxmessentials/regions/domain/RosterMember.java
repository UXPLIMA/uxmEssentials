package com.uxplima.uxmessentials.regions.domain;

import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One classified line of a region's roster. A member or owner entry as the members/owners editor reasons about it,
 * derived purely from the identifier string the {@code RegionService} roster reads return. WorldGuard stores a roster
 * entry as a player uuid, a legacy player name, or a {@code g:}-prefixed group; only a uuid-backed entry can be
 * removed through the uuid-keyed {@link RegionMemberChange}, so this value carries the parsed {@link #player() uuid}
 * when the identifier is one and leaves it absent for a group or an unresolved legacy name (shown read-only).
 *
 * <p>Pure Java: it names no Bukkit, Paper, Kyori, or WorldGuard type. Resolving a legacy name to a uuid is the
 * adapter's concern (it needs the offline profile cache) and is deliberately not attempted here.
 *
 * @param identifier the raw roster identifier (a uuid string, a player name, or a {@code g:group} entry)
 * @param role whether this entry sits in the member set or the owner set
 * @param player the entry's uuid when the identifier parses as one, else {@code null} (a group or legacy name)
 */
@NullMarked
public record RosterMember(
        String identifier,
        RegionMemberChange.Role role,
        @Nullable UUID player) {

    private static final String GROUP_PREFIX = "g:";

    public RosterMember {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(role, "role");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("roster identifier must not be blank");
        }
    }

    /** Classify one roster identifier for {@code role}: a uuid string becomes removable, a group or name does not. */
    public static RosterMember classify(String identifier, RegionMemberChange.Role role) {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(role, "role");
        if (identifier.startsWith(GROUP_PREFIX)) {
            return new RosterMember(identifier, role, null);
        }
        return new RosterMember(identifier, role, parseUuid(identifier));
    }

    /** Whether this entry is a {@code g:}-prefixed group rather than a player. */
    public boolean group() {
        return identifier.startsWith(GROUP_PREFIX);
    }

    /** Whether this entry can be removed through the uuid-keyed {@link RegionMemberChange} (a uuid-backed player). */
    public boolean removable() {
        return player != null;
    }

    /** The group name without its {@code g:} prefix for display; the raw identifier when this is not a group. */
    public String groupName() {
        return group() ? identifier.substring(GROUP_PREFIX.length()) : identifier;
    }

    /**
     * The {@link RegionMemberChange} that removes this entry from {@code region}.
     *
     * @throws IllegalStateException when this entry carries no uuid (a group or legacy name is not removable here)
     */
    public RegionMemberChange removalFrom(RegionRef region) {
        Objects.requireNonNull(region, "region");
        if (player == null) {
            throw new IllegalStateException("roster entry " + identifier + " has no uuid to remove");
        }
        return new RegionMemberChange(region, player, role, RegionMemberChange.Action.REMOVE);
    }

    private static @Nullable UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }
}
