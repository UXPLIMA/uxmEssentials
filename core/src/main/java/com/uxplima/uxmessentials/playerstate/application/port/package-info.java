/**
 * The playerstate context's outbound ports: {@code PlayerStateStore} (the transient
 * {@code ConcurrentHashMap<UUID, PlayerStateSnapshot>} mutated via {@code compute}), {@code StateReconciler}
 * (push a snapshot to the live player on its owning region thread), {@code PlayerEffects} (the apply-once and
 * live-only verbs, heal/feed/extinguish/suicide/night-vision/ptime/pweather), and {@code NearbyPlayers}
 * (the {@code /near} scan). The adapters implementing these live in the bukkit-adapter; this package holds
 * only the contracts, so no Bukkit, Paper, Kyori, or logging type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerstate.application.port;
