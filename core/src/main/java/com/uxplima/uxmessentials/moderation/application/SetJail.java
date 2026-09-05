package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.JailLocationStore;
import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import com.uxplima.uxmessentials.moderation.domain.event.JailLocationDefined;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * {@code /setjail <name>}: save the staff member's current position as a named jail in the DB-backed
 * {@link JailLocationStore}, or re-anchor an existing one of the same name in place. The store is keyed by
 * name, so this is an upsert and always succeeds — a brand-new name and a redefinition both publish
 * {@code JailLocationDefined} and confirm with {@code SETJAIL_SAVED}. The stored jail then merges with the
 * config jails behind the combined directory, so {@code /jail <player> <name>} accepts it and the sanction
 * adapter resolves its location. The operator-only permission is enforced at the command gate.
 */
public final class SetJail {

    private final JailLocationStore store;
    private final Notifier notifier;
    private final ModerationAudit audit;
    private final DomainEventPublisher events;
    private final Clock clock;

    public SetJail(
            JailLocationStore store,
            Notifier notifier,
            ModerationAudit audit,
            DomainEventPublisher events,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Save (or re-anchor) the jail {@code rawName} at {@code at}, attributing it to {@code actor}. */
    public void set(PlayerRef actor, String rawName, Position at) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(rawName, "rawName");
        Objects.requireNonNull(at, "at");
        StoredJail jail = StoredJail.of(rawName, at);
        store.save(jail);
        events.publish(new JailLocationDefined(jail.name(), actor, clock.instant()));
        audit.jailLocationDefined(actor, jail.name());
        notifier.send(actor, ModerationMessageKey.SETJAIL_SAVED, Map.of("jail", jail.name()));
    }
}
