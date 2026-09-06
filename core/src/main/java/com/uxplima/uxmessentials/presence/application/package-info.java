/**
 * Application layer of the presence context: the AFK and vanish use cases that orchestrate the pure
 * {@code PlayerPresence} aggregate through outbound ports, {@code MarkAfk} (manual {@code /afk} toggle and the
 * sweep's auto-mark), {@code ClearAfkOnActivity} (the sync activity listeners' return-from-AFK), {@code
 * ToggleVanish} ({@code /vanish}), and {@code ResolveVisibility} (the read-only vanish query messaging and
 * teleport consume), plus the {@code PresenceModule} {@code FeatureModule} and the {@code PresenceMessageKey}
 * catalog. Presence persists nothing: the per-player aggregate is transient in-memory state. The outward
 * coupling to messaging ({@code /msg} resolution) and teleport ({@code /tpa} listings) is the soft vanish
 * visibility, which degrades to "fully visible" when presence is disabled. No Bukkit, Paper, Kyori, or logging
 * type appears here; the layer is pure Java over the kernel ports.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.presence.application;
