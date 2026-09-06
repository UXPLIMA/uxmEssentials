package com.uxplima.uxmessentials.persistence.messaging;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Mail.MAIL;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.domain.MailId;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MailSender;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.MailRecord;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.Record;

/**
 * The anti-corruption mapping between a {@code mail} row and the domain {@link MailItem}. UUIDs are stored as
 * their canonical 36-character text and the send time as epoch milliseconds, the read flag as a {@code 0/1}
 * smallint, so the column shape is identical on every backend; this class is the single place that
 * translation lives.
 *
 * <p>The recipient name is not persisted (only the uuid is), so a {@link MailItem} rebuilt from a row carries
 * the recipient uuid with the name the caller already holds. The repository passes the queried
 * {@link PlayerRef} through rather than inventing a display name from the row. The sender keeps both its
 * (nullable) uuid and the name at send time.
 */
final class MailRows {

    private static final short READ = 1;
    private static final short UNREAD = 0;

    private MailRows() {}

    /** Rebuild a {@link MailItem} from a queried row, attributing it to the already-resolved {@code recipient}. */
    static MailItem toItem(Record row, PlayerRef recipient) {
        MailSender sender = MailSender.stored(
                Optional.ofNullable(row.get(MAIL.SENDER)).map(UUID::fromString), row.get(MAIL.SENDER_NAME));
        return new MailItem(
                MailId.of(row.get(MAIL.ID)),
                recipient,
                sender,
                MessageBody.of(row.get(MAIL.BODY)),
                Instant.ofEpochMilli(row.get(MAIL.SENT_AT)),
                row.get(MAIL.IS_READ) == READ);
    }

    /** Populate a {@link MailRecord} from a domain {@link MailItem} for an insert under {@code assignedId}. */
    static void apply(MailRecord record, MailItem item, long assignedId) {
        record.setId(assignedId)
                .setRecipient(item.recipient().uuid().toString())
                .setSender(item.sender().uuid().map(UUID::toString).orElse(null))
                .setSenderName(item.sender().name())
                .setBody(item.body().value())
                .setSentAt(item.sentAt().toEpochMilli())
                .setIsRead(item.read() ? READ : UNREAD);
    }
}
