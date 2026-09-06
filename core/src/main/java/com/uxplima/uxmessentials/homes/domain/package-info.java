/**
 * Pure domain of the homes bounded context: the {@code Home} value object and the {@code HomeSet}
 * aggregate that enforces the per-owner invariants. A slot within the resolved maximum-slot count, one
 * home per slot, and a count no greater than the owner's resolved limit. Plus the sealed {@code HomeEvent}
 * family. A home's identity is its {@code HomeSlot}; its {@code HomeLabel} and {@code HomeIcon} are
 * cosmetic. The aggregate decides; the application layer applies its outcome through the repository and
 * delegates the actual teleport to the teleport context. No Bukkit, Paper, Kyori, or logging type appears
 * here. The model is built from value objects and the cross-cutting kernel primitives ({@code PlayerRef},
 * {@code WorldRef}, {@code Position}).
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.homes.domain;
