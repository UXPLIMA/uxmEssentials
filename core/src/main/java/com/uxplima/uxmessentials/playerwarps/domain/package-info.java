/**
 * Pure domain of the player-warps bounded context. The {@code PlayerWarp} aggregate is identified by a durable
 * surrogate {@code PlayerWarpId} the database assigns on its first save, and carries a server-wide-unique
 * {@code PlayerWarpName} so a warp is addressed by name alone. Access is an ordered {@code WarpAccess} axis
 * (public / password / whitelist / private) crossed with a {@code WarpStatus} lifecycle
 * (active / suspended / archived), not a single public flag. Around that identity it composes the presentation,
 * economy, and social facets, {@code DisplayName}, {@code WarpDescription}, {@code IconSpec},
 * {@code WarpCost}, {@code WarpEarnings}, the denormalised {@code RatingSummary} / {@code VisitSummary} rollups,
 * {@code Sponsorship}, {@code RentState}, {@code WarpEffects}, and {@code WarpTimingOverrides}, plus the
 * {@code PlayerWarpLimit} resolved quota, the {@code PlayerWarpError} failure enum, and the sealed
 * {@code PlayerWarpEvent} family. Name uniqueness is enforced at the persistence layer, not here; this package
 * only guards each value's shape. The password hash never enters the domain. The aggregate holds a
 * {@code passwordSet} flag and nothing more. No Bukkit, Paper, Kyori, or logging type appears here; the model is
 * built from value objects and the kernel primitives ({@code PlayerRef}, {@code WorldRef}, {@code Position}).
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerwarps.domain;
