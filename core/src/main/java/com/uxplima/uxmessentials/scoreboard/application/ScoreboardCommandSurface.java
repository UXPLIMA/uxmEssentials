package com.uxplima.uxmessentials.scoreboard.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The scoreboard context's command surface as a single platform-neutral {@link CommandSpec}: {@code /scoreboard}
 * (alias {@code /sb}), guarded by {@code uxmessentials.scoreboard.use}, which toggles whether the invoking player
 * sees the display. The literal and node are greppable and fixed so the surface guard checks them against
 * {@code paper-plugin.yml}; the realised Brigadier handler is built in the adapter.
 */
final class ScoreboardCommandSurface {

    private static final String USE_PERMISSION = "uxmessentials.scoreboard.use";

    private ScoreboardCommandSurface() {}

    /** The whole surface: the single {@code /scoreboard} toggle command. */
    static List<CommandSpec> all() {
        return List.of(spec(
                "scoreboard", USE_PERMISSION, command("scoreboard", "Toggle whether you see the scoreboard display")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    private static BrigadierCommand command(String literal, String description) {
        return new ScoreboardCommand(literal, description);
    }

    /** The kernel-side description of the scoreboard command, literal and help text, no Brigadier type. */
    private record ScoreboardCommand(String literal, String description) implements BrigadierCommand {}
}
