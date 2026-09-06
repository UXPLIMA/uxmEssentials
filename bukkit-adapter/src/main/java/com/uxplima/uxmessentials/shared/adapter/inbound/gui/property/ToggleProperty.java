package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * A property whose click cycles through a small, fixed set of states. A boolean (two states) or a short enum
 *, and hands the chosen state to a setter. A left-click advances to the next state, a right-click steps back,
 * so the viewer can move either way through the cycle without re-opening anything. The new state is written
 * through the caller's setter off the tick thread via the shared {@link Scheduler}, then the editor is
 * redrawn so the value lore shows the new state.
 *
 * <p>The setter is the module's existing application use case wrapped as a {@link Consumer}; this property
 * holds no domain logic. The value-display function resolves a state to its locale display string (the caller
 * routes it through the message catalog), so no state name is ever an inline literal.
 *
 * @param <S> the state type cycled (e.g. {@link Boolean} or an enum)
 */
@NullMarked
public final class ToggleProperty<S> implements EditableProperty {

    private final MessageKey label;
    private final Material icon;
    private final List<S> states;
    private final Supplier<S> current;
    private final BiFunction<PlayerRef, S, String> display;
    private final Consumer<S> setter;
    private final Scheduler scheduler;

    public ToggleProperty(
            MessageKey label,
            Material icon,
            List<S> states,
            Supplier<S> current,
            BiFunction<PlayerRef, S, String> display,
            Consumer<S> setter,
            Scheduler scheduler) {
        this.label = Objects.requireNonNull(label, "label");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.states = List.copyOf(Objects.requireNonNull(states, "states"));
        if (this.states.size() < 2) {
            throw new IllegalArgumentException("a toggle needs at least two states, had " + this.states.size());
        }
        this.current = Objects.requireNonNull(current, "current");
        this.display = Objects.requireNonNull(display, "display");
        this.setter = Objects.requireNonNull(setter, "setter");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** A two-state on/off toggle: the common boolean case. */
    public static ToggleProperty<Boolean> ofBoolean(
            MessageKey label,
            Material icon,
            Supplier<Boolean> current,
            BiFunction<PlayerRef, Boolean, String> display,
            Consumer<Boolean> setter,
            Scheduler scheduler) {
        return new ToggleProperty<>(
                label, icon, List.of(Boolean.FALSE, Boolean.TRUE), current, display, setter, scheduler);
    }

    @Override
    public MessageKey label() {
        return label;
    }

    @Override
    public Material icon() {
        return icon;
    }

    @Override
    public String valueLore(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return display.apply(viewer, current.get());
    }

    @Override
    public void onClick(ClickContext context) {
        Objects.requireNonNull(context, "context");
        S next = step(context.rightClick() ? -1 : 1);
        scheduler.async(() -> {
            setter.accept(next);
            scheduler.onEntity(context.viewer(), context.reopen());
        });
    }

    private S step(int direction) {
        int index = states.indexOf(current.get());
        int base = index < 0 ? 0 : index;
        int size = states.size();
        int target = ((base + direction) % size + size) % size;
        return states.get(target);
    }
}
