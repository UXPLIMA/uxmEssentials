/**
 * Pure domain of the playerstate bounded context: the immutable {@code PlayerStateSnapshot} (the god/fly
 * toggles, an optional pinned {@code GameModeRef}, and the walk/fly {@code SpeedValue}s) with its
 * new-snapshot-per-mutation toggle/adjust rules, the {@code PersonalTime}/{@code PersonalWeather} value
 * objects for the per-player time/weather verbs, and the sealed {@code PlayerStateEvent} family. The snapshot
 * is the value a {@code ConcurrentHashMap<UUID, PlayerStateSnapshot>} holds and the reconciler re-applies to
 * the live player; apply-once effects (heal, feed) leave no flag here and surface only as events. No Bukkit,
 * Paper, Kyori, or logging type appears in this package. The model is built from value objects and the
 * cross-cutting kernel primitives ({@code PlayerRef}).
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerstate.domain;
