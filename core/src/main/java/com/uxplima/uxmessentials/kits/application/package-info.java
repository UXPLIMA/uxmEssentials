/**
 * The kits context's use cases and outbound ports. The use cases orchestrate the {@code KitDefinition}
 * catalog (one config file per kit under {@code modules/kits/kits/}) through the {@code KitRepository} port,
 * gate {@code /kit} through {@code KitAccess} (the per-kit
 * permission, the persisted one-time stamp via {@code KitClaimStore}, the shared cooldown via the kernel
 * {@code Cooldowns} port, and, only when an economy provider is present, the per-kit cost), grant items
 * through the {@code KitGranter} port, and render feedback through the {@code Messages}/{@code MessageSink}
 * pair. The economy coupling is <em>soft</em>: the {@code KitEconomy} seam is injected as an {@code Optional},
 * so a kit's cost is charged when economy is wired and ignored gracefully when it is not. The
 * {@code KitsModule} declares the context's commands and enable gate. No Bukkit, Paper, Kyori, or logging
 * type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.kits.application;
