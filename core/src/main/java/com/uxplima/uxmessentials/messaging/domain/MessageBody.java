package com.uxplima.uxmessentials.messaging.domain;

import java.util.Objects;

/**
 * The text of a private message, a mail item, or a help-op request, validated at construction so an
 * empty or over-long body can never reach a use case, the repository, or another player's screen. The
 * raw player input is kept verbatim apart from edge trimming. Unlike a {@link com.uxplima.uxmessentials
 * .messaging.domain} name, the message text is content, not an identifier, so it is not lower-cased.
 *
 * <p>The maximum mirrors the {@code mail.body} column width in the messaging migration, so a body that
 * constructs here always fits the durable column; a message and a piece of mail share the one bound so a
 * player cannot say more in a PM than they could leave in mail.
 *
 * @param value the trimmed message text
 */
public record MessageBody(String value) {

    /** Mirrors the {@code mail.body} column width in the messaging migration. */
    public static final int MAX_LENGTH = 256;

    public MessageBody {
        Objects.requireNonNull(value, "value");
    }

    /** Build a body from raw player input, trimming the edges; rejects blank or over-long text. */
    public static MessageBody of(String raw) {
        Objects.requireNonNull(raw, "raw");
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("message body must not be blank");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("message body must be at most " + MAX_LENGTH + " characters");
        }
        return new MessageBody(trimmed);
    }

    @Override
    public String toString() {
        return value;
    }
}
