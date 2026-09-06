/**
 * The kits context's outbound adapters: {@code ConfigurateKitRepository} over the per-kit files under
 * {@code modules/kits/kits/} (with {@code KitCodec} for the per-file shape), {@code PdcKitClaims} for the
 * per-player one-time-claim stamp in
 * PDC (NamespacedKeys created once), {@code BukkitKitGranter} for delivering a kit's items into a player's
 * inventory, and {@code KitItemCodec}, the anti-corruption layer between a Bukkit {@code ItemStack} and the
 * domain's opaque {@code KitItem} payload. These implement the kits application ports; the economy bridge for
 * the per-kit cost lives in the economy adapter so the kits context never imports an economy type.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.kits.adapter.outbound;
