package com.uxplima.uxmessentials.presence.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The presence context's command surface (docs/10-feature-modules.md §15.8) as platform-neutral
 * {@link CommandSpec}s: each pairs a literal with its base permission node and a factory that produces the
 * kernel-side {@link BrigadierCommand} description. The presence inbound adapter realises the Brigadier node
 * from the started module's context on the next {@code COMMANDS} fire. Collected here so {@code PresenceModule}
 * stays small and the command/permission pairing is one greppable table the permissions guard checks against
 * {@code paper-plugin.yml}.
 *
 * <p>Seven commands: {@code /afk [reason]} ({@code uxmessentials.afk.use}), {@code /list}
 * ({@code uxmessentials.list.use}), {@code /realname <player>} ({@code uxmessentials.realname.use}),
 * {@code /nick} ({@code uxmessentials.nick.use}), {@code /whois <player>} ({@code uxmessentials.whois.use}),
 * {@code /gc} ({@code uxmessentials.gc.use}), and {@code /staff} ({@code uxmessentials.staff.use}). Vanish moved
 * to its own {@code vanish} context and is no longer a presence command. The staff-membership node
 * ({@code uxmessentials.staff.member}) marks who appears in {@code /staff} and is not a command literal.
 */
final class PresenceCommandSurface {

    private PresenceCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                spec("afk", "uxmessentials.afk.use", cmd("afk", "Toggle your AFK state")),
                spec("list", "uxmessentials.list.use", cmd("list", "List online players")),
                spec("realname", "uxmessentials.realname.use", cmd("realname", "Look up a player's real name")),
                spec("nick", "uxmessentials.nick.use", cmd("nick", "Set or clear a player's display name")),
                spec("whois", "uxmessentials.whois.use", cmd("whois", "Show information about an online player")),
                spec("gc", "uxmessentials.gc.use", cmd("gc", "Show server health")),
                spec("staff", "uxmessentials.staff.use", cmd("staff", "List online staff")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    private static BrigadierCommand cmd(String literal, String description) {
        return new PresenceCommand(literal, description);
    }

    /** The kernel-side description of one presence command, literal and help text, no Brigadier type. */
    private record PresenceCommand(String literal, String description) implements BrigadierCommand {}
}
