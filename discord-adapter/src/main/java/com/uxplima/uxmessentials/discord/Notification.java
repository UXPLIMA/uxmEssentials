package com.uxplima.uxmessentials.discord;

import java.util.Objects;
import java.util.Optional;

/**
 * A single thing to mirror to Discord: which {@link EventCategory} it belongs to, the already-formatted line,
 * and, for {@link EventCategory#ECONOMY}, the transaction amount used to apply the {@code min-eco-notify}
 * threshold. Audit notifications carry no amount.
 *
 * <p>This is the bridge's internal value object; the adapter that subscribes to the host plugin's audit /
 * economy events builds one of these and hands it to the {@link NotificationForwarder}.
 *
 * @param category the channel category to route to
 * @param message the formatted, Discord-ready text (already greppable {@code event=… key=value} or prose)
 * @param amount the economy amount for threshold filtering; empty for non-economy notifications
 */
public record Notification(EventCategory category, String message, Optional<Long> amount) {

    public Notification {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(amount, "amount");
    }

    /** An audit notification with no amount. */
    public static Notification audit(String message) {
        return new Notification(EventCategory.AUDIT, message, Optional.empty());
    }

    /** An economy notification carrying the transaction amount for threshold filtering. */
    public static Notification economy(String message, long amount) {
        return new Notification(EventCategory.ECONOMY, message, Optional.of(amount));
    }
}
