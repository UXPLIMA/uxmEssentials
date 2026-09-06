package com.uxplima.uxmessentials.vote.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The vote context's command surface as platform-neutral {@link CommandSpec}s: {@code /vote} guarded by
 * {@code uxmessentials.vote.use} (shows the configured vote links; its {@code testreward} subcommand,
 * gated by {@code uxmessentials.vote.testreward}, simulates a vote for the sender) and {@code /voteparty}
 * guarded by {@code uxmessentials.voteparty.use} (shows the party progress). The literals and nodes are
 * fixed and greppable so the surface guard checks them against {@code paper-plugin.yml}; the realised
 * Brigadier handlers are built in the adapter.
 *
 * <p>The {@code testreward} action is a subcommand of {@code /vote} gated by its own node, not a separate
 * command literal, so it is not in this table: only the two top-level command literals are.
 */
final class VoteCommandSurface {

    private VoteCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                spec("vote", "uxmessentials.vote.use", command("vote", "Show the server's vote links")),
                spec(
                        "voteparty",
                        "uxmessentials.voteparty.use",
                        command("voteparty", "Show progress towards the next vote party")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    private static BrigadierCommand command(String literal, String description) {
        return new VoteCommand(literal, description);
    }

    /** The kernel-side description of one vote command, literal and help text, no Brigadier type. */
    private record VoteCommand(String literal, String description) implements BrigadierCommand {}
}
