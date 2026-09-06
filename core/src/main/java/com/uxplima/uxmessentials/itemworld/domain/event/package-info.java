/**
 * The itemworld context's domain events. Pure Java records under the sealed
 * {@link com.uxplima.uxmessentials.itemworld.domain.event.ItemWorldEvent} family.
 *
 * <p>Most itemworld verbs are ACL-thin mutations with no meaningful domain event (giving an item, opening a
 * workbench, setting the time), so the event set is deliberately minimal. Only the two verbs that effect a
 * <em>real</em>, observable state change worth bridging to other plugins: a successful mob spawn
 * ({@link com.uxplima.uxmessentials.itemworld.domain.event.MobsSpawned}) and an entity purge
 * ({@link com.uxplima.uxmessentials.itemworld.domain.event.EntitiesPurged}). Both are abusable verbs the use
 * case also audit-logs; the event lets a listener observe the effect without importing this package.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.itemworld.domain.event;
