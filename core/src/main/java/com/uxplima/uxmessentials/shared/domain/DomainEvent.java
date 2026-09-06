package com.uxplima.uxmessentials.shared.domain;

/**
 * Non-sealed marker for every domain event the plugin raises.
 *
 * <p>The seal deliberately lives one level down, on each per-context sub-interface
 * ({@code TeleportEvent}, {@code WalletEvent}, {@code ModerationEvent}, …): that sub-interface
 * extends this marker, is {@code sealed}, and {@code permits} only its own concrete event records.
 * A new bounded context defines its own sealed {@code <Context>Event} and never edits this marker, so
 * the closed-event-set guarantee is per context while the cross-cutting publisher still accepts any
 * event through this single type.
 *
 * <p>Every concrete implementation is a {@code record}. Value equality backs bus de-duplication,
 * codec round-trips, and equality-based test consumers. The ArchUnit rule
 * {@code domainEventConcreteImplementationsAreRecords} enforces this.
 */
public interface DomainEvent {}
