package com.uxplima.uxmessentials.itemworld.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * The itemworld context's sealed family of domain events. The per-context seal that closes the event set
 * without touching the shared {@code DomainEvent} marker. Every concrete implementation is a {@code record}
 * (value equality backs the in-process bus and equality-based test consumers), and the adapter bridges each
 * to a Bukkit event so other plugins observe the effect without importing this package.
 *
 * <p>The set is intentionally small: itemworld is mostly ACL-thin mutations with no domain event, so only the
 * two verbs with a real, observable effect raise one: {@link MobsSpawned} and {@link EntitiesPurged}. Names
 * are past tense: a domain event records something that already happened, never an imperative command.
 */
public sealed interface ItemWorldEvent extends DomainEvent permits MobsSpawned, EntitiesPurged {}
