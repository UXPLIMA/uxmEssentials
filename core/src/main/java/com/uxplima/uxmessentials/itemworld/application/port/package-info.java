/**
 * The itemworld context's outbound ports. Pure application contracts the use cases depend on, implemented in
 * the bukkit-adapter.
 *
 * <p>The only port the itemworld foundation owns is {@link
 * com.uxplima.uxmessentials.itemworld.application.port.ItemworldAudit}, the audit trail for the abusable verbs
 * (bulk {@code /give}, {@code /spawnmob}, {@code /spawner}, the {@code /kill}//{@code /butcher}//{@code
 * /killall}//{@code /remove} entity-purge family, and the {@code /lightning}//{@code /fireball}//{@code
 * /kittycannon} admin-fun verbs). The non-abusable verbs (workstations, cleanup, time/weather, item
 * cosmetics) carry no audit line. Item, entity and world mutation goes through the adapter directly, the
 * itemworld surface is ACL-thin and stateless, so it owns no repository port.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.itemworld.application.port;
