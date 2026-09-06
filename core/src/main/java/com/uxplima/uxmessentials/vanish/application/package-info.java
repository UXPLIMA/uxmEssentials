/**
 * Application layer of the vanish bounded context: the {@code VanishModule} feature-module identity, the typed
 * import com.uxplima.uxmessentials.shared.application.message.Notifier;
 * {@code VanishConfig}, the {@code VanishMessageKey} catalog handles, the {@code ToggleVanish} use case (the single
 * writer of the vanish state, driving the {@code VanishStore} authority and the {@code VanishView} effect), and the
 * {@code Notifier} feedback helper. It orchestrates the pure domain rule through the outbound ports and holds no
 * Bukkit, Paper, Kyori, or logging type: those live in the bukkit-adapter behind the ports.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vanish.application;
