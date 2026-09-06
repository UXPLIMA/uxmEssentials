/**
 * The homes context's use cases and outbound ports. The use cases orchestrate the {@code HomeSet}
 * aggregate through the {@code HomeRepository} port, resolve each owner's quota through the shared
 * {@code Permissions} reducer, render feedback through the {@code Messages}/{@code MessageSink} pair, and
 * delegate the actual teleport to the teleport context through the {@code HomeTeleporter} port, homes
 * never re-implements movement. The {@code HomesModule} declares the context's commands, migration, and
 * enable gate. No Bukkit, Paper, Kyori, or logging type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.homes.application;
