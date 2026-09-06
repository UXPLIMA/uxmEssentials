package com.uxplima.uxmessentials.playerstate.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerStateStore;
import com.uxplima.uxmessentials.playerstate.application.port.StateReconciler;
import com.uxplima.uxmessentials.playerstate.domain.PlayerStateSnapshot;
import com.uxplima.uxmessentials.playerstate.domain.event.GodToggled;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /god [player]}: flip a player's damage-immunity flag. The snapshot is mutated atomically through the
 * {@link PlayerStateStore} ({@code update} runs the pure {@code toggleGod}), the new value is pushed to the
 * live player by the {@link StateReconciler} on the player's owning region thread, the {@link GodToggled}
 * event is published, and both the actor and, for a staff toggle, the subject are notified.
 *
 * <p>A self-toggle passes the same ref as actor and subject; the {@code .others} target form passes the
 * staff member as actor and the affected player as subject, so the subject sees the change and the staff
 * member sees the confirmation.
 */
public final class ToggleGod {

    private final PlayerStateStore store;
    private final StateReconciler reconciler;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public ToggleGod(
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

    /** Toggle {@code who}'s own god flag. */
    public boolean toggle(PlayerRef who) {
        return toggleFor(who, who);
    }

    /** Toggle {@code subject}'s god flag on behalf of {@code actor} (a staff toggle when they differ). */
    public boolean toggleFor(PlayerRef actor, PlayerRef subject) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        PlayerStateSnapshot updated = store.update(subject, PlayerStateSnapshot::toggleGod);
        reconciler.reconcile(subject, updated);
        events.publish(new GodToggled(subject, actor, updated.god(), clock.instant()));
        notify(actor, subject, updated.god());
        return updated.god();
    }

    private void notify(PlayerRef actor, PlayerRef subject, boolean enabled) {
        boolean self = actor.equals(subject);
        if (self) {
            notifier.send(actor, enabled ? PlayerstateMessageKey.GOD_ON : PlayerstateMessageKey.GOD_OFF);
            return;
        }
        Map<String, String> player = Map.of("player", subject.name());
        notifier.send(
                actor, enabled ? PlayerstateMessageKey.GOD_OTHER_ON : PlayerstateMessageKey.GOD_OTHER_OFF, player);
        notifier.send(subject, enabled ? PlayerstateMessageKey.GOD_ON : PlayerstateMessageKey.GOD_OFF);
    }
}
