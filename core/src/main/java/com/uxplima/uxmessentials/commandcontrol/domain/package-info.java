/**
 * The command-control context's domain: the pure allow/deny decision behind the command whitelist / blacklist. The
 * {@link com.uxplima.uxmessentials.commandcontrol.domain.RuleSet} decides, for one command root and one player's
 * facts, whether the command may run; {@link com.uxplima.uxmessentials.commandcontrol.domain.RuleMode} is the two
 * modes (only-listed-allowed vs listed-denied) and
 * {@link com.uxplima.uxmessentials.commandcontrol.domain.PlayerFacts} is the abstraction over the one player the
 * decision reads, their permission group and a permission-check function. The Bukkit event and the group/permission
 * lookups live in the adapter; nothing here touches a live player. Pure Java: no Bukkit, Paper, Kyori, or SLF4J.
 */
@NullMarked
package com.uxplima.uxmessentials.commandcontrol.domain;

import org.jspecify.annotations.NullMarked;
