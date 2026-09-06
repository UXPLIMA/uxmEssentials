package com.uxplima.uxmessentials.homes.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The homes context's command surface (docs/10-feature-modules.md §15.1) as platform-neutral
 * {@link CommandSpec}s. There is a single command literal, {@code /home}, gated by
 * {@code uxmessentials.home.use}. Everything a player does with homes hangs off it: the no-arg invocation
 * opens the slot grid, while {@code visit}, {@code invite}, {@code uninvite} and the {@code admin} subtree
 * are Brigadier subcommands the inbound adapter gates with their own permission nodes
 * ({@code uxmessentials.home.visit}, {@code .invite}, {@code .admin}) via {@code .requires(...)}. Those are
 * not separate command literals, so they are not in this table: only the top-level {@code home} literal is.
 * Collected here so {@code HomesModule} stays small and the command/permission pairing is one greppable
 * table the permissions guard checks against {@code paper-plugin.yml}.
 */
final class HomeCommandSurface {

    private HomeCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(spec("home", "uxmessentials.home.use", HomeCommand.of("home", "Open and manage your homes")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    /** The kernel-side description of one home command, literal and help text, no Brigadier type. */
    private record HomeCommand(String literal, String description) implements BrigadierCommand {

        static HomeCommand of(String literal, String description) {
            return new HomeCommand(literal, description);
        }
    }
}
