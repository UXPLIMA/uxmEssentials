package com.uxplima.uxmessentials.warps.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The warps context's command surface (docs/10-feature-modules.md §15.2) as platform-neutral
 * {@link CommandSpec}s: each pairs a literal with its base permission node and a factory that produces the
 * kernel-side {@link BrigadierCommand} description. The warps inbound adapter realises the Brigadier node
 * from the started module's context on the next {@code COMMANDS} fire. Collected here so {@code
 * WarpsModule} stays small and the command/permission pairing is one greppable table the permissions guard
 * checks against {@code paper-plugin.yml}.
 *
 * <p>There is a single command literal, {@code /warp}, gated by {@code uxmessentials.warp.use}. Everything
 * a player does with server warps hangs off it: {@code <name>} teleports, while {@code list}, {@code set},
 * {@code del}, {@code info}, {@code move} and the per-warp {@code lock}/{@code password}/{@code rate}/
 * {@code rating}/{@code edit} actions are Brigadier subcommands the inbound adapter gates with their own
 * permission nodes ({@code uxmessentials.warp.list}, {@code .set}, {@code .delete}, {@code .info},
 * {@code .move}, …) via {@code .requires(...)}. Those are not separate command literals, so they are not in
 * this table: only the top-level {@code warp} literal is.
 *
 * <p>The per-warp permission node {@code uxmessentials.warp.use.<warp>} is not a command base permission
 * it is the data-driven gate {@code UseWarp} checks per destination, so it is intentionally absent from this
 * table; only the base {@code uxmessentials.warp.use} node guards the {@code /warp} command itself.
 */
final class WarpCommandSurface {

    private WarpCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(spec("warp", "uxmessentials.warp.use", WarpCommand.of("warp", "Teleport to a server warp")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    /** The kernel-side description of one warp command, literal and help text, no Brigadier type. */
    private record WarpCommand(String literal, String description) implements BrigadierCommand {

        static WarpCommand of(String literal, String description) {
            return new WarpCommand(literal, description);
        }
    }
}
