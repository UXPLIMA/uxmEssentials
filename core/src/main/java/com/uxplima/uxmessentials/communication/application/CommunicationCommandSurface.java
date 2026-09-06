package com.uxplima.uxmessentials.communication.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.uxplima.uxmessentials.communication.domain.InfoPage;
import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The communication context's command surface as platform-neutral {@link CommandSpec}s. It has two halves. The
 * <em>static</em> half is the plugin's own operator commands, {@code /broadcast}
 * ({@code uxmessentials.communication.broadcast}) and {@code /broadcasttoggle}
 * ({@code uxmessentials.communication.broadcasttoggle}), which are fixed and greppable so the permissions guard
 * checks them against {@code paper-plugin.yml}. The
 * <em>dynamic</em> half is the operator-configured info pages: one command per {@link InfoPage} in the
 * {@code InfoRegistry} ({@code /rules}, {@code /motd}, {@code /info}, any custom page), each guarded by a
 * per-page permission node derived from its command name.
 *
 * <p>The dynamic commands are built at module start from the config-derived registry: their literals are
 * operator data, not a fixed table, while their <em>permission</em> nodes follow the fixed
 * {@code uxmessentials.communication.info.<page>} shape so the permission surface stays predictable. The page
 * bodies themselves are operator content the adapter renders through MiniMessage; only the command wiring lives
 * here.
 */
final class CommunicationCommandSurface {

    private static final String INFO_PERMISSION_PREFIX = "uxmessentials.communication.info.";

    private CommunicationCommandSurface() {}

    /**
     * The static command surface: the operator {@code /broadcast} plus the per-player {@code /broadcasttoggle}. The
     * info commands are added dynamically.
     */
    static List<CommandSpec> staticCommands() {
        return List.of(
                spec(
                        "broadcast",
                        "uxmessentials.communication.broadcast",
                        command("broadcast", "Send an announcement to all online players")),
                spec(
                        "broadcastworld",
                        "uxmessentials.communication.broadcastworld",
                        command("broadcastworld", "Send an announcement to players in your world")),
                spec(
                        "me",
                        "uxmessentials.communication.me",
                        command("me", "Broadcast an action message about yourself")),
                spec(
                        "broadcasttoggle",
                        "uxmessentials.communication.broadcasttoggle",
                        command("broadcasttoggle", "Toggle whether you receive server announcements")),
                spec(
                        "clearchat",
                        "uxmessentials.communication.clearchat",
                        command("clearchat", "Flush the chat for online players")),
                spec(
                        "togglechat",
                        "uxmessentials.communication.togglechat",
                        command("togglechat", "Lock or unlock public chat for non-staff")));
    }

    /** One {@link CommandSpec} per configured info page, guarded by a per-page permission node. */
    static List<CommandSpec> infoCommands(InfoRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        List<CommandSpec> commands = new ArrayList<>();
        for (InfoPage page : registry.all()) {
            commands.add(spec(
                    page.command(),
                    INFO_PERMISSION_PREFIX + page.command(),
                    command(page.command(), "Show the " + page.command() + " info page")));
        }
        return List.copyOf(commands);
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    private static BrigadierCommand command(String literal, String description) {
        return new CommunicationCommand(literal, description);
    }

    /** The kernel-side description of one communication command, literal and help text, no Brigadier type. */
    private record CommunicationCommand(String literal, String description) implements BrigadierCommand {}
}
