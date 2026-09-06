package com.uxplima.uxmessentials.messaging.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * One recipient's persistent mailbox, the aggregate over their {@link MailItem}s. Mail is DB-backed and
 * survives restart (the hard messaging invariant): an offline recipient still has the mail waiting and reads
 * it on next join. The box is rebuilt from queryable rows; this aggregate enforces the read-order and
 * counting rules over them and is immutable between operations.
 *
 * <p>Items are held newest-first so {@code /mail read} shows the latest first and the unread count is read
 * without re-sorting. The aggregate decides; the application layer applies its outcome through the
 * {@code MailRepository} (the actual insert/mark-read/delete): the box never talks to a database.
 */
public final class MailBox {

    private final PlayerRef recipient;
    private final List<MailItem> items;

    private MailBox(PlayerRef recipient, List<MailItem> items) {
        this.recipient = recipient;
        this.items = items;
    }

    /** An empty mailbox for {@code recipient}. */
    public static MailBox empty(PlayerRef recipient) {
        return new MailBox(Objects.requireNonNull(recipient, "recipient"), List.of());
    }

    /** Rebuild a mailbox from stored items, ordered newest-first regardless of input order. */
    public static MailBox of(PlayerRef recipient, List<MailItem> stored) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(stored, "stored");
        List<MailItem> ordered = new ArrayList<>(stored);
        ordered.sort(Comparator.comparing(MailItem::sentAt).reversed());
        return new MailBox(recipient, List.copyOf(ordered));
    }

    /** The recipient this box belongs to. */
    public PlayerRef recipient() {
        return recipient;
    }

    /** Every item, newest first. */
    public List<MailItem> items() {
        return items;
    }

    /** Whether the box holds no mail at all. */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** How many items the box holds. */
    public int size() {
        return items.size();
    }

    /** How many items are unread, the new-mail notify count. */
    public long unreadCount() {
        return items.stream().filter(item -> !item.read()).count();
    }

    /** Whether any item is unread. */
    public boolean hasUnread() {
        return items.stream().anyMatch(item -> !item.read());
    }

    /** A copy with {@code item} added and the order restored to newest-first. */
    public MailBox with(MailItem item) {
        Objects.requireNonNull(item, "item");
        List<MailItem> next = new ArrayList<>(items);
        next.add(item);
        return MailBox.of(recipient, next);
    }

    /** A copy with every item marked read, the {@code /mail read} side-effect. */
    public MailBox markAllRead() {
        List<MailItem> next = new ArrayList<>(items.size());
        for (MailItem item : items) {
            next.add(item.markRead());
        }
        return new MailBox(recipient, List.copyOf(next));
    }
}
