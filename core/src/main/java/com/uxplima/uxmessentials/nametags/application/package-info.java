/**
 * The nametags context's application layer: the {@link com.uxplima.uxmessentials.nametags.application.NametagsModule}
 * feature module (ships disabled, no persistence, no command, render timer on the {@code Scheduler} port). The
 * nametag is always-on for every eligible wearer when enabled, so there is no use case or command surface here, the
 * operator-authored formats are rendered by the adapter's render timer onto a viewer-restricted text-display
 * hologram. Pure Java: no Bukkit, Paper, Kyori, or SLF4J.
 */
@NullMarked
package com.uxplima.uxmessentials.nametags.application;

import org.jspecify.annotations.NullMarked;
