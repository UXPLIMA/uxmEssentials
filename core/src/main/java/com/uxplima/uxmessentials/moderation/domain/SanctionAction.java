package com.uxplima.uxmessentials.moderation.domain;

/**
 * The kind a {@link SanctionHistoryEntry} records: the high-level verb applied to a target. The ban and mute
 * families each have a verb and its lift: {@link #BAN} and {@link #UNBAN} are the ban-family rows that
 * {@code /banhistory} surfaces, {@link #MUTE} and {@link #UNMUTE} the mute-family rows {@code /mutehistory}
 * surfaces. {@link #WARN} and {@link #KICK} record the two remaining disciplinary actions.
 *
 * <p>The temporary/permanent and IP distinctions are <em>not</em> separate kinds: they are carried by the
 * entry's expiry and IP fields, mirroring how a {@code BanEntry} folds {@code /ban} and {@code /tempban} into
 * one row. A {@code /banip} is a {@link #BAN} with an IP present; an {@code /unbanip} an {@link #UNBAN} with
 * an IP present; a {@code /tempban} a {@link #BAN} with an expiry present. A {@code /tempwarn} is a {@link #WARN}
 * with the warning's lapse expiry present; a {@link #KICK} never carries an expiry (a kick is a live
 * disconnect, not a stored sentence).
 *
 * <p>The family-scoped reviews stay narrow. {@code /banhistory} surfaces only {@link #BAN}/{@link #UNBAN} and
 * {@code /mutehistory} only {@link #MUTE}/{@link #UNMUTE}, while {@code /history} folds every kind, warns and
 * kicks included, into one newest-first timeline.
 */
public enum SanctionAction {
    BAN,
    UNBAN,
    MUTE,
    UNMUTE,
    WARN,
    KICK
}
