/**
 * Pure domain of the presence bounded context: the immutable {@code PlayerPresence} aggregate (the AFK flag
 * with its optional reason, the last-activity instant, and a vanished flag that mirrors the vanish authority) with
 * its two transition rules (the AFK auto-transition (idle past a threshold) and the activity-clears-AFK rule) and
 * the sealed {@code PresenceEvent} family ({@code WentAfk} / {@code ReturnedFromAfk}). The aggregate is the value a
 * {@code ConcurrentHashMap<UUID, PlayerPresence>} holds, re-stamped by sync activity listeners and read by the async
 * AFK sweep; its vanished flag is overlaid on read from the {@code vanish} context's single vanish authority so a
 * presence reader (the {@code %..._vanished%} placeholder, the sleep exclusion) reflects one vanish state. No Bukkit,
 * Paper, Kyori, or logging type appears in this package. The model is built from value objects and the cross-cutting
 * kernel primitives ({@code PlayerRef}).
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.presence.domain;
