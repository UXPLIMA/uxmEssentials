/**
 * The playerstate context's bukkit-adapter wiring. {@code PlayerstateWiring} constructs the use cases over the
 * kernel ports and the context's in-memory store, reconciler, effects bridge, and nearby scan, and publishes
 * the Brigadier commands and the join/quit/respawn listener; {@code PlayerStateServices} holds the constructed
 * use cases the commands share. The context needs no database and no {@code Plugin} handle, all live-player
 * work routes through the kernel {@code Scheduler} port onto the owning region thread.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerstate.adapter;
