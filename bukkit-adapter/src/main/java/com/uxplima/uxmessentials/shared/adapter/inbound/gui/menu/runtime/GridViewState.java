package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import org.jspecify.annotations.NullMarked;

/**
 * The per-open state of a slot-grid canvas, parked on its {@link MenuHolder} on the grid path only. Where a spec menu
 * routes a click through its {@code clickMap}, an editor through its {@link EditorState}, a selector through its
 * {@link SelectorState} and an entity list through its {@link ListViewState}, a grid routes through this: a content
 * cell maps to the menu slot it drew and whether that slot held an item, the previous/next nav slots re-paginate the
 * same holder, and each control-bar button maps to its handler. Keeping these maps here, off the spec {@code clickMap}
 * and off the other menu kinds' maps, is what lets the one listener tell a grid apart from every other menu kind
 * without giving any of them a slot it has no item for.
 *
 * <p>The {@code spec} and {@code handlers} are held as {@link Object} so this runtime class needs no compile
 * dependency on the public {@code GridSpec} / {@code GridHandlers} façade types (the grid renderer and the listener,
 * which already depend on those types, cast them back) and the {@code runtime}→{@code menu} package edge stays the
 * same shape every sibling state uses.
 */
@NullMarked
public final class GridViewState {

    private final Object spec;

    private final Object handlers;

    private final Map<Integer, ContentSlot> contentSlots = new HashMap<>();

    private final Map<Integer, Consumer<Player>> controlSlots = new HashMap<>();

    private OptionalInt prevSlot = OptionalInt.empty();

    private OptionalInt nextSlot = OptionalInt.empty();

    public GridViewState(Object spec, Object handlers) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.handlers = Objects.requireNonNull(handlers, "handlers");
    }

    /** The {@code GridSpec} this grid was opened from, opaque here and cast back by the grid renderer / listener. */
    public Object spec() {
        return spec;
    }

    /** The {@code GridHandlers} this grid was opened with, opaque here and cast back by the listener. */
    public Object handlers() {
        return handlers;
    }

    /** Drops every recorded slot ahead of a re-render so a stale cell, nav or control can never be clicked. */
    public void clearSlots() {
        contentSlots.clear();
        controlSlots.clear();
        prevSlot = OptionalInt.empty();
        nextSlot = OptionalInt.empty();
    }

    /** Records the content cell the renderer painted at {@code canvasSlot}: the menu slot it maps to and its fill. */
    public void recordContent(int canvasSlot, int menuSlot, boolean filled) {
        contentSlots.put(canvasSlot, new ContentSlot(menuSlot, filled));
    }

    /** Records a control-bar button at {@code canvasSlot}; its handler runs with the live viewer on a click. */
    public void recordControl(int canvasSlot, Consumer<Player> onClick) {
        Objects.requireNonNull(onClick, "onClick");
        controlSlots.put(canvasSlot, onClick);
    }

    /** Records the previous/next nav slots drawn this page (either may be absent on a first / last page). */
    public void recordNav(OptionalInt prevSlot, OptionalInt nextSlot) {
        this.prevSlot = Objects.requireNonNull(prevSlot, "prevSlot");
        this.nextSlot = Objects.requireNonNull(nextSlot, "nextSlot");
    }

    /** The content cell drawn at {@code canvasSlot}, or empty when the slot carries no content cell (a blocker). */
    public Optional<ContentSlot> contentAt(int canvasSlot) {
        return Optional.ofNullable(contentSlots.get(canvasSlot));
    }

    /** The control-bar handler at {@code canvasSlot}, or empty when the slot carries no control button. */
    public Optional<Consumer<Player>> controlAt(int canvasSlot) {
        return Optional.ofNullable(controlSlots.get(canvasSlot));
    }

    /** Whether {@code slot} is the previous-page nav slot drawn this page. */
    public boolean isPrev(int slot) {
        return prevSlot.isPresent() && prevSlot.getAsInt() == slot;
    }

    /** Whether {@code slot} is the next-page nav slot drawn this page. */
    public boolean isNext(int slot) {
        return nextSlot.isPresent() && nextSlot.getAsInt() == slot;
    }

    /** One painted content cell: the absolute menu slot it maps to and whether it held an item this draw. */
    public record ContentSlot(int menuSlot, boolean filled) {}
}
