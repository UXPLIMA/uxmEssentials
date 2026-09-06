package com.uxplima.uxmessentials.messaging.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The messaging context's command surface (docs/10-feature-modules.md §15.7) as platform-neutral
 * {@link CommandSpec}s: each pairs a literal with its permission node and a factory that produces the
 * kernel-side {@link BrigadierCommand} description. The messaging inbound adapter realises the Brigadier
 * node from the started module's context on the next {@code COMMANDS} fire. Collected here so
 * {@code MessagingModule} stays small and the command/permission pairing is one greppable table the
 * permissions guard checks against {@code paper-plugin.yml}.
 *
 * <p>Only {@code /msg} {@code /reply} {@code /mail} are the private-channel surface; {@code /msgtoggle}
 * {@code /ignore} {@code /unignore} {@code /ignorelist} {@code /socialspy} {@code /mailclear} {@code /helpop}
 * round it out. There is no public-chat command: that is out of charter.
 */
final class MessagingCommandSurface {

    private MessagingCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                spec("msg", "uxmessentials.msg.use", command("msg", "Send a private message")),
                spec("reply", "uxmessentials.msg.reply", command("reply", "Reply to your last conversation")),
                spec("mail", "uxmessentials.mail.use", command("mail", "Read, send or clear your mail")),
                spec("msgtoggle", "uxmessentials.msg.toggle", command("msgtoggle", "Refuse incoming private messages")),
                spec("rtoggle", "uxmessentials.msg.toggle", command("rtoggle", "Toggle whether you receive replies")),
                spec("ignore", "uxmessentials.msg.ignore", command("ignore", "Ignore a player")),
                spec("unignore", "uxmessentials.msg.ignore", command("unignore", "Stop ignoring a player")),
                spec("ignorelist", "uxmessentials.msg.ignore", command("ignorelist", "List the players you ignore")),
                spec(
                        "socialspy",
                        "uxmessentials.msg.socialspy",
                        command("socialspy", "Observe private messages, globally or for one player")),
                spec("mailclear", "uxmessentials.mail.use", command("mailclear", "Clear your mailbox")),
                spec("helpop", "uxmessentials.helpop.use", command("helpop", "Open a staff support request")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    private static BrigadierCommand command(String literal, String description) {
        return new MessagingCommand(literal, description);
    }

    /** The kernel-side description of one messaging command, literal and help text, no Brigadier type. */
    private record MessagingCommand(String literal, String description) implements BrigadierCommand {}
}
