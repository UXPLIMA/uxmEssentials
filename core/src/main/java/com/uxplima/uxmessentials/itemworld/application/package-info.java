/**
 * The itemworld context's application layer, the {@link
 * com.uxplima.uxmessentials.itemworld.application.ItemworldModule feature module}, the {@link
 * com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey message catalog}, the {@link
 * com.uxplima.uxmessentials.itemworld.application.ItemworldCommandSurface command surface}, the thin
 * per-verb-group policies where domain logic exists, and the itemworld audit port.
 *
 * <p>itemworld is overwhelmingly stateless, ACL-thin mutations (docs/10-feature-modules.md §15.10), so the
 * application layer is thin by design: it carries the policies that hold a real rule, the {@code
 * /give}//{@code /more} cap ({@link com.uxplima.uxmessentials.itemworld.application.GiveCapPolicy}), the
 * enchant-level clamp ({@link com.uxplima.uxmessentials.itemworld.application.EnchantPolicy}), the powertool
 * bind model ({@link com.uxplima.uxmessentials.itemworld.application.PowertoolPolicy}), and the entity-purge
 * selection ({@link com.uxplima.uxmessentials.itemworld.application.PurgePolicy}), and the {@link
 * com.uxplima.uxmessentials.itemworld.application.ItemworldConfig} that resolves the per-sub-feature-group and
 * per-command disable flags from {@code itemworld.conf}. Every abusable verb (entity purge, spawners,
 * admin-fun, bulk {@code /give}) is audit-logged through {@link
 * com.uxplima.uxmessentials.itemworld.application.port.ItemworldAudit}. All command inputs are validated at
 * the adapter boundary through the domain value objects before any domain or adapter call.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.itemworld.application;
