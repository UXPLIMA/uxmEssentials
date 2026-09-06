package com.uxplima.uxmessentials.vote.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The vote context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code VOTE_THANKS} ↔ {@code vote.thanks}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere
 * in the context: every message resolves through one of these.
 *
 * <p>The vote-links shown by {@code /vote} and the reward commands run on a vote are operator-authored
 * config content under {@code modules/vote/config.conf}, rendered/dispatched as written and never
 * parity-checked. Only the plugin's own strings, the thank-you broadcast, the {@code /vote} and
 * {@code /voteparty} framing, the test-reward confirmation, and the players-only rejection, are keys.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum VoteMessageKey implements MessageKey {

    // thank-you broadcast on a credited vote
    VOTE_THANKS("vote.thanks"),

    // /vote header + the per-link entry + the no-links-configured line
    VOTE_LINKS_HEADER("vote.links.header"),
    VOTE_LINKS_ENTRY("vote.links.entry"),
    VOTE_LINKS_EMPTY("vote.links.empty"),

    // /voteparty status line and the party-fired broadcast
    VOTEPARTY_STATUS("voteparty.status"),
    VOTEPARTY_REACHED("voteparty.reached"),

    // /vote testreward confirmation
    VOTE_TESTREWARD("vote.testreward"),

    // /vote claim. Paid out {count} queued rewards, or the queue was empty
    VOTE_CLAIM_PAID("vote.claim.paid"),
    VOTE_CLAIM_EMPTY("vote.claim.empty"),

    // console rejection. Only a player can run /vote or /voteparty
    VOTE_PLAYERS_ONLY("vote.players-only"),

    // /votes <player>. Per-player totals across all periods
    VOTE_TOTAL("vote.total"),

    // /votes unknown player
    VOTE_TOTAL_UNKNOWN("vote.total.unknown"),

    // /vote streak <player>, current and best consecutive-day streak
    VOTE_STREAK("vote.streak"),

    // /votetop header, per-row entry, and empty-state
    VOTE_TOP_HEADER("vote.top.header"),
    VOTE_TOP_ENTRY("vote.top.entry"),
    VOTE_TOP_EMPTY("vote.top.empty"),

    // approaching-party announcement: {remaining} votes still needed
    VOTEPARTY_ANNOUNCE("voteparty.announce"),

    // admin forced a party immediately
    VOTEPARTY_FORCED("voteparty.forced"),

    // admin set the party counter to a specific value: {count}
    VOTEPARTY_COUNT_SET("voteparty.count-set"),

    // admin gave votes to a player: {player}, {amount}
    VOTE_ADMIN_GAVE("vote.admin.gave"),

    // admin reset a player's vote totals: {player}
    VOTE_ADMIN_RESET("vote.admin.reset"),

    // /vote next, per-site cooldown display
    VOTE_NEXT_HEADER("vote.next.header"),
    VOTE_NEXT_ENTRY("vote.next.entry"),
    VOTE_NEXT_AVAILABLE("vote.next.available"),
    VOTE_NEXT_NONE("vote.next.none"),

    // /vote last. When the player last voted on each site
    VOTE_LAST_HEADER("vote.last.header"),
    VOTE_LAST_ENTRY("vote.last.entry"),
    VOTE_LAST_NEVER("vote.last.never"),

    // reminders, login reminder and toggle feedback
    VOTE_REMINDER("vote.reminder"),
    VOTE_REMIND_ENABLED("vote.remind.enabled"),
    VOTE_REMIND_DISABLED("vote.remind.disabled"),

    // vote-broadcast visibility toggle feedback
    VOTE_BROADCASTS_SHOWN("vote.broadcasts.shown"),
    VOTE_BROADCASTS_HIDDEN("vote.broadcasts.hidden"),

    // /vote sites GUI. Title, page navigation, per-site status, and clickable URL prompt
    VOTE_GUI_TITLE("vote.gui.title"),
    VOTE_GUI_PREV("vote.gui.prev"),
    VOTE_GUI_NEXT("vote.gui.next"),
    VOTE_GUI_SITE_VOTABLE("vote.gui.site.votable"),
    VOTE_GUI_SITE_COOLDOWN("vote.gui.site.cooldown"),
    VOTE_GUI_CLICK("vote.gui.click");

    private final String key;

    VoteMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
