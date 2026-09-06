package com.uxplima.uxmessentials.itemworld.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The powertool bind model: a held-item id mapped to the command(s) {@code /powertool <command>} runs when
 * the item is used. A binding is keyed by the item's normalised {@code namespace:path} id (the same shape as
 * {@link ItemQuery#asKey()}) and carries one or more command lines, each <em>without</em> a leading slash.
 *
 * <p>This is the only stateful corner of the otherwise-stateless itemworld surface, and it is intentionally
 * transient: the adapter stores a player's bindings as a PDC stamp on the held item (or a per-player map), so
 * the module holds no DB state. The domain owns the binding shape and the empty-binding clear semantics:
 * {@code /powertool} with no command on a bound item clears the binding.
 *
 * @param itemKey the normalised {@code namespace:path} id of the bound item
 * @param commands the command lines to run, each without a leading slash; empty clears the binding
 */
public record PowertoolBinding(String itemKey, List<String> commands) {

    public PowertoolBinding {
        Objects.requireNonNull(itemKey, "itemKey");
        Objects.requireNonNull(commands, "commands");
        if (itemKey.isBlank()) {
            throw new IllegalArgumentException("powertool item key must not be blank");
        }
        commands = List.copyOf(commands);
        for (String command : commands) {
            if (command.isBlank()) {
                throw new IllegalArgumentException("powertool command line must not be blank");
            }
        }
    }

    /** A binding that runs a single command line on use. */
    public static PowertoolBinding single(String itemKey, String command) {
        Objects.requireNonNull(command, "command");
        String normalised = command.startsWith("/") ? command.substring(1) : command;
        return new PowertoolBinding(itemKey, List.of(normalised));
    }

    /** An empty binding for {@code itemKey}: applying it clears whatever was bound. */
    public static PowertoolBinding cleared(String itemKey) {
        return new PowertoolBinding(itemKey, List.of());
    }

    /** True when this binding carries no command and therefore clears the item's binding. */
    public boolean isClear() {
        return commands.isEmpty();
    }

    /** The single command line when exactly one is bound, for the common one-command case. */
    public Optional<String> firstCommand() {
        return commands.isEmpty() ? Optional.empty() : Optional.of(commands.get(0));
    }
}
