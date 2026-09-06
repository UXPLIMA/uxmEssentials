/**
 * The server-tweaks context's domain: the pure decisions behind the tweaks, with no Bukkit, Paper, Kyori, or SLF4J.
 * {@link com.uxplima.uxmessentials.servertweaks.domain.ConsoleFilterPolicy} decides whether a rendered console line is
 * suppressed against an operator-configured substring list. {@link com.uxplima.uxmessentials.servertweaks.domain.ChatReportPolicy}
 * decides whether a public-chat message should be re-delivered unsigned. The SignedVelocity protocol model
 * {@link com.uxplima.uxmessentials.servertweaks.domain.SignedVelocityFrame} (the wire decode),
 * {@link com.uxplima.uxmessentials.servertweaks.domain.SignedSource}, and
 * {@link com.uxplima.uxmessentials.servertweaks.domain.SignedChatDirective} (allow/cancel/modify): models a proxy's
 * chat/command ruling. The Bukkit calls that act on these decisions all live in the adapter.
 */
@NullMarked
package com.uxplima.uxmessentials.servertweaks.domain;

import org.jspecify.annotations.NullMarked;
