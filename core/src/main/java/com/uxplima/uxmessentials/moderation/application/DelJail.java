package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.JailLocationStore;
import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import com.uxplima.uxmessentials.moderation.domain.event.JailLocationRemoved;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /jail del <name>}: remove a DB-backed jail location, freeing its name. A name no stored jail exists at
 * is a not-found notice ({@code DELJAIL_NOT_FOUND}) and publishes nothing; a successful delete publishes
 * {@code JailLocationRemoved} attributed to the staff member and confirms with {@code DELJAIL_DELETED}. Only a
 * stored jail can be removed this way — a config-defined jail name lives in {@code moderation.conf}, not the
 * store, so a {@code /jail del} of such a name reports not-found (the operator edits the config to remove it).
 * The operator-only permission is enforced at the command gate.
 */
public final class DelJail {

    private final JailLocationStore store;
    private final Notifier notifier;
    private final ModerationAudit audit;
    private final DomainEventPublisher events;
    private final Clock clock;

    public DelJail(
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

    /** Delete the stored jail {@code rawName}, or report not-found when no stored jail exists at it. */
    public void delete(PlayerRef actor, String rawName) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(rawName, "rawName");
        String name = StoredJail.normalise(rawName);
        if (!store.delete(name)) {
            notifier.send(actor, ModerationMessageKey.DELJAIL_NOT_FOUND, Map.of("jail", name));
            return;
        }
        events.publish(new JailLocationRemoved(name, actor, clock.instant()));
        audit.jailLocationRemoved(actor, name);
        notifier.send(actor, ModerationMessageKey.DELJAIL_DELETED, Map.of("jail", name));
    }
}
