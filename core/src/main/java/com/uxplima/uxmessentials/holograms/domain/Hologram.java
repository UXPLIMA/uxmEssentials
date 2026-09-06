package com.uxplima.uxmessentials.holograms.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import org.jspecify.annotations.Nullable;

/**
 * One server-wide hologram: a {@link HologramName}, the {@link Position} it floats at, its {@link HologramContent}
 * (the {@link HologramType} and what it renders. Text lines, an item material, or a BlockData string), its visual
 * {@link Appearance}, its {@link Visibility}, its display {@link Rotation}, how often it re-renders, and the moment
 * it was created. A hologram is a value object. Re-anchoring (a move), editing a line, restyling, or switching to
 * an item/block produces a new instance rather than mutating in place, so the aggregate is always in a valid state
 * and a repository save records a fully-formed snapshot.
 *
 * <p>The content fields are grouped into {@link HologramContent} to keep this aggregate small; {@code Hologram}
 * exposes the same content transitions and accessors it always did ({@code withLineAppended}, {@code asItem},
 * {@code type()}, {@code lines()}, …) and delegates each to the content value object, whose constructor enforces
 * the type invariants (a TEXT hologram keeps at least one line; an ITEM/BLOCK hologram carries its model string).
 * The legacy eleven-argument constructor is retained so the persistence mapper and field-by-field callers are
 * unaffected by the grouping.
 *
 * <p>The position carries its own {@link com.uxplima.uxmessentials.shared.domain.WorldRef}, so the hologram's
 * world is read from {@code location().world()}. {@link #refreshIntervalTicks()} is 0 for a static hologram
 * (rendered once, never re-rendered); a positive value means the live entity re-renders on that cadence so its
 * lines pick up fresh placeholder values. {@code createdAt} is preserved across every move or edit.
 *
 * <p>{@link #linkedNpcName()} is the optional name of an NPC the hologram follows (the
 * link-with-NPC feature): when set, the renderer anchors the hologram above that NPC's head and re-anchors as it
 * moves, falling back to {@link #location()} when no such NPC exists; when null the hologram stays anchored to its
 * own stored location. It is a positioning concern only, so it lives on the aggregate as a plain name and is
 * preserved across every other edit.
 *
 * @param name the hologram's canonical, server-unique name
 * @param location where the hologram floats when it is not linked to an NPC
 * @param content the type and what it renders (text lines, item material, or block data)
 * @param appearance the visual styling (billboard, background, brightness, scale, …)
 * @param visibility who may see the hologram and how far away it stays visible
 * @param rotation the display's stored spin (yaw + pitch), only visible with a FIXED billboard
 * @param refreshIntervalTicks how often (in ticks) the live entity re-renders, or 0 for a static hologram
 * @param createdAt when the hologram was first created (preserved across a move or edit)
 * @param linkedNpcName the name of the NPC the hologram follows, or null when it is anchored to its own location
 * @param clickCommand the command run as the clicking player, or null when the hologram is not clickable
 * @param leaderboard the ranked source a leaderboard hologram regenerates its lines from, or null
 * @param pages the ordered page set of a multi-page hologram (at least two pages, page 0 == {@link #lines()}),
 *     or null when the hologram is an ordinary single-page hologram
 * @param growUp whether the lines grow upward from the anchor (the anchor is the bottom) instead of the default
 *     downward (the anchor is the top)
 * @param actions the ordered click-action chain a click runs (parity with the NPC action list), copied defensively
 *     and defaulting to empty; never null
 */
