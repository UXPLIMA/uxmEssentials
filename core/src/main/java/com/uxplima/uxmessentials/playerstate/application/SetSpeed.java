package com.uxplima.uxmessentials.playerstate.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerStateStore;
import com.uxplima.uxmessentials.playerstate.application.port.StateReconciler;
import com.uxplima.uxmessentials.playerstate.domain.PlayerStateSnapshot;
import com.uxplima.uxmessentials.playerstate.domain.SpeedValue;
import com.uxplima.uxmessentials.playerstate.domain.event.SpeedChanged;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The {@code /speed} family. {@code /speed <value> [player]} sets both walk and fly speed, while
 * {@code /walkspeed} and {@code /flyspeed} set each independently. The snapshot is updated through the
 * {@link PlayerStateStore}, the live player's walk/fly multiplier is set by the {@link StateReconciler} on the
 * owning region thread, a {@link SpeedChanged} event is published per affected speed, and the actor and
 * subject are notified. The {@code 0..10} argument is parsed to a {@link SpeedValue} (clamped) in the adapter.
 */
public final class SetSpeed {

    private final PlayerStateStore store;
    private final StateReconciler reconciler;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public SetSpeed(
            PlayerStateStore store,
            StateReconciler reconciler,
            Notifier notifier,
            DomainEventPublisher events,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Set {@code subject}'s walk speed on behalf of {@code actor}. */
    public void setWalk(PlayerRef actor, PlayerRef subject, SpeedValue value) {
        apply(actor, subject, value, SpeedChanged.Kind.WALK);
    }

    /** Set {@code subject}'s fly speed on behalf of {@code actor}. */
    public void setFly(PlayerRef actor, PlayerRef subject, SpeedValue value) {
        apply(actor, subject, value, SpeedChanged.Kind.FLY);
    }

    /** Set both {@code subject}'s walk and fly speed on behalf of {@code actor} ({@code /speed}). */
    public void setBoth(PlayerRef actor, PlayerRef subject, SpeedValue value) {
        apply(actor, subject, value, SpeedChanged.Kind.WALK);
        apply(actor, subject, value, SpeedChanged.Kind.FLY);
    }

    private void apply(PlayerRef actor, PlayerRef subject, SpeedValue value, SpeedChanged.Kind kind) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(value, "value");
        PlayerStateSnapshot updated = store.update(subject, current -> mutate(current, value, kind));
        reconciler.reconcile(subject, updated);
        events.publish(new SpeedChanged(subject, actor, kind, value, clock.instant()));
        notify(actor, subject, value, kind);
    }

    private static PlayerStateSnapshot mutate(PlayerStateSnapshot current, SpeedValue value, SpeedChanged.Kind kind) {
        return kind == SpeedChanged.Kind.WALK ? current.withWalkSpeed(value) : current.withFlySpeed(value);
    }

    private void notify(PlayerRef actor, PlayerRef subject, SpeedValue value, SpeedChanged.Kind kind) {
        String amount = formatScale(value.scale());
        boolean walk = kind == SpeedChanged.Kind.WALK;
        if (actor.equals(subject)) {
            notifier.send(
                    actor,
                    walk ? PlayerstateMessageKey.SPEED_WALK_SET : PlayerstateMessageKey.SPEED_FLY_SET,
                    Map.of("speed", amount));
            return;
        }
        notifier.send(
                actor,
                walk ? PlayerstateMessageKey.SPEED_WALK_SET_OTHER : PlayerstateMessageKey.SPEED_FLY_SET_OTHER,
                Map.of("speed", amount, "player", subject.name()));
        notifier.send(
                subject,
                walk ? PlayerstateMessageKey.SPEED_WALK_SET : PlayerstateMessageKey.SPEED_FLY_SET,
                Map.of("speed", amount));
    }

    private static String formatScale(double scale) {
        if (scale == Math.rint(scale)) {
            return Long.toString((long) scale);
        }
        return Double.toString(scale);
    }
}
