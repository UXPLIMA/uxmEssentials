package com.uxplima.uxmessentials.regions.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * A requested change to a region's roster. The domain value the (Phase 3) members/owners editor builds and hands
 * to {@code RegionService.applyMemberChange}. It names the region, the player by uuid, whether the change touches
 * the member or the owner set, and whether the player is being added or removed, so the single mutation method
 * covers all four of add-member / remove-member / add-owner / remove-owner without four overloads.
 *
 * <p>Phase 1 declares this VO and the port method it feeds so the seam is stable; the editor that produces it lands
 * in Phase 3. Pure Java: no Bukkit, Paper, Kyori, or WorldGuard.
 *
 * @param region the region whose roster changes
 * @param player the player being added or removed, by persistent uuid
 * @param role whether the change touches the member set or the owner set
 * @param action whether the player is added or removed
 */
public record RegionMemberChange(RegionRef region, UUID player, Role role, Action action) {

    public RegionMemberChange {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(action, "action");
    }

    /** Which roster the change applies to: a plain member or a full owner. */
    public enum Role {
        MEMBER,
        OWNER
    }

    /** Whether the player is joining or leaving the roster. */
    public enum Action {
        ADD,
        REMOVE
    }
}
