package com.uxplima.uxmessentials.staff.adapter;

import java.util.Optional;

import org.jspecify.annotations.NullMarked;

/**
 * The kinds of gadget a staff member carries on the staff-mode hotbar. Each value is tagged onto its hotbar
 * item through the staff PDC key so the right-click listener can tell which gadget was used without matching on
 * material or display name (which an operator may freely re-skin in config).
 *
 * <p>STAFF-MODE ONLY: the gadgets orchestrate the existing presence, playerstate, moderation, and teleport
 * modules. VANISH toggles vanish, EXAMINE opens an online player's inventory, FREEZE toggles a target's
 * freeze through moderation, COMPASS teleports to a target, FOLLOW keeps the staff member on a target, and
 * never carry a sanction beyond what moderation's freeze use case already audits.
 */
@NullMarked
public enum StaffGadget {

    /** Toggle the staff member's own vanish state. */
    VANISH("vanish"),

    /** Pick an online player and open their inventory. */
    EXAMINE("examine"),

    /** Right-click a player to freeze or unfreeze them through the moderation freeze use case. */
    FREEZE("freeze"),

    /** Pick (or right-click) a player and teleport to them as an admin teleport. */
    COMPASS("compass"),

    /** Right-click a player to start or stop continuously following them. */
    FOLLOW("follow");

    private final String tag;

    StaffGadget(String tag) {
        this.tag = tag;
    }

    /** The stable PDC tag value written onto the gadget item; never the localized display name. */
    public String tag() {
        return tag;
    }

    /** Resolve a gadget from its stored PDC {@code tag}, or empty when it names no known gadget. */
    public static Optional<StaffGadget> fromTag(String tag) {
        for (StaffGadget gadget : values()) {
            if (gadget.tag.equals(tag)) {
                return Optional.of(gadget);
            }
        }
        return Optional.empty();
    }
}
