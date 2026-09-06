package com.uxplima.uxmessentials.messaging.domain;

/**
 * The stable identity of one mail item, the {@code mail.id} primary key. A persisted mail item has a
 * concrete id assigned by the store; an item being composed before it is written carries
 * {@link #UNASSIGNED} so the type is the same on both sides of the save without an {@code Optional} wrapper.
 *
 * @param value the row id, or {@link #UNASSIGNED_VALUE} before the store assigns one
 */
public record MailId(long value) {

    /** The sentinel a not-yet-persisted item carries before the store assigns a real id. */
    public static final long UNASSIGNED_VALUE = 0L;

    /** A mail item that has not yet been written, so it holds no real id. */
    public static final MailId UNASSIGNED = new MailId(UNASSIGNED_VALUE);

    /** A persisted id; rejects the unassigned sentinel so a stored item always has a real key. */
    public static MailId of(long value) {
        if (value == UNASSIGNED_VALUE) {
            throw new IllegalArgumentException("mail id must be a positive assigned value");
        }
        return new MailId(value);
    }

    /** Whether this id has been assigned by the store. */
    public boolean isAssigned() {
        return value != UNASSIGNED_VALUE;
    }
}
