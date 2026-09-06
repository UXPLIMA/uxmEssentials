/**
 * Pure domain of the kits bounded context: the {@code KitDefinition} value object (id, the items it grants,
 * the cooldown, the one-time flag, the permission flag, and the optional {@code KitCost}), the {@code KitId}
 * / {@code KitItem} / {@code KitCost} value objects, the {@code KitError} failures, and the sealed
 * {@code KitEvent} family. A kit is operator-curated and server-wide, so a {@code KitId} is unique across the
 * whole catalog; the gates (permission, cost) are data-driven and optional. Whether a cost is actually
 * charged is an application decision (soft-coupled to the economy context), never a domain one. The
 * {@code KitItem} payload is an opaque serialized string the domain only carries, so no Bukkit, Paper, Kyori,
 * or logging type appears here. The model is built from value objects and the cross-cutting kernel
 * primitives ({@code PlayerRef}).
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.kits.domain;
