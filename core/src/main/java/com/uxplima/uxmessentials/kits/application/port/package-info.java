/**
 * The kits context's outbound ports: {@code KitRepository} for the config-backed kit catalog (one file per
 * kit under {@code modules/kits/kits/}), {@code KitClaimStore} for the per-player one-time-claim stamp (PDC),
 * {@code KitGranter}
 * for delivering a kit's items into a player's inventory, and {@code KitEconomy}, the narrow economy seam
 * kits owns so a per-kit cost can be charged without a hard dependency on the economy context. The shared
 * {@code Cooldowns} and {@code Permissions} kernel ports cover the cooldown and permission gates, so the kits
 * context owns no port for those. The application depends only on these interfaces; the Configurate
 * repository, the PDC claim store, the Bukkit granter, and (when wired) the economy bridge implement them in
 * the bukkit adapter.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.kits.application.port;
