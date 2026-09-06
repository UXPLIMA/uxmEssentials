package com.uxplima.uxmessentials.playerwarps.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The player-warps context's command surface (docs/10-feature-modules.md §15.2) as platform-neutral
 * {@link CommandSpec}s: each pairs a literal with its base permission node and a factory that produces the
 * kernel-side {@link BrigadierCommand} description. The player-warps inbound adapter realises the Brigadier
 * node from the started module's context on the next {@code COMMANDS} fire. Collected here so
 * {@code PlayerwarpsModule} stays small and the command/permission pairing is one greppable table the
 * permissions guard checks against {@code paper-plugin.yml}.
 *
 * <p>The {@code public}/{@code private} visibility toggles and {@code del} (gated by
 * {@code uxmessentials.pwarp.delete}) are subcommands of {@code /pwarp}, not separate command literals, so they
 * are not in this table: only the three top-level command literals are.
 */
final class PlayerWarpCommandSurface {

    private PlayerWarpCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                spec("pwarp", "uxmessentials.pwarp.use", PlayerWarpCommand.of("pwarp", "Teleport to a player warp")),
                spec(
                        "setpwarp",
                        "uxmessentials.pwarp.set",
                        PlayerWarpCommand.of("setpwarp", "Create or move a player warp")),
                spec(
                        "pwarps",
                        "uxmessentials.pwarp.list",
                        PlayerWarpCommand.of("pwarps", "List your or a player's public warps")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    /** The kernel-side description of one player-warp command, literal and help text, no Brigadier type. */
    private record PlayerWarpCommand(String literal, String description) implements BrigadierCommand {

        static PlayerWarpCommand of(String literal, String description) {
            return new PlayerWarpCommand(literal, description);
        }
    }
}
