package com.uxplima.uxmessentials.shared.application.message;

/**
 * The shared kernel's common message keys: the cross-cutting block no single feature context owns.
 *
 * <p>Several families live here (docs/13-i18n §6, the {@code shared} row): the {@code command.*} failures
 * every command-bearing context raises before it reaches a use case (no permission, player-only,
 * unknown player), the {@code cooldown.*} / {@code warmup.*} feedback the shared {@code Cooldowns} /
 * {@code Warmups} ports render, the {@code lang.*} feedback the {@code /lang} override command emits, and
 * the {@code help.*} lines the cross-cutting {@code /help} listing renders. They sit in {@code shared} so
 * any context references them without a cross-context dependency, exactly as a per-feature
 * {@code MessageKey} enum does for its own block.
 *
 * <p>Like every {@link MessageKey} enum the constant name and the catalog key map 1:1
 * ({@code COMMAND_NO_PERMISSION} ↔ {@code command.no-permission}); the locale-parity guard asserts each
 * has an {@code en} entry and that the {@code command.*} / {@code cooldown.*} / {@code warmup.*} /
 * {@code lang.*} / {@code help.*} / {@code common.*} namespaces are owned here.
 */
public enum SharedMessageKey implements MessageKey {

    // cross-cutting command failures shared by every command-bearing context
    COMMAND_NO_PERMISSION("command.no-permission"),
    COMMAND_PLAYERS_ONLY("command.players-only"),
    COMMAND_UNKNOWN_PLAYER("command.unknown-player"),
    COMMAND_UNKNOWN_WORLD("command.unknown-world"),
    COMMAND_INVALID_POSITION("command.invalid-position"),
    COMMAND_USAGE("command.usage"),
    COMMAND_ERROR("command.error"),

    // shared cooldown / warmup feedback rendered from the Cooldowns / Warmups ports
    COOLDOWN_ACTIVE("cooldown.active"),
    WARMUP_STARTED("warmup.started"),
    WARMUP_CANCELLED("warmup.cancelled"),

    // the /lang personal locale override command
    LANG_STATUS("lang.status"),
    LANG_SET("lang.set"),
    LANG_RESET("lang.reset"),
    LANG_UNKNOWN("lang.unknown"),
    LANG_AVAILABLE("lang.available"),

    // the cross-cutting /help command listing. One paginated page of usable commands
    HELP_HEADER("help.header"),
    HELP_ENTRY("help.entry"),
    HELP_ENTRY_ALIASES("help.entry-aliases"),
    HELP_EMPTY("help.empty"),
    HELP_NO_MATCH("help.no-match"),
    HELP_FOOTER("help.footer"),
    HELP_FOOTER_PREV("help.footer-prev"),
    HELP_FOOTER_NEXT("help.footer-next"),

    // What a player is told when another plugin refused an action through the developer API's veto events. One key
    // for every context rather than one per context: this line only has to say that something outside uxmEssentials
    // said no, and the plugin that said no is the one that knows why and can send its own reason.
    COMMON_ACTION_VETOED("common.action-vetoed");

    private final String key;

    SharedMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
