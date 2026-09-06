package com.uxplima.uxmessentials.presence.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.presence.application.port.PresenceAudience;
import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.presence.domain.event.ReturnedFromAfk;
import com.uxplima.uxmessentials.presence.domain.event.WentAfk;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The AFK transition use case, covering both entry points (docs/10-feature-modules.md §15.8): the manual
 * {@code /afk [reason]} toggle a player runs, and the automatic flip the idle sweep performs when a player has
 * been idle past the threshold. Both route through the {@link PresenceStore}'s atomic {@code compute} so the
 * sync activity listeners and the async sweep never lose an update, publish the matching {@link WentAfk} /
 * {@link ReturnedFromAfk} event, and broadcast the away/back line to the online audience.
 *
 * <p>{@code /afk} is a toggle: running it while active goes AFK (with the optional reason), running it while
 * already AFK returns the player. The sweep only ever marks AFK, never the reverse, because returning is the
 * activity listener's job.
 */
public final class MarkAfk {

    private final PresenceStore store;
    private final PresenceAudience audience;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public MarkAfk(
            PresenceStore store,
            PresenceAudience audience,
            Notifier notifier,
            DomainEventPublisher events,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Toggle {@code who}'s AFK state from {@code /afk [reason]}; returns the new AFK flag. */
    public boolean toggle(PlayerRef who, Optional<String> reason) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(reason, "reason");
        boolean wasAfk = store.current(who).afk();
        return wasAfk ? clear(who) : enter(who, reason, false);
    }

    /**
     * Mark {@code who} AFK automatically from the idle sweep. Returns {@code true} when this call flipped them
     * (so the caller may apply a side-effect once); a player already AFK is left unchanged.
     */
    public boolean markAuto(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (store.current(who).afk()) {
            return false;
        }
        enter(who, Optional.empty(), true);
        return true;
    }

    private boolean enter(PlayerRef who, Optional<String> reason, boolean automatic) {
        store.update(who, presence -> presence.markAfk(reason));
        events.publish(new WentAfk(who, reason, automatic, clock.instant()));
        announceAway(who, reason);
        return true;
    }

    private boolean clear(PlayerRef who) {
        store.update(who, presence -> presence.cleared(clock.instant()));
        events.publish(new ReturnedFromAfk(who, clock.instant()));
        announceBack(who);
        return false;
    }

    private void announceAway(PlayerRef who, Optional<String> reason) {
        notifier.send(
                who,
                reason.isPresent() ? PresenceMessageKey.AFK_NOW_REASON : PresenceMessageKey.AFK_NOW,
                Map.of("reason", reason.orElse("")));
        PresenceMessageKey key = reason.isPresent()
                ? PresenceMessageKey.AFK_BROADCAST_AWAY_REASON
                : PresenceMessageKey.AFK_BROADCAST_AWAY;
        Map<String, String> placeholders = Map.of("player", who.name(), "reason", reason.orElse(""));
        broadcast(who, key, placeholders);
    }

    private void announceBack(PlayerRef who) {
        notifier.send(who, PresenceMessageKey.AFK_RETURNED);
        broadcast(who, PresenceMessageKey.AFK_BROADCAST_BACK, Map.of("player", who.name()));
    }

    private void broadcast(PlayerRef subject, PresenceMessageKey key, Map<String, String> placeholders) {
        List<PlayerRef> online = audience.online();
        for (PlayerRef viewer : online) {
            if (!viewer.equals(subject)) {
                notifier.send(viewer, key, placeholders);
            }
        }
    }
}
