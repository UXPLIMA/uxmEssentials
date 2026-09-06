package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.NullMarked;

/**
 * The per-open state of a paginated entity list, parked on its {@link MenuHolder} on the list path only. Where a
 * spec menu re-derives its slots from a {@code MenuSpec}, an editor from an {@code EditorSpec}, a selector from its
 * {@link SelectorState} and a confirm from its {@link ConfirmState}, an entity list re-derives its slots from the
 * {@code EntityListSpec} and the already-snapshotted entities, so the holder carries the spec here and the list renderer
 * re-reads the entity snapshot fresh on every draw (matching the bespoke {@code EntityListView}'s
 * {@code entities.get()} per page).
 *
 * <p>It also owns the slot routing for one rendered page. An entity slot maps to the entity drawn there on the
 * current page; the click listener consults {@link #entityAt} to route a click into the spec's {@code onSelect} for
 * that entity. The previous/next nav slots are recorded so a page flip re-paginates the same holder, and the
 * optional create/action buttons map to a {@link Runnable} the listener runs as-is. Keeping these maps here, off
 * the spec {@code clickMap} and off the editor/selector/confirm maps, is what lets the one listener tell a list
 * apart from the other menu kinds without giving any of them a slot it has no item for.
 *
 * <p>The {@code spec} is held as {@link Object} so this runtime class needs no compile dependency on the public
 * {@code EntityListSpec} façade type (the list renderer, which already depends on that type, casts it back) and the
 * {@code runtime}→{@code menu} package edge stays acyclic.
 */
@NullMarked
public final class ListViewState {

    private final Object spec;

    private final Map<Integer, Object> entitySlots = new HashMap<>();

    private final Map<Integer, Runnable> buttonSlots = new HashMap<>();

    private OptionalInt prevSlot = OptionalInt.empty();

    private OptionalInt nextSlot = OptionalInt.empty();

    public ListViewState(Object spec) {
        this.spec = Objects.requireNonNull(spec, "spec");
    }

    /** The {@code EntityListSpec} this list was opened from, opaque here and cast back by the list renderer. */
    public Object spec() {
        return spec;
    }

    /** Drops every recorded slot ahead of a re-render so a stale entity or button can never be clicked. */
    public void clearSlots() {
        entitySlots.clear();
        buttonSlots.clear();
        prevSlot = OptionalInt.empty();
        nextSlot = OptionalInt.empty();
    }

    /** Records the entity the list renderer painted at {@code slot} on this page, so a later click routes to it. */
    public void recordEntity(int slot, Object entity) {
        Objects.requireNonNull(entity, "entity");
        entitySlots.put(slot, entity);
    }

    /** Records a plain button (create, action) at {@code slot}; its action runs as-is on a click. */
    public void recordButton(int slot, Runnable action) {
        Objects.requireNonNull(action, "action");
        buttonSlots.put(slot, action);
    }

    /** Records the previous/next nav slots so a click on either re-paginates the same holder. */
    public void recordNav(int prevSlot, int nextSlot) {
        this.prevSlot = OptionalInt.of(prevSlot);
        this.nextSlot = OptionalInt.of(nextSlot);
    }

    /** The entity drawn at {@code slot} on the current page, or empty when the slot carries no entity icon. */
    public Optional<Object> entityAt(int slot) {
        return Optional.ofNullable(entitySlots.get(slot));
    }

    /** The plain-button action at {@code slot} (create or action), or empty when the slot carries no such button. */
    public Optional<Runnable> buttonAt(int slot) {
        return Optional.ofNullable(buttonSlots.get(slot));
    }

    /** Whether {@code slot} is the previous-page nav slot. */
    public boolean isPrev(int slot) {
        return prevSlot.isPresent() && prevSlot.getAsInt() == slot;
    }

    /** Whether {@code slot} is the next-page nav slot. */
    public boolean isNext(int slot) {
        return nextSlot.isPresent() && nextSlot.getAsInt() == slot;
    }
}
