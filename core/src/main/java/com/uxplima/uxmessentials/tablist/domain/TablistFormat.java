package com.uxplima.uxmessentials.tablist.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import com.uxplima.uxmessentials.shared.display.DisplayCondition;

/**
 * One named tablist format: the header/footer {@link TablistContent} to render, the {@link DisplayCondition} deciding
 * which viewers it applies to, a {@code priority} that breaks the field of matching formats, and the two per-player
 * tab-list overrides a format may carry. The viewer's own list-<em>name</em> template ({@code nameFormat}, how they
 * appear to everyone in the tab list) and their list <em>sort order</em> ({@code sortOrder}). A server may author
 * several formats (a staff format, a build-world format, a default) and {@link TablistFormatConfig#select} picks the
 * highest-priority one whose condition matches each viewer.
 *
 * <p>This mirrors the scoreboard's {@code SidebarBoard}: the {@code content} owns the structural header/footer concerns
 * (its {@link TablistContent#worldBlacklist() world blacklist} still suppresses the tablist within the selected format),
 * while {@code nameFormat} and {@code sortOrder} are the tablist-only additions. Both are optional: an absent
 * {@code nameFormat} leaves the viewer's vanilla list name untouched (and resets it on a switch away from a format that
 * set one), and an absent {@code sortOrder} leaves their vanilla sort order untouched.
 *
 * <p>The {@code nameFormat}, when present, is raw operator content rendered per viewer through the placeholder pipeline
 * and MiniMessage by the adapter; it may embed the {@code {player}} token (the viewer's name) and PlaceholderAPI
 * placeholders. The {@code sortOrder}, when present, is the {@code Player.setPlayerListOrder(int)} value, a positive
 * integer where a higher value sorts the player higher in the tab list (see the renderer for the confirmed semantics).
 *
 * <p>The optional {@code skin} is the one thing native Paper cannot do: a custom texture on the viewer's tab row. When
 * present the adapter delivers this format's row to each viewer through a player-info packet carrying the texture rather
 * than the native list-name/order setters; when absent (the default) the renderer keeps the native path unchanged. The
 * display name and order still come from {@code nameFormat}/{@code sortOrder}: only the delivery changes, plus the skin.
 *
 * <p>The optional {@code layout} is the fixed-slot filler grid: synthetic {@link TablistFiller} rows that occupy the tab
 * cells the real players do not, each positioned by slot. When the layout {@link TablistLayout#isEmpty() carries fillers}
 * the adapter paints them per viewer through packets (real players keep the early slots, the fillers fill the rest);
 * {@link TablistLayout#empty() an empty layout} (the default) leaves the tab exactly as it was before fillers existed.
 *
 * <p>The {@code suppressRealPlayers} flag is the opt-in synthetic-tab switch: when {@code true} the adapter hides the
 * real players from the viewer's tab list entirely (only this format's filler rows are drawn), delivered by rewriting the
 * outbound player-info packets to force every non-filler entry unlisted. It defaults to {@code false}, leaving the tab
 * unchanged: real players show alongside any fillers, the historical behaviour. The flag only makes visual sense with a
 * filler {@code layout}; with neither layout nor suppression the format is the plain header/footer/name case.
 *
 * @param name the format name, non-blank (the config map key; used only for operator-facing identification and tie-break)
 * @param condition the per-viewer gate; {@link DisplayCondition#always()} for an unconditional format
 * @param priority the selection rank; higher wins, ties broken by name (see {@link TablistFormatConfig#select})
 * @param content the operator-authored header/footer rendered when this format is selected
 * @param nameFormat the per-viewer list-name template, or empty to leave the vanilla list name untouched
 * @param sortOrder the per-viewer {@code setPlayerListOrder} value, or empty to leave the vanilla sort order untouched
 * @param skin the custom-skin source, or empty to keep the native list-name/order delivery (the default, no skin)
 * @param layout the fixed-slot filler grid, or {@link TablistLayout#empty()} for no fillers (the default)
 * @param suppressRealPlayers true to hide real players from the viewer's tab (only fillers drawn); false to leave it
 */
public record TablistFormat(
        String name,
        DisplayCondition condition,
        int priority,
        TablistContent content,
        Optional<String> nameFormat,
        OptionalInt sortOrder,
        Optional<TablistSkinSource> skin,
        TablistLayout layout,
        boolean suppressRealPlayers) {

    public TablistFormat {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(nameFormat, "nameFormat");
        Objects.requireNonNull(sortOrder, "sortOrder");
        Objects.requireNonNull(skin, "skin");
        Objects.requireNonNull(layout, "layout");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a tablist format name must not be blank");
        }
    }

    /** A format with a layout but no real-player suppression: fillers sit alongside the real players (the default). */
    public TablistFormat(
            String name,
            DisplayCondition condition,
            int priority,
            TablistContent content,
            Optional<String> nameFormat,
            OptionalInt sortOrder,
            Optional<TablistSkinSource> skin,
            TablistLayout layout) {
        this(name, condition, priority, content, nameFormat, sortOrder, skin, layout, false);
    }

    /** A format with a custom skin but no filler layout, the skin path with the native tab grid untouched. */
    public TablistFormat(
            String name,
            DisplayCondition condition,
            int priority,
            TablistContent content,
            Optional<String> nameFormat,
            OptionalInt sortOrder,
            Optional<TablistSkinSource> skin) {
        this(name, condition, priority, content, nameFormat, sortOrder, skin, TablistLayout.empty(), false);
    }

    /** A format with no custom skin and no filler layout, the common case, the native list-name/order delivery. */
    public TablistFormat(
            String name,
            DisplayCondition condition,
            int priority,
            TablistContent content,
            Optional<String> nameFormat,
            OptionalInt sortOrder) {
        this(name, condition, priority, content, nameFormat, sortOrder, Optional.empty(), TablistLayout.empty(), false);
    }
}
