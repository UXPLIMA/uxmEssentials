/**
 * Pure domain of the holograms bounded context: the {@code Hologram} value object (server-wide name,
 * position, ordered text lines, and creation time), the {@code HologramName} and {@code HologramLine} value
 * objects, the modelled {@code HologramError} failures, and the sealed {@code HologramEvent} family.
 * Holograms are server-wide, so a {@code HologramName} is unique across the whole table; a hologram always
 * keeps at least one line, so re-anchoring and line editing produce new validated instances rather than
 * mutating in place. How the lines render (MiniMessage into a native {@code TextDisplay}) is an adapter
 * concern: the domain stores raw line source. No Bukkit, Paper, Kyori, or logging type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.holograms.domain;
