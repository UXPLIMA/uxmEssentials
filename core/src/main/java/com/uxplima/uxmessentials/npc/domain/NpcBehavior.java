package com.uxplima.uxmessentials.npc.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import org.jspecify.annotations.Nullable;

/**
 * The "how an NPC acts" half of the {@link Npc} aggregate: the single click command, whether the NPC rotates to
 * face nearby viewers, and the ordered list of typed actions a click runs. Grouping these behavioural fields into
 * one immutable value object keeps the {@link Npc} aggregate small while leaving the public surface unchanged
 * {@code Npc} delegates every behavioural transition and accessor here. A behavior is a value object: each
 * {@code with*} produces a new instance rather than mutating.
 *
 * <p>{@code clickCommand} is the raw command text run when a player clicks the NPC, or {@code null} for none
 * running it is an adapter concern, so the domain only carries the binding. {@code lookAtPlayer} controls whether
 * the fake player turns to face each nearby viewer; it defaults to {@code true} so a freshly created NPC tracks
 * players out of the box. {@code actions} is the ordered list of {@link ClickAction}s a click runs, the richer
 * mechanism alongside the single {@code clickCommand} (which still runs first). The list is copied defensively on
 * construction so the snapshot is immutable.
 *
 * <p>{@code interactionCooldownMillis} is the per-NPC override of the global click cooldown that swallows a
 * duplicate click: {@code 0} (the default) means "use the module-wide default", any positive value overrides it
 * for this NPC alone. It is carried here rather than as a global so a busy command NPC and a chatty greeter can
 * each tune their own debounce; the listener resolves the effective value. Negative values are rejected.
 */
public record NpcBehavior(
        @Nullable String clickCommand,
        boolean lookAtPlayer,
        List<ClickAction> actions,
        long interactionCooldownMillis) {

    public NpcBehavior {
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        if (interactionCooldownMillis < 0) {
            throw new IllegalArgumentException(
                    "interactionCooldownMillis must not be negative, was " + interactionCooldownMillis);
        }
    }

    /** The legacy three-field constructor, retained so existing callers default the per-NPC cooldown to global. */
    public NpcBehavior(@Nullable String clickCommand, boolean lookAtPlayer, List<ClickAction> actions) {
        this(clickCommand, lookAtPlayer, actions, 0L);
    }

    /** The default behavior for a freshly created NPC: no command, looking at players, no actions, global cooldown. */
    static NpcBehavior defaults() {
        return new NpcBehavior(null, true, List.of(), 0L);
    }

    NpcBehavior withClickCommand(@Nullable String newCommand) {
        return new NpcBehavior(newCommand, lookAtPlayer, actions, interactionCooldownMillis);
    }

    NpcBehavior withLookAtPlayer(boolean newLookAtPlayer) {
        return new NpcBehavior(clickCommand, newLookAtPlayer, actions, interactionCooldownMillis);
    }

    NpcBehavior withInteractionCooldownMillis(long newCooldownMillis) {
        return new NpcBehavior(clickCommand, lookAtPlayer, actions, newCooldownMillis);
    }

    NpcBehavior withActionAdded(ClickAction action) {
        Objects.requireNonNull(action, "action");
        List<ClickAction> updated = new ArrayList<>(actions);
        updated.add(action);
        return new NpcBehavior(clickCommand, lookAtPlayer, updated, interactionCooldownMillis);
    }

    NpcBehavior withActionRemovedAt(int index) {
        if (index < 0 || index >= actions.size()) {
            throw new IndexOutOfBoundsException("action index out of range: " + index);
        }
        List<ClickAction> updated = new ArrayList<>(actions);
        updated.remove(index);
        return new NpcBehavior(clickCommand, lookAtPlayer, updated, interactionCooldownMillis);
    }

    NpcBehavior withActionsCleared() {
        return new NpcBehavior(clickCommand, lookAtPlayer, List.of(), interactionCooldownMillis);
    }

    NpcBehavior withActionInsertedAt(int index, ClickAction action) {
        Objects.requireNonNull(action, "action");
        if (index < 0 || index > actions.size()) {
            throw new IndexOutOfBoundsException("action insert index out of range: " + index);
        }
        List<ClickAction> updated = new ArrayList<>(actions);
        updated.add(index, action);
        return new NpcBehavior(clickCommand, lookAtPlayer, updated, interactionCooldownMillis);
    }

    NpcBehavior withActionSetAt(int index, ClickAction action) {
        Objects.requireNonNull(action, "action");
        if (index < 0 || index >= actions.size()) {
            throw new IndexOutOfBoundsException("action index out of range: " + index);
        }
        List<ClickAction> updated = new ArrayList<>(actions);
        updated.set(index, action);
        return new NpcBehavior(clickCommand, lookAtPlayer, updated, interactionCooldownMillis);
    }

    NpcBehavior withActionMoved(int from, int to) {
        if (from < 0 || from >= actions.size() || to < 0 || to >= actions.size()) {
            throw new IndexOutOfBoundsException("action move index out of range: " + from + " -> " + to);
        }
        List<ClickAction> updated = new ArrayList<>(actions);
        ClickAction moved = updated.remove(from);
        updated.add(to, moved);
        return new NpcBehavior(clickCommand, lookAtPlayer, updated, interactionCooldownMillis);
    }

    boolean hasClickCommand() {
        return clickCommand != null && !clickCommand.isBlank();
    }

    boolean hasActions() {
        return !actions.isEmpty();
    }

    /** Whether this NPC overrides the module-wide click cooldown (a positive value), rather than using the default. */
    boolean hasInteractionCooldown() {
        return interactionCooldownMillis > 0;
    }
}
