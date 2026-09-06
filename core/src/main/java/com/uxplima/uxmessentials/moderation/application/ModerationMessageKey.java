package com.uxplima.uxmessentials.moderation.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The moderation context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code MUTE_APPLIED} ↔ {@code moderation.mute.applied}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in
 * the context: every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum ModerationMessageKey implements MessageKey {

    // /mute, /tempmute, /unmute
    MUTE_APPLIED("moderation.mute.applied"),
    MUTE_APPLIED_TIMED("moderation.mute.applied-timed"),
    MUTE_NOTIFY_TARGET("moderation.mute.notify-target"),
    MUTE_ALREADY("moderation.mute.already"),
    UNMUTE_APPLIED("moderation.unmute.applied"),
    UNMUTE_NOT_MUTED("moderation.unmute.not-muted"),

    // /jail, /unjail
    JAIL_APPLIED("moderation.jail.applied"),
    JAIL_APPLIED_TIMED("moderation.jail.applied-timed"),
    JAIL_NOTIFY_TARGET("moderation.jail.notify-target"),
    JAIL_UNKNOWN("moderation.jail.unknown-jail"),
    JAIL_ALREADY("moderation.jail.already"),
    UNJAIL_APPLIED("moderation.unjail.applied"),
    UNJAIL_NOT_JAILED("moderation.unjail.not-jailed"),
    JAILS_HEADER("moderation.jails.header"),
    JAILS_ENTRY("moderation.jails.entry"),
    JAILS_EMPTY("moderation.jails.empty"),

    // /sanction, aggregated read-only punishment summary for one player
    SANCTION_HEADER("moderation.sanction.header"),
    SANCTION_MUTE_ACTIVE("moderation.sanction.mute-active"),
    SANCTION_MUTE_NONE("moderation.sanction.mute-none"),
    SANCTION_JAIL_ACTIVE("moderation.sanction.jail-active"),
    SANCTION_JAIL_NONE("moderation.sanction.jail-none"),
    SANCTION_BAN_ACTIVE("moderation.sanction.ban-active"),
    SANCTION_BAN_NONE("moderation.sanction.ban-none"),
    SANCTION_WARNS("moderation.sanction.warns"),

    // /setjail, /jail del. Define and remove a stored jail location at the staff member's position
    SETJAIL_SAVED("moderation.setjail.saved"),
    DELJAIL_DELETED("moderation.deljail.deleted"),
    DELJAIL_NOT_FOUND("moderation.deljail.not-found"),

    // /jailedplayers. Review of currently jailed players
    JAILEDLIST_HEADER("moderation.jailedlist-header"),
    JAILEDLIST_ENTRY("moderation.jailedlist-entry"),
    JAILEDLIST_EMPTY("moderation.jailedlist-empty"),

    // /tempban
    TEMPBAN_APPLIED("moderation.tempban.applied"),
    TEMPBAN_KICK("moderation.tempban.kick"),

    // /ban, /unban, permanent UUID ban and its lift
    BAN_APPLIED("moderation.ban.applied"),
    // STRICT address-strictness: a UUID ban also IP-banned the target's known addresses ({count} of them)
    MOD_BAN_STRICT_IP("moderation.ban.strict-ip"),
    BAN_KICK("moderation.ban.kick"),
    BAN_LIFTED("moderation.unban.applied"),
    BAN_NOT_BANNED("moderation.unban.not-banned"),

    // /banlist. Review of currently banned players
    BANLIST_HEADER("moderation.banlist-header"),
    BANLIST_ENTRY("moderation.banlist-entry"),
    BANLIST_EMPTY("moderation.banlist-empty"),

    // /mutelist. Review of currently muted players
    MUTELIST_HEADER("moderation.mutelist-header"),
    MUTELIST_ENTRY("moderation.mutelist-entry"),
    MUTELIST_EMPTY("moderation.mutelist-empty"),

    // /banhistory. A player's full ban/unban history (newest-first)
    BANHISTORY_HEADER("moderation.banhistory.header"),
    BANHISTORY_ENTRY("moderation.banhistory.entry"),
    BANHISTORY_EMPTY("moderation.banhistory.empty"),

    // /mutehistory. A player's full mute/unmute history (newest-first)
    MUTEHISTORY_HEADER("moderation.mutehistory.header"),
    MUTEHISTORY_ENTRY("moderation.mutehistory.entry"),
    MUTEHISTORY_EMPTY("moderation.mutehistory.empty"),

    // /history. A player's full sanction history of every kind (newest-first)
    MOD_HISTORY_HEADER("moderation.history.header"),
    MOD_HISTORY_ENTRY("moderation.history.entry"),
    MOD_HISTORY_EMPTY("moderation.history.empty"),

    // /staffhistory. The sanctions a staff member has issued (newest-first)
    MOD_STAFFHISTORY_HEADER("moderation.staffhistory.header"),
    MOD_STAFFHISTORY_ENTRY("moderation.staffhistory.entry"),
    MOD_STAFFHISTORY_EMPTY("moderation.staffhistory.empty"),

    // /staffrollback. Revoke a staff member's still-active sanctions
    MOD_STAFFROLLBACK_SUMMARY("moderation.staffrollback.summary"),
    MOD_STAFFROLLBACK_NONE("moderation.staffrollback.none"),

    // /modstats. Staff punishment analytics: the server-wide leaderboard and a single-staff breakdown, each
    // with an all-time and a windowed variant so the time frame is stated in the line rather than an inline literal
    MOD_STATS_HEADER("moderation.stats.header"),
    MOD_STATS_HEADER_WINDOW("moderation.stats.header-window"),
    MOD_STATS_ENTRY("moderation.stats.entry"),
    MOD_STATS_EMPTY("moderation.stats.empty"),
    MOD_STATS_STAFF("moderation.stats.staff"),
    MOD_STATS_STAFF_WINDOW("moderation.stats.staff-window"),
    MOD_STATS_STAFF_EMPTY("moderation.stats.staff-empty"),

    // /checkban, /checkmute. Present-tense "is this player banned/muted?" checks
    MOD_CHECK_BANNED("moderation.check.banned"),
    MOD_CHECK_NOT_BANNED("moderation.check.not-banned"),
    MOD_CHECK_MUTED("moderation.check.muted"),
    MOD_CHECK_NOT_MUTED("moderation.check.not-muted"),

    // /kick, /kickall
    KICK_APPLIED("moderation.kick.applied"),
    KICK_KICKED("moderation.kick.kicked"),
    KICKALL_APPLIED("moderation.kickall.applied"),

    // /warn, /tempwarn, /warns, /unwarn
    WARN_APPLIED("moderation.warn.applied"),
    TEMPWARN_APPLIED("moderation.tempwarn.applied"),
    WARN_NOTIFY_TARGET("moderation.warn.notify-target"),
    WARNS_HEADER("moderation.warns.header"),
    WARNS_ENTRY("moderation.warns.entry"),
    WARNS_EMPTY("moderation.warns.empty"),
    UNWARN_CLEARED("moderation.unwarn.cleared"),
    UNWARN_NONE("moderation.unwarn.none"),

    // /banip, /tempbanip, /unbanip
    BANIP_APPLIED("moderation.banip.applied"),
    TEMPBANIP_APPLIED("moderation.tempbanip.applied"),
    BANIP_ALTS_DETECTED("moderation.banip.alts-detected"),
    BANIP_KICK("moderation.banip.kick"),
    UNBANIP_APPLIED("moderation.unbanip.applied"),
    UNBANIP_NOT_BANNED("moderation.unbanip.not-banned"),

    // /freeze, /unfreeze
    FREEZE_APPLIED("moderation.freeze.applied"),
    FREEZE_NOTIFY_TARGET("moderation.freeze.notify-target"),
    UNFREEZE_APPLIED("moderation.unfreeze.applied"),
    UNFREEZE_NOTIFY_TARGET("moderation.unfreeze.notify-target"),
    FREEZE_BLOCKED("moderation.freeze.blocked"),

    // /commandspy. Staff toggle to watch the commands other players run
    COMMANDSPY_ON("moderation.commandspy.on"),
    COMMANDSPY_OFF("moderation.commandspy.off"),
    COMMANDSPY_OBSERVED("moderation.commandspy.observed"),

    // /alts. Accounts sharing a target's last IP
    ALTS_HEADER("moderation.alts.header"),
    ALTS_ENTRY("moderation.alts.entry"),
    ALTS_NONE("moderation.alts.none"),
    ALTS_NO_IP("moderation.alts.no-ip"),

    // /punish. Apply a configured punishment template (preset reason + optional duration)
    TEMPLATE_UNKNOWN("moderation.template.unknown"),

    // /sudo
    SUDO_DONE("moderation.sudo.done"),

    // /lockdown: refuse every login except bypass-perm holders; the kick line is the disconnect screen.
    // The *_SELF keys are the short personal confirmation to the actor, mirroring every other moderation
    // action's distinct self-line so an actor who also receives the broadcast does not see the same line twice.
    MOD_LOCKDOWN_ENABLED("moderation.lockdown.enabled"),
    MOD_LOCKDOWN_DISABLED("moderation.lockdown.disabled"),
    MOD_LOCKDOWN_ENABLED_SELF("moderation.lockdown.enabled-self"),
    MOD_LOCKDOWN_DISABLED_SELF("moderation.lockdown.disabled-self"),
    MOD_LOCKDOWN_KICK("moderation.lockdown.kick"),

    // /seen, /seenip
    SEEN_REPORT("moderation.seen.report"),
    SEEN_NEVER("moderation.seen.never"),
    SEENIP_REPORT("moderation.seenip.report"),
    SEENIP_ALTS("moderation.seenip.alts"),
    SEENIP_NO_IP("moderation.seenip.no-ip"),

    // staff broadcast (the -s/silent flag suppresses these), duration tier cap, warn escalation
    MOD_BROADCAST_BAN("moderation.broadcast.ban"),
    MOD_BROADCAST_TEMPBAN("moderation.broadcast.tempban"),
    MOD_BROADCAST_MUTE("moderation.broadcast.mute"),
    MOD_BROADCAST_KICK("moderation.broadcast.kick"),
    MOD_BROADCAST_WARN("moderation.broadcast.warn"),
    MOD_DURATION_CAPPED("moderation.duration-capped"),
    MOD_WARN_ESCALATED("moderation.warn-escalated"),

    // shared moderation failures
    TARGET_EXEMPT("moderation.target-exempt"),
    BAD_DURATION("moderation.bad-duration"),
    UNKNOWN_TARGET("moderation.unknown-target"),
    MUTED_COMMAND_BLOCKED("moderation.muted-command-blocked"),

    // management GUI. The active-punishments list, the per-punishment detail/manage view, and a player's history
    MOD_GUI_LIST_TITLE("moderation.gui.list.title"),
    MOD_GUI_LIST_PREV("moderation.gui.list.prev"),
    MOD_GUI_LIST_NEXT("moderation.gui.list.next"),
    MOD_GUI_LIST_ENTRY_NAME("moderation.gui.list.entry-name"),
    MOD_GUI_LIST_ENTRY_LORE("moderation.gui.list.entry-lore"),
    MOD_GUI_LIST_EMPTY_NAME("moderation.gui.list.empty-name"),
    MOD_GUI_LIST_EMPTY_LORE("moderation.gui.list.empty-lore"),
    MOD_GUI_KIND_BAN("moderation.gui.kind.ban"),
    MOD_GUI_KIND_MUTE("moderation.gui.kind.mute"),
    MOD_GUI_KIND_JAIL("moderation.gui.kind.jail"),
    MOD_GUI_DETAIL_TITLE("moderation.gui.detail.title"),
    MOD_GUI_DETAIL_VALUE_LORE("moderation.gui.detail.value-lore"),
    MOD_GUI_DETAIL_BACK("moderation.gui.detail.back"),
    MOD_GUI_DETAIL_TARGET("moderation.gui.detail.target"),
    MOD_GUI_DETAIL_TYPE("moderation.gui.detail.type"),
    MOD_GUI_DETAIL_ISSUER("moderation.gui.detail.issuer"),
    MOD_GUI_DETAIL_REASON("moderation.gui.detail.reason"),
    MOD_GUI_DETAIL_REMAINING("moderation.gui.detail.remaining"),
    MOD_GUI_DETAIL_HISTORY("moderation.gui.detail.history"),
    MOD_GUI_DETAIL_HISTORY_HINT("moderation.gui.detail.history-hint"),
    MOD_GUI_DETAIL_REVOKE("moderation.gui.detail.revoke"),
    MOD_GUI_DETAIL_REVOKE_CONFIRM("moderation.gui.detail.revoke-confirm"),
    MOD_GUI_VALUE_PERMANENT("moderation.gui.value.permanent"),
    MOD_GUI_VALUE_NONE("moderation.gui.value.none"),
    MOD_GUI_HISTORY_TITLE("moderation.gui.history.title"),
    MOD_GUI_HISTORY_PREV("moderation.gui.history.prev"),
    MOD_GUI_HISTORY_NEXT("moderation.gui.history.next"),
    MOD_GUI_HISTORY_ENTRY_NAME("moderation.gui.history.entry-name"),
    MOD_GUI_HISTORY_ENTRY_LORE("moderation.gui.history.entry-lore"),
    MOD_GUI_HISTORY_EMPTY_NAME("moderation.gui.history.empty-name"),
    MOD_GUI_HISTORY_EMPTY_LORE("moderation.gui.history.empty-lore"),

    // bare /ban and /mute GUI flow, the player picker title and the per-target confirm screen.
    // The picker reuses the shared gui.player-picker.* chrome; these are the moderation-specific labels.
    MOD_GUI_PICK_BAN_TITLE("moderation.gui.pick.ban-title"),
    MOD_GUI_PICK_MUTE_TITLE("moderation.gui.pick.mute-title"),
    MOD_GUI_CONFIRM_BAN_TITLE("moderation.gui.confirm.ban-title"),
    MOD_GUI_CONFIRM_MUTE_TITLE("moderation.gui.confirm.mute-title"),
    MOD_GUI_CONFIRM_TARGET_NAME("moderation.gui.confirm.target-name"),
    MOD_GUI_CONFIRM_TARGET_LORE("moderation.gui.confirm.target-lore"),
    MOD_GUI_CONFIRM_BAN("moderation.gui.confirm.ban"),
    MOD_GUI_CONFIRM_BAN_LORE("moderation.gui.confirm.ban-lore"),
    MOD_GUI_CONFIRM_BAN_SILENT("moderation.gui.confirm.ban-silent"),
    MOD_GUI_CONFIRM_BAN_SILENT_LORE("moderation.gui.confirm.ban-silent-lore"),
    MOD_GUI_CONFIRM_MUTE("moderation.gui.confirm.mute"),
    MOD_GUI_CONFIRM_MUTE_LORE("moderation.gui.confirm.mute-lore"),
    MOD_GUI_CONFIRM_MUTE_SILENT("moderation.gui.confirm.mute-silent"),
    MOD_GUI_CONFIRM_MUTE_SILENT_LORE("moderation.gui.confirm.mute-silent-lore"),
    MOD_GUI_CONFIRM_REASON("moderation.gui.confirm.reason"),
    MOD_GUI_CONFIRM_REASON_SET_LORE("moderation.gui.confirm.reason-set-lore"),
    MOD_GUI_CONFIRM_REASON_NONE_LORE("moderation.gui.confirm.reason-none-lore"),
    MOD_GUI_CONFIRM_REASON_PROMPT("moderation.gui.confirm.reason-prompt"),
    MOD_GUI_CONFIRM_BACK("moderation.gui.confirm.back"),

    // bare /tempban, /tempmute, /warn and /banip GUI flow. The picker titles, the per-verb confirm-screen
    // labels, and the timed verbs' duration step. The picker reuses the shared gui.player-picker.* chrome and
    // the timed verbs reuse the shared gui.duration-picker.* chrome; these are the moderation-specific labels.
    MOD_GUI_PICK_TEMPBAN_TITLE("moderation.gui.pick.tempban-title"),
    MOD_GUI_PICK_TEMPMUTE_TITLE("moderation.gui.pick.tempmute-title"),
    MOD_GUI_PICK_WARN_TITLE("moderation.gui.pick.warn-title"),
    MOD_GUI_PICK_BANIP_TITLE("moderation.gui.pick.banip-title"),
    MOD_GUI_CONFIRM_TEMPBAN_TITLE("moderation.gui.confirm.tempban-title"),
    MOD_GUI_CONFIRM_TEMPMUTE_TITLE("moderation.gui.confirm.tempmute-title"),
    MOD_GUI_CONFIRM_WARN_TITLE("moderation.gui.confirm.warn-title"),
    MOD_GUI_CONFIRM_BANIP_TITLE("moderation.gui.confirm.banip-title"),
    MOD_GUI_CONFIRM_TEMPBAN("moderation.gui.confirm.tempban"),
    MOD_GUI_CONFIRM_TEMPBAN_LORE("moderation.gui.confirm.tempban-lore"),
    MOD_GUI_CONFIRM_TEMPBAN_SILENT("moderation.gui.confirm.tempban-silent"),
    MOD_GUI_CONFIRM_TEMPBAN_SILENT_LORE("moderation.gui.confirm.tempban-silent-lore"),
    MOD_GUI_CONFIRM_TEMPMUTE("moderation.gui.confirm.tempmute"),
    MOD_GUI_CONFIRM_TEMPMUTE_LORE("moderation.gui.confirm.tempmute-lore"),
    MOD_GUI_CONFIRM_TEMPMUTE_SILENT("moderation.gui.confirm.tempmute-silent"),
    MOD_GUI_CONFIRM_TEMPMUTE_SILENT_LORE("moderation.gui.confirm.tempmute-silent-lore"),
    MOD_GUI_CONFIRM_WARN("moderation.gui.confirm.warn"),
    MOD_GUI_CONFIRM_WARN_LORE("moderation.gui.confirm.warn-lore"),
    MOD_GUI_CONFIRM_WARN_SILENT("moderation.gui.confirm.warn-silent"),
    MOD_GUI_CONFIRM_WARN_SILENT_LORE("moderation.gui.confirm.warn-silent-lore"),
    MOD_GUI_CONFIRM_BANIP("moderation.gui.confirm.banip"),
    MOD_GUI_CONFIRM_BANIP_LORE("moderation.gui.confirm.banip-lore"),
    MOD_GUI_DURATION_TEMPBAN_TITLE("moderation.gui.duration.tempban-title"),
    MOD_GUI_DURATION_TEMPMUTE_TITLE("moderation.gui.duration.tempmute-title"),
    MOD_GUI_DURATION_REJECT("moderation.gui.duration.reject"),

    // bare /banhistory picker title, and the bare /checkban and /checkmute anvil-name prompts. The picker
    // reuses the shared gui.player-picker.* chrome and routes a pick to the read-only history view; the two
    // anvil prompts capture a name that resolves into the existing chat-output check use cases.
    MOD_GUI_PICK_HISTORY_TITLE("moderation.gui.pick.history-title"),
    MOD_GUI_CHECK_BAN_PROMPT("moderation.gui.check.ban-prompt"),
    MOD_GUI_CHECK_MUTE_PROMPT("moderation.gui.check.mute-prompt"),

    // The jail management GUI (bare /jail, /jails, /jailedplayers). The hub is the player picker with two extra
    // footer buttons; choosing a target opens the jail chooser, then the duration step (which offers permanent),
    // then jails the target. The jail-list view manages the defined jails (re-anchor / delete / create), and the
    // jailed-players view releases a currently-jailed player on click.
    MOD_GUI_JAIL_HUB_TITLE("moderation.gui.jail.hub-title"),
    MOD_GUI_JAIL_FOOTER_JAILS("moderation.gui.jail.footer-jails"),
    MOD_GUI_JAIL_FOOTER_JAILS_LORE("moderation.gui.jail.footer-jails-lore"),
    MOD_GUI_JAIL_FOOTER_JAILED("moderation.gui.jail.footer-jailed"),
    MOD_GUI_JAIL_FOOTER_JAILED_LORE("moderation.gui.jail.footer-jailed-lore"),
    MOD_GUI_JAIL_CHOOSE_TITLE("moderation.gui.jail.choose-title"),
    MOD_GUI_JAIL_CHOOSE_PREV("moderation.gui.jail.choose-prev"),
    MOD_GUI_JAIL_CHOOSE_NEXT("moderation.gui.jail.choose-next"),
    MOD_GUI_JAIL_CHOOSE_ENTRY_NAME("moderation.gui.jail.choose-entry-name"),
    MOD_GUI_JAIL_CHOOSE_ENTRY_LORE("moderation.gui.jail.choose-entry-lore"),
    MOD_GUI_JAIL_CHOOSE_EMPTY("moderation.gui.jail.choose-empty"),
    MOD_GUI_JAIL_DURATION_TITLE("moderation.gui.jail.duration-title"),
    MOD_GUI_JAIL_DURATION_REJECT("moderation.gui.jail.duration-reject"),
    MOD_GUI_JAIL_LIST_TITLE("moderation.gui.jail.list-title"),
    MOD_GUI_JAIL_LIST_PREV("moderation.gui.jail.list-prev"),
    MOD_GUI_JAIL_LIST_NEXT("moderation.gui.jail.list-next"),
    MOD_GUI_JAIL_LIST_CREATE("moderation.gui.jail.list-create"),
    MOD_GUI_JAIL_LIST_ENTRY_NAME("moderation.gui.jail.list-entry-name"),
    MOD_GUI_JAIL_LIST_ENTRY_LORE("moderation.gui.jail.list-entry-lore"),
    MOD_GUI_JAIL_LOCATION_UNKNOWN("moderation.gui.jail.location-unknown"),
    MOD_GUI_JAIL_LIST_EMPTY_NAME("moderation.gui.jail.list-empty-name"),
    MOD_GUI_JAIL_LIST_EMPTY_LORE("moderation.gui.jail.list-empty-lore"),
    MOD_GUI_JAIL_EDIT_TITLE("moderation.gui.jail.edit-title"),
    MOD_GUI_JAIL_EDIT_BACK("moderation.gui.jail.edit-back"),
    MOD_GUI_JAIL_EDIT_ANCHOR("moderation.gui.jail.edit-anchor"),
    MOD_GUI_JAIL_EDIT_ANCHOR_LORE("moderation.gui.jail.edit-anchor-lore"),
    MOD_GUI_JAIL_EDIT_GOTO("moderation.gui.jail.edit-goto"),
    MOD_GUI_JAIL_EDIT_GOTO_LORE("moderation.gui.jail.edit-goto-lore"),
    MOD_GUI_JAIL_EDIT_DELETE("moderation.gui.jail.edit-delete"),
    MOD_GUI_JAIL_EDIT_DELETE_LORE("moderation.gui.jail.edit-delete-lore"),
    MOD_GUI_JAIL_CREATE_PROMPT("moderation.gui.jail.create-prompt"),
    MOD_GUI_JAILED_TITLE("moderation.gui.jail.jailed-title"),
    MOD_GUI_JAILED_PREV("moderation.gui.jail.jailed-prev"),
    MOD_GUI_JAILED_NEXT("moderation.gui.jail.jailed-next"),
    MOD_GUI_JAILED_ENTRY_NAME("moderation.gui.jail.jailed-entry-name"),
    MOD_GUI_JAILED_ENTRY_LORE("moderation.gui.jail.jailed-entry-lore"),
    MOD_GUI_JAILED_EMPTY_NAME("moderation.gui.jail.jailed-empty-name"),
    MOD_GUI_JAILED_EMPTY_LORE("moderation.gui.jail.jailed-empty-lore");

    private final String key;

    ModerationMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
