/**
 * Outbound ports of the vanish bounded context: the {@code VanishStore} authority (the transient
 * {@code ConcurrentHashMap<UUID, VanishLevel>} of who is vanished, the single source of vanish truth every consumer
 * reads) and the {@code VanishView} that applies the hide/show effect to the live server view. Both are interfaces the
 * bukkit-adapter implements over the packet layer and the in-memory map; the application depends only on these.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vanish.application.port;
