package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code messaging_*} placeholders. It is an adapter over the messaging
 * context's existing ports wired during bootstrap. The DB-backed mail repository and ignore store, and the
 * session-scoped conversation, message-toggle, and social-spy stores; when the messaging module is disabled the
 * seam is absent and the placeholders degrade to the dash.
 *
 * <p>The two reads that are durable. {@link #unreadMail(PlayerRef)} / {@link #totalMail(PlayerRef)} (mail is
 * DB-backed and survives restart) and {@link #ignoringCount(PlayerRef)} (the persistent ignore list), answer
 * for an offline player as well. The session-scoped reads, {@link #replyTarget(PlayerRef)} (the in-memory
 * last-conversation partner), {@link #acceptingMessages(PlayerRef)} (the {@code /msgtoggle} PDC flag), and
 * {@link #socialSpy(PlayerRef)} (the {@code /socialspy} session flag), hold no value for an offline player, so
 * the resolver gates them behind the online flag and renders the dash when the player is offline.
 */
public interface MessagingPlaceholders {

    /** How many unread mail items {@code who} holds (the new-mail notify count). DB-backed; safe offline. */
    long unreadMail(PlayerRef who);

    /** How many mail items {@code who} holds in total, read and unread. DB-backed; safe offline. */
    long totalMail(PlayerRef who);

    /** The name of {@code who}'s last private-message partner, the {@code /reply} target, if any this session. */
    Optional<String> replyTarget(PlayerRef who);

    /** True when {@code who} currently accepts incoming private messages (the {@code /msgtoggle} state). */
    boolean acceptingMessages(PlayerRef who);

    /** True when {@code who} currently has the global {@code /socialspy} flag on. */
    boolean socialSpy(PlayerRef who);

    /** How many players {@code who} ignores (the persistent ignore list size). DB-backed; safe offline. */
    int ignoringCount(PlayerRef who);

    /** True when {@code owner}'s ignore list holds {@code other}. */
    boolean ignores(PlayerRef owner, PlayerRef other);
}
