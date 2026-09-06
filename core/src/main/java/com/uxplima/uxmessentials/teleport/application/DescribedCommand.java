package com.uxplima.uxmessentials.teleport.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;

/**
 * The kernel-side description of one teleport command (its literal, aliases, and help text) with no
 * Brigadier or Bukkit type. The teleport inbound adapter realises this into a real
 * {@code LiteralCommandNode} when Paper fires {@code LifecycleEvents.COMMANDS}; until then the module
 * carries only this platform-neutral shape, which is what lets {@code TeleportModule} stay pure
 * application code while still owning the full description of its command surface.
 *
 * @param literal the primary command literal, without a leading slash
 * @param aliasList additional literals the same node answers to
 * @param description the one-line help text surfaced in the command listing
 */
public record DescribedCommand(String literal, List<String> aliasList, String description) implements BrigadierCommand {

    public DescribedCommand {
        Objects.requireNonNull(literal, "literal");
        Objects.requireNonNull(aliasList, "aliasList");
        Objects.requireNonNull(description, "description");
        if (literal.isBlank()) {
            throw new IllegalArgumentException("command literal must not be blank");
        }
        aliasList = List.copyOf(aliasList);
    }

    /** A command with no aliases. */
    public static DescribedCommand of(String literal, String description) {
        return new DescribedCommand(literal, List.of(), description);
    }

    /** A command with one or more aliases. */
    public static DescribedCommand of(String literal, String description, String... aliases) {
        return new DescribedCommand(literal, List.of(aliases), description);
    }

    @Override
    public List<String> aliases() {
        return aliasList;
    }
}
