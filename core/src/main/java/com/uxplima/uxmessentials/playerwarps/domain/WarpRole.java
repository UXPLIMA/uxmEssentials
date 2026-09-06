package com.uxplima.uxmessentials.playerwarps.domain;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * The role a non-owner player holds on a warp, in descending authority:
 *
 * <ul>
 *   <li>{@link #OWNER}: the warp's creator, with unconditional control. This constant exists so the members
 *       table can carry the owner row too when a use case wants the full roster in one place; the aggregate's
 *       own owner field remains the source of truth for ownership.
 *   <li>{@link #CO_OWNER}: a trusted delegate who may manage the warp almost as fully as the owner.
 *   <li>{@link #MANAGER}, a helper with a narrower set of management actions.
 * </ul>
 *
 * <p>The persisted token is the constant's {@link #name()} (uppercase); {@link #parse(String)} reads it back
 * case-insensitively, matching the {@link WarpAccess} / {@link WarpStatus} idiom already in the package.
 */
public enum WarpRole {
    OWNER,
    CO_OWNER,
    MANAGER;

    // The capability matrix, declared once so every management use case gates through one policy. The owner set is
    // derived (every capability) rather than listed, so a new WarpCapability is automatically owner-allowed and only
    // needs a deliberate decision for the two delegate tiers.
    private static final Set<WarpCapability> OWNER_CAPS = EnumSet.allOf(WarpCapability.class);
    private static final Set<WarpCapability> CO_OWNER_CAPS = EnumSet.complementOf(EnumSet.of(
            WarpCapability.DELETE, WarpCapability.TRANSFER, WarpCapability.MANAGE_MEMBERS, WarpCapability.SPONSOR));
    private static final Set<WarpCapability> MANAGER_CAPS =
            EnumSet.of(WarpCapability.EDIT_METADATA, WarpCapability.MANAGE_WHITELIST, WarpCapability.MANAGE_BANS);

    /**
     * Whether a holder of this role may perform {@code capability}:
     *
     * <ul>
     *   <li>{@link #OWNER}, every capability.
     *   <li>{@link #CO_OWNER}, every capability except {@link WarpCapability#DELETE},
     *       {@link WarpCapability#TRANSFER}, {@link WarpCapability#MANAGE_MEMBERS}, and
     *       {@link WarpCapability#SPONSOR} (a trusted delegate may run the warp and even withdraw its earnings, but
     *       never dispose of it, promote further delegates, or spend the owner's money on paid placement).
     *   <li>{@link #MANAGER}, only {@link WarpCapability#EDIT_METADATA}, {@link WarpCapability#MANAGE_WHITELIST},
     *       and {@link WarpCapability#MANAGE_BANS} (presentation and the guest list, never the money or the
     *       lifecycle).
     * </ul>
     */
    public boolean can(WarpCapability capability) {
        Objects.requireNonNull(capability, "capability");
        return switch (this) {
            case OWNER -> OWNER_CAPS.contains(capability);
            case CO_OWNER -> CO_OWNER_CAPS.contains(capability);
            case MANAGER -> MANAGER_CAPS.contains(capability);
        };
    }

    /**
     * Match a stored or user-supplied token to a constant, ignoring case and surrounding whitespace. Returns
     * an empty result, never throws, for {@code null}, blank, or unrecognised input.
     */
    public static Optional<WarpRole> parse(@Nullable String token) {
        if (token == null) {
            return Optional.empty();
        }
        String normalised = token.strip().toUpperCase(Locale.ROOT);
        if (normalised.isEmpty()) {
            return Optional.empty();
        }
        for (WarpRole role : values()) {
            if (role.name().equals(normalised)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
