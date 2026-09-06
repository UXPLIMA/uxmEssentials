/**
 * The teleport context's application layer: the use cases that orchestrate the {@code teleport} domain
 * through the shared kernel ports (scheduler, permissions, cooldowns, warmups, messages, lookups, the
 * event bus) plus the context-owned outbound ports (the back-location store, the spawn directory, the
 * pre-warmed RTP queue, the teleport executor). {@code TeleportModule} declares the context as a
 * first-class {@code FeatureModule}. Pure application code: no Bukkit, Paper, Kyori, or logging types.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.teleport.application;
