package com.uxplima.uxmessentials.messaging.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * One delivered piece of mail: its {@link MailId}, the recipient, the {@link MailSender}, the
 * {@link MessageBody}, when it was sent, and whether it has been read. A mail item is a value object
 * marking it read produces a new instance rather than mutating in place, so the {@link MailBox} aggregate
 * stays immutable between operations.
 *
 * <p>Mail is text-only: there are no item attachments (out of scope), so the body is the whole payload.
 * The send time drives both the newest-first listing and expiry: {@link #isExpired} answers whether the
 * item has outlived a retention window, which the expiry sweep uses to bound its delete.
 *
 * @param id the row id, or {@link MailId#UNASSIGNED} for an item not yet written
 * @param recipient who the mail is for
 * @param sender who the mail is from
 * @param body the text payload
 * @param sentAt when the mail was sent
 * @param read whether the recipient has read it
 */
public record MailItem(
        MailId id, PlayerRef recipient, MailSender sender, MessageBody body, Instant sentAt, boolean read) {

    public MailItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(sentAt, "sentAt");
    }

    /** A new, unread, not-yet-persisted mail item from {@code sender} to {@code recipient}. */
    public static MailItem compose(PlayerRef recipient, MailSender sender, MessageBody body, Instant sentAt) {
        return new MailItem(MailId.UNASSIGNED, recipient, sender, body, sentAt, false);
    }

    /** A copy carrying the {@code id} the store assigned on write. */
    public MailItem withId(MailId assigned) {
        return new MailItem(Objects.requireNonNull(assigned, "assigned"), recipient, sender, body, sentAt, read);
    }

    /** A copy marked read; unchanged when it was already read. */
    public MailItem markRead() {
        return read ? this : new MailItem(id, recipient, sender, body, sentAt, true);
    }

    /** Whether this item is older than {@code retention} as measured from {@code now} (expiry sweep input). */
    public boolean isExpired(Instant now, Duration retention) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(retention, "retention");
        if (retention.isZero() || retention.isNegative()) {
            return false;
        }
        return sentAt.plus(retention).isBefore(now);
    }
}
