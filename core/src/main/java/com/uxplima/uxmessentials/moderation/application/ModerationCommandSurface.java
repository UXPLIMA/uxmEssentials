package com.uxplima.uxmessentials.moderation.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The moderation context's command surface (docs/10-feature-modules.md §15.9, docs/permissions.md
 * "Moderation") as platform-neutral {@link CommandSpec}s: each pairs a literal with its permission node and a
 * factory that produces the kernel-side {@link BrigadierCommand} description. The inbound adapter realises the
 * Brigadier node from the started module's context on the next {@code COMMANDS} fire. Collected here so the
 * command/permission pairing is one greppable table that matches {@code paper-plugin.yml} and the permissions
 * reference.
 *
 * <p>Every command is permission-gated and every state-mutating one is audit-logged in its use case;
 * {@code /tempmute} is the explicit-duration alias of {@code /mute}, {@code /kickall} shares the kick node,
 * {@code /warns} and {@code /unwarn} share the warn node, {@code /jails}, {@code /jailedplayers} and
 * {@code /setjail} share the jail node (as does {@code /jail del}, a subcommand of {@code /jail} rather than a
 * separate literal), {@code /tempbanip} and {@code /unbanip} share the banip node, {@code /unfreeze} shares the
 * freeze node, and {@code /checkban} and {@code /checkmute} share the check node, matching the node table.
 */
final class ModerationCommandSurface {

    private ModerationCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                spec("mute", "uxmessentials.moderation.mute", "Mute a player"),
                spec("tempmute", "uxmessentials.moderation.mute", "Mute a player for a duration"),
                spec("unmute", "uxmessentials.moderation.unmute", "Lift a player's mute"),
                spec("jail", "uxmessentials.moderation.jail", "Confine a player to a jail"),
                spec("unjail", "uxmessentials.moderation.unjail", "Release a jailed player"),
                spec("togglejail", "uxmessentials.moderation.togglejail", "Toggle a player's jail"),
                spec("jails", "uxmessentials.moderation.jail", "List the configured jails"),
                spec("jailedplayers", "uxmessentials.moderation.jail", "List the players currently jailed"),
                spec("setjail", "uxmessentials.moderation.jail", "Define a jail at your location"),
                spec("tempban", "uxmessentials.moderation.tempban", "Ban a player for a duration"),
                spec("ban", "uxmessentials.moderation.ban", "Permanently ban a player"),
                spec("punish", "uxmessentials.moderation.templates", "Apply a configured punishment template"),
                spec("unban", "uxmessentials.moderation.ban", "Lift a player's permanent ban"),
                spec("banlist", "uxmessentials.moderation.banlist", "Review currently banned players"),
                spec("mutelist", "uxmessentials.moderation.mutelist", "Review currently muted players"),
                spec("banhistory", "uxmessentials.moderation.ban", "Review a player's ban history"),
                spec("mutehistory", "uxmessentials.moderation.mute", "Review a player's mute history"),
                spec("history", "uxmessentials.moderation.history", "Review a player's full sanction history"),
                spec(
                        "staffhistory",
                        "uxmessentials.moderation.staffhistory",
                        "Review the sanctions a staff member issued"),
                spec(
                        "staffrollback",
                        "uxmessentials.moderation.staffrollback",
                        "Revoke a staff member's still-active sanctions"),
                spec("modstats", "uxmessentials.moderation.stats", "Show staff punishment analytics"),
                spec("checkban", "uxmessentials.moderation.check", "Check whether a player is banned"),
                spec("checkmute", "uxmessentials.moderation.check", "Check whether a player is muted"),
                spec("kick", "uxmessentials.moderation.kick", "Kick a player"),
                spec("kickall", "uxmessentials.moderation.kick", "Kick all non-exempt players"),
                spec("warn", "uxmessentials.moderation.warn", "Warn a player"),
                spec("tempwarn", "uxmessentials.moderation.warn", "Warn a player for a duration"),
                spec("warns", "uxmessentials.moderation.warn", "Review a player's warnings"),
                spec("unwarn", "uxmessentials.moderation.warn", "Clear a player's warnings"),
                spec("sanction", "uxmessentials.moderation.sanction", "Show a player's punishment summary"),
                spec("banip", "uxmessentials.moderation.banip", "Ban a player or IP by address"),
                spec("tempbanip", "uxmessentials.moderation.banip", "Ban an IP for a duration"),
                spec("unbanip", "uxmessentials.moderation.banip", "Lift an IP ban"),
                spec("freeze", "uxmessentials.moderation.freeze", "Freeze a player in place"),
                spec("unfreeze", "uxmessentials.moderation.freeze", "Release a frozen player"),
                spec("seen", "uxmessentials.moderation.seen", "Show a player's last-seen"),
                spec("seenip", "uxmessentials.moderation.seen", "Show a player's last IP and alts"),
                spec("alts", "uxmessentials.moderation.seen", "List accounts sharing a player's IP"),
                spec("commandspy", "uxmessentials.moderation.commandspy", "Watch the commands players run"),
                spec("lockdown", "uxmessentials.moderation.lockdown", "Lock the server to bypass holders only"),
                spec("sudo", "uxmessentials.moderation.sudo", "Run a command as another player"));
    }

    private static CommandSpec spec(String literal, String permission, String description) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> new ModerationCommand(literal, description);
        return new CommandSpec(literal, permission, factory);
    }

    /** The kernel-side description of one moderation command, literal and help text, no Brigadier type. */
    private record ModerationCommand(String literal, String description) implements BrigadierCommand {}
}
