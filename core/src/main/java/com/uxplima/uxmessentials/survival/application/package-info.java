/**
 * The survival context's application layer: the {@link com.uxplima.uxmessentials.survival.application.SurvivalModule}
 * feature-module identity and enable gate, the typed {@link com.uxplima.uxmessentials.survival.application.SurvivalConfig}
 * over {@code modules/survival/config.conf} (a sub-record per mechanic), and the
 * {@link com.uxplima.uxmessentials.survival.application.SurvivalMessageKey} catalog handles for the toggle notices.
 * Pure application code: no Bukkit, Paper, Kyori, or SLF4J: the Bukkit block mechanics live behind the adapter.
 */
@NullMarked
package com.uxplima.uxmessentials.survival.application;

import org.jspecify.annotations.NullMarked;
