package com.uxplima.uxmessentials.commandcontrol.domain;

import java.util.Optional;

/**
 * The narrow, Bukkit-free view of one player the {@link RuleSet} decision reads: the player's permission group (which
 * selects the group's command list) and a permission-check function (which resolves the {@code .bypass} node and any
 * future per-rule node). The adapter supplies a concrete implementation over a live {@code Player}, a lazy group
 * lookup and {@code player.hasPermission}, so the domain stays pure and fully unit-testable with a plain fake.
 *
 * <p>The group is optional: a server with no permission plugin, or a player in no named group, reports
 * {@link Optional#empty()}, in which case the {@code default} command list applies.
 */
public interface PlayerFacts {

    /** The player's primary permission group, or {@link Optional#empty()} when none is exposed. */
    Optional<String> group();

    /** True when the player holds {@code node} (vanilla op or any permission plugin). */
    boolean hasPermission(String node);
}
