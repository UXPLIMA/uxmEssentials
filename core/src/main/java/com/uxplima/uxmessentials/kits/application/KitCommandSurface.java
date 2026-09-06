package com.uxplima.uxmessentials.kits.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The kits context's command surface (docs/10-feature-modules.md §15.5) as platform-neutral
 * {@link CommandSpec}s: each pairs a literal with its base permission node and a factory that produces the
 * kernel-side {@link BrigadierCommand} description. The kits inbound adapter realises the Brigadier node from
 * the started module's context on the next {@code COMMANDS} fire. Collected here so {@code KitsModule} stays
 * small and the command/permission pairing is one greppable table the permissions guard checks against
 * {@code paper-plugin.yml}.
 *
 * <p>There is a single command literal, {@code /kit}, gated by {@code uxmessentials.kit.use}. Everything a
 * player or operator does with kits hangs off it: {@code <name>} claims a kit, while {@code list},
 * {@code show}, {@code create}, {@code del}, {@code editor} and {@code reset} are Brigadier subcommands the
 * inbound adapter gates with their own permission nodes ({@code uxmessentials.kit.preview},
 * {@code .edit}, {@code .reset}) via {@code .requires(...)}. Those are not separate command literals, so they
 * are not in this table: only the top-level {@code kit} literal is.
 *
 * <p>The per-kit permission node {@code uxmessentials.kit.<id>} is not a command base permission either: it
 * is the data-driven gate the claim/list use cases check per kit, so it is intentionally absent from this
 * table; only the base {@code uxmessentials.kit.use} node guards the {@code /kit} command itself.
 */
final class KitCommandSurface {

    private KitCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(spec("kit", "uxmessentials.kit.use", KitCommand.of("kit", "Claim a kit")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    /** The kernel-side description of one kit command, literal and help text, no Brigadier type. */
    private record KitCommand(String literal, String description) implements BrigadierCommand {

        static KitCommand of(String literal, String description) {
            return new KitCommand(literal, description);
        }
    }
}
