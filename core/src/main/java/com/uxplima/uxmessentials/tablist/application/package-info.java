/**
 * The tablist context's application layer: the {@link com.uxplima.uxmessentials.tablist.application.TablistModule}
 * feature module (ships disabled, no persistence, no command, render timer on the {@code Scheduler} port). The tablist
 * is always-on for every viewer when enabled, so there is no use case or command surface here: the operator-authored
 * header/footer is rendered by the adapter's render timer. Pure Java: no Bukkit, Paper, Kyori, or SLF4J.
 */
@NullMarked
package com.uxplima.uxmessentials.tablist.application;

import org.jspecify.annotations.NullMarked;
