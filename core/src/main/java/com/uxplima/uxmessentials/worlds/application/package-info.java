/**
 * The worlds context's use cases and outbound ports. The use cases orchestrate world creation, import,
 * load/unload, and deletion through the context's ports, gate the operator commands through the shared
 * permission seam, and render every outcome through the {@link
 * com.uxplima.uxmessentials.worlds.application.WorldsMessageKey} catalog: no inline player-facing
 * literal appears anywhere in the context. No Bukkit, Paper, Kyori, or logging type appears here.
 *
 * <p>This context gates its two halves in two different places, and only one of them holds for every adapter.
 * The player-facing half is right: {@link com.uxplima.uxmessentials.worlds.application.WorldAccessPolicy}
 * checks the per-world entry node and its bypass inside the use case, so a GUI, a command and the developer
 * API all inherit it. The operator half is not: {@code /world} gates create, import, load, unload, unregister,
 * delete, set, gamerule, backup, restore and pregen on a node each, while the world GUI is reached through
 * {@code uxmessentials.world.gui} alone and runs {@code CreateWorld} with no node check. Warps and kits do the
 * whole thing the first way. The operator half is a known open defect, not a design.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.worlds.application;
