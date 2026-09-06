package com.uxplima.uxmessentials.commandcontrol.adapter.outbound;

import java.util.Objects;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;

import org.jspecify.annotations.NullMarked;

/**
 * Resolves whether a player may see a command purely by its vanilla permission, so the tab-completion scrub honours the
 * permission a command already carries in addition to the whitelist / blacklist rule set. The server already omits
 * commands a player lacks permission for from the {@code PlayerCommandSendEvent} list, so this is a belt-and-braces
 * cross-check: it reads the command map on the tick thread (where the event fires) and never mutates it.
 *
 * <p>An unknown label (one with no registered command, e.g. a namespaced alias the map does not resolve) is treated
 * as visible ({@code true}) so the rule set and the plugin-hide policy remain the sole authority over such entries.
 */
@NullMarked
public interface CommandPermissionView {

    /** True when {@code player} may see the command registered under {@code label}, or the label is unknown. */
    boolean canSee(Player player, String label);

    /** A view that reveals every command: the fallback when no command map is consulted (and used in tests). */
    static CommandPermissionView allowingAll() {
        return (player, label) -> true;
    }

    /** A view backed by the server's live {@link CommandMap}: a label is visible when its command permits the player. */
    static CommandPermissionView backedBy(CommandMap commandMap) {
        Objects.requireNonNull(commandMap, "commandMap");
        return (player, label) -> {
            Command command = commandMap.getCommand(label);
            return command == null || command.testPermissionSilent(player);
        };
    }
}
