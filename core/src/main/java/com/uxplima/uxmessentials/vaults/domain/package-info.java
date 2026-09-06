/**
 * Pure domain of the vaults bounded context: the {@code Vault} aggregate (an owner's numbered, DB-persisted
 * item container), its identity {@code VaultId}, the {@code VaultSize} value object in rows, the opaque
 * {@code VaultContents} payload the aggregate carries without ever seeing a Bukkit {@code ItemStack}, and the
 * quota value objects ({@code VaultAmount}, {@code VaultSize}) the aggregate checks against. The model decides;
 * the application layer applies its outcome through the repository and the adapter (de)serialises the opaque
 * contents to and from real items at the boundary. No Bukkit, Paper, Kyori, or logging type appears here, the
 * model is built from value objects and the cross-cutting kernel primitives ({@code PlayerRef}).
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vaults.domain;
