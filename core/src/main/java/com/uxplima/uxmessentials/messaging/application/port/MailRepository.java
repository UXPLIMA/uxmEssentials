package com.uxplima.uxmessentials.messaging.application.port;

import java.time.Instant;

import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for durable, per-recipient mail storage. Mail is DB-backed and survives restart (the hard
 * messaging invariant): an offline recipient still has their mail on next join. Every mail fact (recipient,
 * sender, sender name, body, send time, read flag) is a first-class column. There is no opaque JSON blob
 * (the architecture persistence invariant), so a {@link MailBox} is rebuilt from queryable rows and the
 * unread count is read with a {@code COUNT(*)} rather than by deserialising a document.
 *
 * <p>Mail is text-only. There are no item attachments (out of scope), so a stored item is its text payload
 * and metadata. The jOOQ adapter behind this port loads a recipient's box, appends one item on a
 * {@code /mail send} (assigning its id), marks a box read, clears it, and bounds an expiry sweep by send
 * time. A cache decorator may sit in front of it; the contract here is the durable source of truth.
 */
public interface MailRepository {

    /** The recipient's full mailbox, newest-first. Empty box when they have none. */
    MailBox load(PlayerRef recipient);

    /** How many unread items the recipient holds, read without materialising the box (the notify count). */
    long unreadCount(PlayerRef recipient);

    /** Persist {@code item} into its recipient's box, returning the item with its assigned id. */
    MailItem append(MailItem item);

    /** Mark every item in {@code recipient}'s box read, the {@code /mail read} side-effect. */
    void markAllRead(PlayerRef recipient);

    /** Remove every item from {@code recipient}'s box, {@code /mail clear} / {@code /mailclear}. */
    void clear(PlayerRef recipient);

    /** Delete every item sent strictly before {@code cutoff}, returning the number removed (expiry sweep). */
    int deleteSentBefore(Instant cutoff);
}
