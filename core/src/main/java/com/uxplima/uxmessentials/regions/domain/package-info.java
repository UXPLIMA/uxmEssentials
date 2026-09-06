/**
 * The regions context's domain: the immutable value objects behind the WorldGuard region management GUI, the
 * {@link com.uxplima.uxmessentials.regions.domain.RegionRef} identity of a region (world + id), a
 * {@link com.uxplima.uxmessentials.regions.domain.FlagValue} name/value pair, and the
 * {@link com.uxplima.uxmessentials.regions.domain.RegionMemberChange} a roster edit produces. These decouple the
 * application from WorldGuard entirely: the {@code RegionService} port speaks only these types, and the single
 * outbound adapter is the only place a {@code com.sk89q} class is named. Pure Java: no Bukkit, Paper, Kyori, or
 * WorldGuard.
 */
@NullMarked
package com.uxplima.uxmessentials.regions.domain;

import org.jspecify.annotations.NullMarked;