public record Hologram(
        HologramName name,
        Position location,
        HologramContent content,
        Appearance appearance,
        Visibility visibility,
        Rotation rotation,
        int refreshIntervalTicks,
        Instant createdAt,
        @Nullable String linkedNpcName,
        @Nullable String clickCommand,
        @Nullable LeaderboardSpec leaderboard,
        @Nullable List<HologramPage> pages,
        boolean growUp,
        List<ClickAction> actions) {

    /** A refresh interval of 0 means "static": render once on enable, never re-render. */
    public static final int STATIC = 0;

    /** The most pages one hologram may carry, a sanity cap on the page set. */
    public static final int MAX_PAGES = 32;

    /** The most click actions one hologram may carry, a sanity cap on the action chain. */
    public static final int MAX_ACTIONS = 32;

    public Hologram {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(createdAt, "createdAt");
        if (refreshIntervalTicks < 0) {
            throw new IllegalArgumentException("refreshIntervalTicks must not be negative: " + refreshIntervalTicks);
        }
        if (pages != null) {
            // List.copyOf rejects a null element; the page set is only ever set through withPages(...), which
            // collapses a zero/one-page list to null, so a non-null page set always carries at least two pages.
            pages = List.copyOf(pages);
            if (pages.size() < 2) {
                throw new IllegalArgumentException("a multi-page hologram needs at least two pages");
            }
            if (pages.size() > MAX_PAGES) {
                throw new IllegalArgumentException("a hologram may not exceed " + MAX_PAGES + " pages");
            }
        }
        actions = actions == null ? List.of() : List.copyOf(actions);
        if (actions.size() > MAX_ACTIONS) {
            throw new IllegalArgumentException("a hologram may not exceed " + MAX_ACTIONS + " actions");
        }
    }

    /**
     * The legacy field-by-field constructor, retained so the persistence mapper and field-level callers are
     * unaffected by the content grouping. The type and what it renders are folded into a {@link HologramContent},
     * which applies the same type invariants and defensive line copy it always did. The hologram is created
     * unlinked; the persistence mapper applies a stored {@link #linkedNpcName()} afterwards through
     * {@link #linkedTo(String)}, so a row threads the link through this same field-by-field path.
     */
    public Hologram(
            HologramName name,
            Position location,
            HologramType type,
            List<HologramLine> lines,
            @Nullable String itemMaterial,
            @Nullable String blockData,
            @Nullable String headTexture,
            @Nullable String entityType,
            Appearance appearance,
            Visibility visibility,
            Rotation rotation,
            int refreshIntervalTicks,
            Instant createdAt) {
        this(
                name,
                location,
                new HologramContent(type, lines, itemMaterial, blockData, headTexture, entityType),
                appearance,
                visibility,
                rotation,
                refreshIntervalTicks,
                createdAt,
                null,
                null,
                null,
                null,
                false,
                List.of());
    }

    /**
     * A new TEXT hologram created now at {@code location} with the given ordered lines (at least one), the
     * default {@link Appearance}, visible to everyone, and no refresh interval (static).
     */
    public static Hologram create(HologramName name, Position location, List<HologramLine> lines, Instant createdAt) {
        return fresh(name, location, HologramContent.text(lines), createdAt);
    }

    /** A new ITEM hologram created now at {@code location} showing {@code itemMaterial}, with no lines. */
    public static Hologram createItem(HologramName name, Position location, String itemMaterial, Instant createdAt) {
        return fresh(name, location, HologramContent.item(itemMaterial), createdAt);
    }

    /** A new BLOCK hologram created now at {@code location} showing {@code blockData}, with no lines. */
    public static Hologram createBlock(HologramName name, Position location, String blockData, Instant createdAt) {
        return fresh(name, location, HologramContent.block(blockData), createdAt);
    }

    /** A new HEAD hologram created now at {@code location} showing the player head with base64 {@code headTexture}. */
    public static Hologram createHead(HologramName name, Position location, String headTexture, Instant createdAt) {
        return fresh(name, location, HologramContent.head(headTexture), createdAt);
    }

    /** A new ENTITY hologram created now at {@code location} showing the frozen mob {@code entityType}. */
    public static Hologram createEntity(HologramName name, Position location, String entityType, Instant createdAt) {
        return fresh(name, location, HologramContent.entity(entityType), createdAt);
    }

    private static Hologram fresh(HologramName name, Position location, HologramContent content, Instant createdAt) {
        return new Hologram(
                name,
                location,
                content,
                Appearance.defaults(),
                Visibility.everyone(),
                Rotation.NONE,
                STATIC,
                createdAt,
                null,
                null,
                null,
                null,
                false,
                List.of());
    }

    // --- Content accessors (delegated, so existing call sites are unchanged) ---

    public HologramType type() {
        return content.type();
    }

    public List<HologramLine> lines() {
        return content.lines();
    }

    public @Nullable String itemMaterial() {
        return content.itemMaterial();
    }

    public @Nullable String blockData() {
        return content.blockData();
    }

    public @Nullable String headTexture() {
        return content.headTexture();
    }

    public @Nullable String entityType() {
        return content.entityType();
    }

    /** The number of lines this hologram renders (0 for an item/block hologram with no label lines). */
    public int lineCount() {
        return content.lineCount();
    }

    /** Whether this hologram re-renders on a cadence (a positive interval), rather than rendering once. */
    public boolean refreshes() {
        return refreshIntervalTicks > STATIC;
    }

    /** Whether this hologram follows an NPC rather than anchoring to its own stored location. */
    public boolean isLinked() {
        return linkedNpcName != null;
    }

    /** Whether this hologram has a page set (two or more pages) rather than a single fixed set of lines. */
    public boolean isMultiPage() {
        return pages != null;
    }

    /** The number of pages this hologram carries (1 for an ordinary single-page hologram). */
    public int pageCount() {
        return pages == null ? 1 : pages.size();
    }

    /**
     * The ordered lines of the page at {@code index}. For a single-page hologram only index 0 is valid and
     * returns {@link #lines()}; for a multi-page hologram the index addresses its page set. Rejects an
     * out-of-range index.
     */
    public List<HologramLine> pageLines(int index) {
        if (pages == null) {
            if (index != 0) {
                throw new IndexOutOfBoundsException("page index out of range: " + index);
            }
            return lines();
        }
        if (index < 0 || index >= pages.size()) {
            throw new IndexOutOfBoundsException("page index out of range: " + index);
        }
        return pages.get(index).lines();
    }

    /**
     * The line count of the longest page. The number of text displays a multi-page hologram must spawn so
     * every page fits, with shorter pages blanking the surplus. For a single-page hologram this is
     * {@link #lineCount()}.
     */
    public int maxPageLineCount() {
        if (pages == null) {
            return lineCount();
        }
        int max = 0;
        for (HologramPage page : pages) {
            max = Math.max(max, page.lineCount());
        }
        return max;
    }

    // --- Transitions (createdAt and the untouched fields are always preserved) ---

    /** A pre-filled builder for the internal transitions; the public surface is unchanged. */
    HologramBuilder toBuilder() {
        return new HologramBuilder(this);
    }

    /** A copy re-anchored to {@code newLocation}, keeping everything else (including any NPC link). */
    public Hologram movedTo(Position newLocation) {
        return toBuilder()
                .location(Objects.requireNonNull(newLocation, "newLocation"))
                .build();
    }

    /**
     * A copy linked to the NPC named {@code npcName}, so the renderer anchors it above that NPC and follows the
     * NPC as it moves; the hologram's own stored {@link #location()} is kept untouched so unlinking restores it.
     * Backs {@code /hologram linknpc}.
     */
    public Hologram linkedTo(String npcName) {
        return toBuilder()
                .linkedNpcName(Objects.requireNonNull(npcName, "npcName"))
                .build();
    }

    /**
     * A copy with any NPC link cleared, so the hologram anchors to its own stored {@link #location()} again. Backs
     * {@code /hologram unlinknpc}; a no-op in effect on an already-unlinked hologram.
     */
    public Hologram unlinked() {
        return toBuilder().linkedNpcName(null).build();
    }

    /**
     * A copy with the command run when a player clicks the hologram, or {@code null} to clear it (the hologram is
     * then not clickable). Backs {@code /hologram clickcommand}; the command is run as the clicking player.
     */
    public Hologram withClickCommand(@Nullable String command) {
        return toBuilder().clickCommand(command).build();
    }

    /**
     * A copy made a leaderboard showing {@code spec}'s ranked source, or {@code null} to clear it (the hologram is
     * then a normal hologram again). Backs {@code /hologram leaderboard}; the renderer regenerates the displayed
     * lines from the provider on each refresh.
     */
    public Hologram withLeaderboard(@Nullable LeaderboardSpec spec) {
        return toBuilder().leaderboard(spec).build();
    }

    /**
     * A copy whose lines grow upward from the anchor when {@code grow} is true (the anchor becomes the bottom of
     * the text), or downward as usual when false. Backs {@code /hologram growup}; a pure positioning choice that
     * the renderer applies as a spawn offset, so it never changes the stored {@link #location()}.
     */
    public Hologram withGrowUp(boolean grow) {
        return toBuilder().growUp(grow).build();
    }

    /** A copy with {@code action} appended to the end of the click-action chain, keeping everything else. */
    public Hologram withActionAdded(ClickAction action) {
        Objects.requireNonNull(action, "action");
        if (actions.size() >= MAX_ACTIONS) {
            throw new IllegalArgumentException("a hologram may not exceed " + MAX_ACTIONS + " actions");
        }
        List<ClickAction> updated = new java.util.ArrayList<>(actions);
        updated.add(action);
        return toBuilder().actions(updated).build();
    }

    /**
     * A copy with the action at the 0-based {@code index} removed, keeping everything else. Throws
     * {@link IndexOutOfBoundsException} when {@code index} is outside the current action chain.
     */
    public Hologram withActionRemovedAt(int index) {
        if (index < 0 || index >= actions.size()) {
            throw new IndexOutOfBoundsException("action index out of range: " + index);
        }
        List<ClickAction> updated = new java.util.ArrayList<>(actions);
        updated.remove(index);
        return toBuilder().actions(updated).build();
    }

    /** A copy with no actions, keeping everything else. */
    public Hologram withActionsCleared() {
        return toBuilder().actions(List.of()).build();
    }

    /**
     * A copy with {@code action} inserted at the 0-based {@code index} (an {@code index} of the size appends).
     * Throws {@link IndexOutOfBoundsException} when {@code index} is negative or past the current size.
     */
    public Hologram withActionInsertedAt(int index, ClickAction action) {
        Objects.requireNonNull(action, "action");
        if (index < 0 || index > actions.size()) {
            throw new IndexOutOfBoundsException("action insert index out of range: " + index);
        }
        if (actions.size() >= MAX_ACTIONS) {
            throw new IllegalArgumentException("a hologram may not exceed " + MAX_ACTIONS + " actions");
        }
        List<ClickAction> updated = new java.util.ArrayList<>(actions);
        updated.add(index, action);
        return toBuilder().actions(updated).build();
    }

    /** A copy with the action at the 0-based {@code index} replaced by {@code action}, keeping everything else. */
    public Hologram withActionSetAt(int index, ClickAction action) {
        Objects.requireNonNull(action, "action");
        if (index < 0 || index >= actions.size()) {
            throw new IndexOutOfBoundsException("action index out of range: " + index);
        }
        List<ClickAction> updated = new java.util.ArrayList<>(actions);
        updated.set(index, action);
        return toBuilder().actions(updated).build();
    }

    /** A copy with the action at 0-based {@code from} moved to 0-based {@code to}, keeping everything else. */
    public Hologram withActionMoved(int from, int to) {
        if (from < 0 || from >= actions.size() || to < 0 || to >= actions.size()) {
            throw new IndexOutOfBoundsException("action move index out of range: " + from + " -> " + to);
        }
        List<ClickAction> updated = new java.util.ArrayList<>(actions);
        ClickAction moved = updated.remove(from);
        updated.add(to, moved);
        return toBuilder().actions(updated).build();
    }

    /** Whether clicking this hologram runs at least one action. */
    public boolean hasActions() {
        return !actions.isEmpty();
    }

    /**
     * A copy with its page set replaced. A {@code null} or single-page list clears the paging, the hologram
     * becomes an ordinary single-page hologram, adopting that lone page's lines as its content when one is given.
     * Two or more pages make it multi-page, and page 0's lines become the rendered {@link #content()} so the base
     * spawn and every single-page code path keep showing the first page. Used by the page-management use cases.
     */
    public Hologram withPages(@Nullable List<HologramPage> newPages) {
        if (newPages == null || newPages.size() <= 1) {
            HologramBuilder builder = toBuilder().pages(null);
            if (newPages != null && newPages.size() == 1) {
                builder.content(content.withLines(newPages.get(0).lines()));
            }
            return builder.build();
        }
        List<HologramPage> copy = List.copyOf(newPages);
        return toBuilder()
                .pages(copy)
                .content(content.withLines(copy.get(0).lines()))
                .build();
    }

    /**
     * A copy with {@code page} appended after the current last page. A single-page hologram's current lines
     * become page 0 and {@code page} becomes page 1, so the first {@code add} promotes it to multi-page.
     */
    public Hologram withPageAppended(HologramPage page) {
        Objects.requireNonNull(page, "page");
        List<HologramPage> next = new java.util.ArrayList<>(currentPages());
        next.add(page);
        return withPages(next);
    }

    /**
     * A copy with the page at {@code index} removed; rejects an out-of-range index and a hologram that is not
     * multi-page. Removing down to a single page collapses the hologram back to an ordinary one showing that
     * page's lines.
     */
    public Hologram withPageRemoved(int index) {
        if (pages == null) {
            throw new IllegalStateException("not a multi-page hologram");
        }
        if (index < 0 || index >= pages.size()) {
            throw new IndexOutOfBoundsException("page index out of range: " + index);
        }
        List<HologramPage> next = new java.util.ArrayList<>(pages);
        next.remove(index);
        return withPages(next);
    }

    /** The current pages as an explicit list: the stored page set, or the lone implicit page 0 (the content lines). */
    private List<HologramPage> currentPages() {
        if (pages != null) {
            return pages;
        }
        return lines().isEmpty() ? List.of() : List.of(HologramPage.of(lines()));
    }

    /**
     * A copy whose text lines are replaced wholesale, keeping every other field. Used by the renderer to lay a
     * leaderboard's provider-generated rows onto the hologram for one render without persisting them.
     */
    public Hologram withLines(List<HologramLine> newLines) {
        return withContent(content.withLines(newLines));
    }

    /**
     * A full clone under {@code newName}, keeping every other property, location, content, appearance, visibility,
     * rotation, refresh interval, creation time and any NPC link. Backs {@code /hologram copy}: the duplicate is the
     * same hologram in every way but its name.
     */
    public Hologram renamedTo(HologramName newName) {
        return toBuilder().name(Objects.requireNonNull(newName, "newName")).build();
    }

    /** A copy with {@code line} appended after the current last line. */
    public Hologram withLineAppended(HologramLine line) {
        return withContent(content.withLineAppended(line));
    }

    /** A copy with the line at {@code index} replaced by {@code line}; rejects an out-of-range index. */
    public Hologram withLineReplaced(int index, HologramLine line) {
        return withContent(content.withLineReplaced(index, line));
    }

    /**
     * A copy with {@code line} inserted <em>before</em> the line at {@code index} (so the inserted line takes that
     * position and the rest shift down by one); an {@code index} at or past the current size appends. A negative
     * index is rejected.
     */
    public Hologram withLineInserted(int index, HologramLine line) {
        return withContent(content.withLineInserted(index, line));
    }

    /**
     * A copy with the line at {@code index} removed; rejects an out-of-range index, and (for a TEXT hologram)
     * rejects removing the last remaining line. A text hologram must keep at least one line, so the caller deletes
     * the hologram instead.
     */
    public Hologram withLineRemoved(int index) {
        return withContent(content.withLineRemoved(index));
    }

    /** A copy switched to an ITEM hologram showing {@code newItemMaterial} (lines and styling kept). */
    public Hologram asItem(String newItemMaterial) {
        return withContent(content.asItem(newItemMaterial));
    }

    /** A copy switched to a BLOCK hologram showing {@code newBlockData} (lines and styling kept). */
    public Hologram asBlock(String newBlockData) {
        return withContent(content.asBlock(newBlockData));
    }

    /** A copy switched to a HEAD hologram showing the player head with base64 {@code newHeadTexture} (lines kept). */
    public Hologram asHead(String newHeadTexture) {
        return withContent(content.asHead(newHeadTexture));
    }

    /** A copy switched to an ENTITY hologram showing the frozen mob {@code newEntityType} (lines kept). */
    public Hologram asEntity(String newEntityType) {
        return withContent(content.asEntity(newEntityType));
    }

    /** A copy restyled with {@code newAppearance}, keeping everything else. */
    public Hologram withAppearance(Appearance newAppearance) {
        return toBuilder()
                .appearance(Objects.requireNonNull(newAppearance, "newAppearance"))
                .build();
    }

    /** A copy with a new {@link Visibility}, keeping everything else. */
    public Hologram withVisibility(Visibility newVisibility) {
        return toBuilder()
                .visibility(Objects.requireNonNull(newVisibility, "newVisibility"))
                .build();
    }

    /** A copy with a new {@link Rotation}, keeping everything else. */
    public Hologram withRotation(Rotation newRotation) {
        return toBuilder()
                .rotation(Objects.requireNonNull(newRotation, "newRotation"))
                .build();
    }

    /** A copy that re-renders every {@code ticks} ticks (0 = static); rejects a negative interval. */
    public Hologram withRefreshIntervalTicks(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("refreshIntervalTicks must not be negative: " + ticks);
        }
        return toBuilder().refreshIntervalTicks(ticks).build();
    }

    private Hologram withContent(HologramContent newContent) {
        HologramBuilder builder = toBuilder().content(newContent);
        if (pages != null && !newContent.lines().isEmpty()) {
            // Page 0 is the rendered content, so a line edit (or model switch that keeps the lines) flows into it,
            // keeping the page set and the content in step: otherwise a reload would revert the edit from page 0.
            List<HologramPage> synced = new java.util.ArrayList<>(pages);
            synced.set(0, HologramPage.of(newContent.lines()));
            builder.pages(synced);
        }
        return builder.build();
    }
}
