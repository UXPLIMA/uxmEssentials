/**
 * Pure domain of the warps bounded context: the {@code Warp} value object (server-wide name, position,
 * owner, creation time, and the optional {@code WarpCost} and required-permission gates) and the sealed
 * {@code WarpEvent} family. Warps are server-wide, so a {@code WarpName} is unique across the whole table
 * rather than per owner; the gates are data-driven and optional, so the common warp is free and ungated.
 * Whether a cost is actually charged is an application decision (soft-coupled to the economy context),
 * never a domain one. No Bukkit, Paper, Kyori, or logging type appears here. The model is built from
 * value objects and the cross-cutting kernel primitives ({@code PlayerRef}, {@code WorldRef},
 * {@code Position}).
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.warps.domain;
