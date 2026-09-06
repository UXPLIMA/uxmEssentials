/**
 * The poses context's domain: the immutable value objects behind GSit-parity sitting and posing, the
 * {@link com.uxplima.uxmessentials.poses.domain.PoseType} surface (sit, player-sit, lay, bellyflop, spin, crawl)
 * and the {@link com.uxplima.uxmessentials.poses.domain.PoseSession} a player holds while posing (subject, type,
 * return location, opaque seat handle, optional carrier, start time). The one-session-per-player invariant is
 * owned by the application's registry; the record only enforces its own structural invariants (a player-sit
 * carries a target, no other pose does). Pure Java: no Bukkit, Paper, Kyori, or SLF4J.
 */
@NullMarked
package com.uxplima.uxmessentials.poses.domain;

import org.jspecify.annotations.NullMarked;
