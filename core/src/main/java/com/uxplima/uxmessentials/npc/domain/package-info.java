/**
 * Pure domain of the npc bounded context: the {@code Npc} value object (server-wide name, position, optional
 * skin, optional click command, an ordered click-action list, and creation time), the {@code NpcName} and
 * {@code NpcSkin} value objects, the modelled {@code NpcError} failures, and the sealed {@code NpcEvent} family.
 * The click-action vocabulary an NPC runs ({@code ClickAction} / {@code ClickActionType} / {@code ClickTrigger})
 * lives in the shared kernel ({@code shared.domain.action}) because it is shared with other click targets such as
 * holograms. NPCs are server-wide, so an {@code NpcName} is unique across the whole table; moving, re-skinning, or
 * rebinding a click command produces new validated instances rather than mutating in place. How an NPC renders (a
 * fake-player spawn packet to each viewer) and how its click command runs are adapter concerns: the domain stores
 * raw values. No Bukkit, Paper, Kyori, or logging type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.npc.domain;
