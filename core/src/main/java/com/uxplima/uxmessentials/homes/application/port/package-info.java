/**
 * The homes context's outbound ports: {@code HomeRepository} for durable, per-owner slot-keyed home
 * storage, {@code HomeTeleporter} for delegating the actual teleport to the teleport context, and
 * {@code SethomeGuard}. The pre-create seam later slices register region, claim, and economy checks
 * against. The application depends only on these interfaces; the jOOQ repository and the
 * teleport-delegating adapter implement them in the persistence and bukkit adapters.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.homes.application.port;
