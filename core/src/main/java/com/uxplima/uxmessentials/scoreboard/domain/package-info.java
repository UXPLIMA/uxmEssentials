/**
 * The scoreboard context's domain: the immutable {@link com.uxplima.uxmessentials.scoreboard.domain.DisplayContent}
 * value object that captures the operator-authored sidebar title, sidebar lines, refresh cadence, and per-world
 * blacklist, plus the per-context sealed domain-event family. The content strings are raw MiniMessage source the
 * adapter renders later. The domain only enforces the structural invariants (line counts within the sidebar limit, a
 * positive refresh interval). Pure Java: no Bukkit, Paper, Kyori, or SLF4J.
 */
@NullMarked
package com.uxplima.uxmessentials.scoreboard.domain;

import org.jspecify.annotations.NullMarked;
