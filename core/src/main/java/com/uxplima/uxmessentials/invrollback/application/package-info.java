/**
 * The invrollback context's application layer: the {@link com.uxplima.uxmessentials.invrollback.application.InvrollbackModule}
 * feature-module identity, the typed {@link com.uxplima.uxmessentials.invrollback.application.InvrollbackConfig},
 * the {@link com.uxplima.uxmessentials.invrollback.application.InvrollbackMessageKey} catalog, and the
 * {@link com.uxplima.uxmessentials.invrollback.application.CaptureSnapshot} use case that saves a captured
 * inventory through the {@link com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository} port and
 * bounds a player's snapshots to the configured count. Orchestration only. The domain holds the rules, the ports
 * reach the outside. Pure Java: no Bukkit, Paper, Kyori, or SLF4J.
 */
@NullMarked
package com.uxplima.uxmessentials.invrollback.application;

import org.jspecify.annotations.NullMarked;
