package com.uxplima.uxmessentials.scoreboard.domain;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.display.DisplayCondition;

/**
 * One named sidebar board: the operator content to render ({@link DisplayContent}), the {@link DisplayCondition}
 * deciding which viewers see it, and a {@code priority} that breaks the field of matching boards. A server may author
 * several boards (a staff board, a build-world board, a default) and {@link SidebarConfig#select} picks the
 * highest-priority one whose condition matches each viewer.
 *
 * <p>The board's {@link DisplayContent} still owns its own structural concerns once selected, its
 * {@link DisplayContent#worldBlacklist() world blacklist} keeps suppressing the sidebar within the selected board, so
 * a board chosen by condition can still tear down for a viewer who walks into a world it blacklists. That preserves the
 * single-board back-compat behaviour for the implicit {@code default} board, which carries an always-true condition and
 * relies entirely on its blacklist.
 *
 * @param name the board name, non-blank (the config map key; used only for operator-facing identification)
 * @param content the operator-authored content rendered when this board is selected
 * @param condition the per-viewer gate; {@link DisplayCondition#always()} for an unconditional board
 * @param priority the selection rank; higher wins, ties broken by config order (see {@link SidebarConfig#select})
 */
public record SidebarBoard(String name, DisplayContent content, DisplayCondition condition, int priority) {

    public SidebarBoard {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(condition, "condition");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a sidebar board name must not be blank");
        }
    }
}
