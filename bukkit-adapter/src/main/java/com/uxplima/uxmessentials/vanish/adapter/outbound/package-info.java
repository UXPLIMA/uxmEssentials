/**
 * The vanish context's outbound adapters.
 * {@link com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryVanishStore} is the transient
 * {@code ConcurrentHashMap<UUID, VanishLevel>} vanish authority, the single source of vanish truth every consumer
 * reads, and {@link com.uxplima.uxmessentials.vanish.adapter.outbound.BukkitVanishView} applies the hide/show effect
 * to the live server view by driving Bukkit's {@code hidePlayer}/{@code showPlayer} graph on the Folia scheduler.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vanish.adapter.outbound;
