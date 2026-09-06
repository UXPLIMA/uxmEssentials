/**
 * Pure domain of the worlds bounded context. The {@code WorldName} value object is the stable
 * registry identity for a managed world, its on-disk folder name, valid even while the world is
 * unloaded, constrained to a safe folder-name shape so a name can never escape the worlds container
 * directory. No Bukkit, Paper, Kyori, or logging type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.worlds.domain;
