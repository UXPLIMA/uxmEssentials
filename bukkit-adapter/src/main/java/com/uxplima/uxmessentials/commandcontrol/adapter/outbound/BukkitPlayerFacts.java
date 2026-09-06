package com.uxplima.uxmessentials.commandcontrol.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.commandcontrol.domain.PlayerFacts;
import org.jspecify.annotations.NullMarked;

/**
 * The adapter's {@link PlayerFacts} over a live {@code Player}, shared by the gate, the tab-completion
 * filter and the published check so all three answer from the same facts: permission checks go straight to
 * {@code player.hasPermission}, and the primary group is resolved lazily through the {@link PlayerGroupSource} and
 * memoised. The lazy read matters. The {@code RuleSet} checks the {@code .bypass} node before it ever asks for the
 * group, so a bypass holder never triggers a permission-plugin lookup, and a repeated group read within one command
 * is served from the cached value.
 */
@NullMarked
public final class BukkitPlayerFacts implements PlayerFacts {

    private final Player player;
    private final PlayerGroupSource groups;
    private boolean resolved;
    private Optional<String> resolvedGroup = Optional.empty();

    public BukkitPlayerFacts(Player player, PlayerGroupSource groups) {
        this.player = Objects.requireNonNull(player, "player");
        this.groups = Objects.requireNonNull(groups, "groups");
    }

    @Override
    public Optional<String> group() {
        if (!resolved) {
            resolvedGroup = groups.groupOf(player.getUniqueId());
            resolved = true;
        }
        return resolvedGroup;
    }

    @Override
    public boolean hasPermission(String node) {
        Objects.requireNonNull(node, "node");
        return player.hasPermission(node);
    }
}
