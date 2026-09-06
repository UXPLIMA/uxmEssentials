package com.uxplima.uxmessentials.messaging.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * The messaging context's sealed family of domain events. The per-context seal that closes the event set
 * without touching the shared {@code DomainEvent} marker. Every concrete implementation is a {@code record}
 * (value equality backs the in-process bus and equality-based test consumers), and the adapter bridges each
 * to a Bukkit event so other plugins observe messaging changes without importing this package.
 *
 * <p>Names are past tense. A domain event records something that already happened
 * ({@code PrivateMessageSent}, {@code MailDelivered}, {@code HelpOpRaised}), never an imperative command. A
 * private message is transient, but the <em>fact</em> that one was sent is still a domain event the
 * socialspy audit path and other plugins may observe.
 */
public sealed interface MessagingEvent extends DomainEvent permits PrivateMessageSent, MailDelivered, HelpOpRaised {}
