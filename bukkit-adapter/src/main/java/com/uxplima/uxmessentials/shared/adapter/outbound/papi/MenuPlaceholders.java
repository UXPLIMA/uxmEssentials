package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Read seam the expansion queries for the {@code menu_*} placeholders. The reverse direction of the engine's
 * inbound PlaceholderAPI bridge. Instead of resolving PlaceholderAPI tokens inside a menu, this exposes the menu
 * engine's own runtime state as a source other plugins, scoreboards and tab can read: whether the requester is
 * in a menu, which one, its page and rows, and the value of a typed argument it was opened with. Like every seam
 * here it is a plain UUID-in / value-out interface, no PlaceholderAPI type and no live {@code Player}, so the
 * resolver test can populate it with a fake.
 *
 * <p>The menu engine is always wired, so in production this seam is never absent; but the resolver still guards
 * its optionality (a test bundle built without it degrades {@code menu_is_in_menu} to "no" and every other key
 * to the dash), matching every other family.
 */
public interface MenuPlaceholders {

    /** Whether the player is currently viewing an engine menu. */
    boolean inMenu(UUID player);

    /** The spec id of the menu the player currently has open, or empty when they are in none. */
    Optional<String> openedMenu(UUID player);

    /** The id of the most-recently-opened menu in the player's history, persisting after that menu closes. */
    Optional<String> lastMenu(UUID player);

    /** The 1-based page of the menu the player currently has open, or empty when they are in none. */
    OptionalInt page(UUID player);

    /** The row count of the menu the player currently has open, or empty when they are in none. */
    OptionalInt rows(UUID player);

    /** The value of the named typed argument the current menu was opened with, or empty when absent or in no menu. */
    Optional<String> argument(UUID player, String name);
}
