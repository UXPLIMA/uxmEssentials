package com.uxplima.uxmessentials.commandcontrol.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The command-control context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf}; the constant is the compile-time handle, the catalog holds the text. There are no
 * inline player-facing literals in the context: every deny line resolves through one of these.
 *
 * <p>Two lines cover the whitelist/blacklist deny outcome, selected by the {@code use-unknown-command-message} config
 * switch: the {@code UNKNOWN_COMMAND} line mimics vanilla's "unknown command" (no plugin prefix) so a hidden command
 * looks as though it does not exist, while {@code NO_PERMISSION} is the plain, prefixed "you can't use that" line for
 * an operator who would rather be honest that the command exists but is blocked. A third line, {@code PLUGIN_HIDDEN},
 * is the deny shown when the plugin-hide feature blocks a {@code /plugins} or {@code /help} from executing so plugin
 * names are not leaked: it mirrors the vanilla "unknown command" wording for the same reason.
 */
public enum CommandControlMessageKey implements MessageKey {

    // Deny: shown when a command is blocked. UNKNOWN_COMMAND mimics vanilla so the command reads as nonexistent;
    // NO_PERMISSION admits the command exists but is not allowed.
    COMMANDCONTROL_UNKNOWN_COMMAND("commandcontrol.unknown-command"),
    COMMANDCONTROL_NO_PERMISSION("commandcontrol.no-permission"),

    // Plugin-hide. Shown when the plugin-listing / help commands are blocked from executing for a player who may not
    // see them, so /plugins and /help cannot leak plugin names. Reads like vanilla's "unknown command".
    COMMANDCONTROL_PLUGIN_HIDDEN("commandcontrol.plugin-hidden"),

    // Command-spam - shown when a player sends commands faster than the configured window allows. SPAM_KICK is the
    // disconnect reason for the KICK action; SPAM_BLOCKED is the in-chat notice for the BLOCK action (the command was
    // cancelled); SPAM_WARN is the nudge for the WARN action (the command still ran).
    COMMANDCONTROL_SPAM_KICK("commandcontrol.spam-kick"),
    COMMANDCONTROL_SPAM_BLOCKED("commandcontrol.spam-blocked"),
    COMMANDCONTROL_SPAM_WARN("commandcontrol.spam-warn");

    private final String key;

    CommandControlMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
