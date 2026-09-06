package com.uxplima.uxmessentials.persistence.messaging;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Mail.MAIL;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailId;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.MailRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link MailRepository} over the generated {@code MAIL} table. A recipient's
 * {@link MailBox} is rebuilt from queryable rows newest-first; the unread count is a {@code COUNT(*)} so the
 * notify never materialises the box. An {@code append} assigns the next id inside the insert transaction
 * (the schema's {@code id} is a portable plain {@code BIGINT}, not a dialect-specific auto-increment), so a
 * concurrent append and the id allocation stay consistent. Mark-read and clear are keyed updates/deletes,
 * and the expiry sweep deletes by send time. Every statement is typed jOOQ DSL; no SQL is ever
 * string-concatenated. Mail is text-only: there are no item attachments.
 */
public final class JooqMailRepository extends JooqRepository implements MailRepository {

    public JooqMailRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public MailBox load(PlayerRef recipient) {
        Objects.requireNonNull(recipient, "recipient");
        List<MailItem> items = read(dsl -> dsl.selectFrom(MAIL)
                .where(MAIL.RECIPIENT.eq(recipient.uuid().toString()))
                .orderBy(MAIL.SENT_AT.desc(), MAIL.ID.desc())
                .fetch()
                .map(row -> MailRows.toItem(row, recipient)));
        return MailBox.of(recipient, items);
    }

    @Override
    public long unreadCount(PlayerRef recipient) {
        Objects.requireNonNull(recipient, "recipient");
        return read(dsl -> dsl.fetchCount(
                MAIL, MAIL.RECIPIENT.eq(recipient.uuid().toString()).and(MAIL.IS_READ.eq((short) 0))));
    }

    @Override
    public MailItem append(MailItem item) {
        Objects.requireNonNull(item, "item");
        long assignedId = write(dsl -> insertWithNextId(dsl, item));
        return item.withId(MailId.of(assignedId));
    }

    @Override
    public void markAllRead(PlayerRef recipient) {
        Objects.requireNonNull(recipient, "recipient");
        write(dsl -> dsl.update(MAIL)
                .set(MAIL.IS_READ, (short) 1)
                .where(MAIL.RECIPIENT.eq(recipient.uuid().toString()))
                .execute());
    }

    @Override
    public void clear(PlayerRef recipient) {
        Objects.requireNonNull(recipient, "recipient");
        write(dsl -> dsl.deleteFrom(MAIL)
                .where(MAIL.RECIPIENT.eq(recipient.uuid().toString()))
                .execute());
    }

    @Override
    public int deleteSentBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        return write(dsl -> dsl.deleteFrom(MAIL)
                .where(MAIL.SENT_AT.lt(cutoff.toEpochMilli()))
                .execute());
    }

    private static long insertWithNextId(DSLContext dsl, MailItem item) {
        Long maxId = dsl.select(org.jooq.impl.DSL.max(MAIL.ID)).from(MAIL).fetchOne(0, Long.class);
        long nextId = (maxId == null ? 0L : maxId) + 1L;
        MailRecord record = dsl.newRecord(MAIL);
        MailRows.apply(record, item, nextId);
        dsl.insertInto(MAIL).set(record).execute();
        return nextId;
    }
}
