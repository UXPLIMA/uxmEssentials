package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerUnjailed;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The online-only jail countdown (docs/02-concurrency.md, docs/09-deployment.md): a timed jail accrues online
 * time only, so the remainder is decremented only while the player is connected in the jail and frozen on
 * quit.
 *
 * <ul>
 *   <li><b>{@link #onJoin}</b>, re-apply the jail (the same offline-jail enforcement the login path uses):
 *       a still-active jail re-teleports the player into the jail world so they cannot escape by relogging.
 *       The caller records the join instant as the session start; nothing is decremented yet.
 *   <li><b>{@link #onTick}</b>. Burn the online time elapsed since {@code sessionStart} off an online-only
 *       remainder. If the remainder is exhausted the player is released (row deleted, teleported out,
 *       {@code PlayerUnjailed} published); otherwise the decremented remainder is persisted so a later quit
 *       freezes it. A permanent or wall-clock jail is left untouched: only the online-only form ticks here.
 * </ul>
 *
 * <p>Pure: the elapsed online span and the now instant are passed in, so the countdown is deterministic and
 * the domain never reads the wall clock.
 */
public final class JailCountdown {

    private final ModerationRepository repository;
    private final Sanctions sanctions;
    private final ModerationAudit audit;
    private final DomainEventPublisher events;
    private final Clock clock;

    public JailCountdown(
            ModerationRepository repository,
            Sanctions sanctions,
            ModerationAudit audit,
            DomainEventPublisher events,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Re-apply a still-active jail on join (offline-jail enforcement); returns true when the player is jailed. */
    public boolean onJoin(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (repository.loadJail(who) instanceof JailState.Active active && active.isActiveAt(clock.instant())) {
            sanctions.sendToJail(who, active.jail());
            return true;
        }
        return false;
    }

    /**
     * Advance an online-only sentence by {@code onlineElapsed} and release the player when the remainder is
     * exhausted. No-op for a permanent, wall-clock, or already-cleared jail.
     */
    public void onTick(PlayerRef who, Duration onlineElapsed) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(onlineElapsed, "onlineElapsed");
        if (!(repository.loadJail(who) instanceof JailState.Active active) || !active.isOnlineTimed()) {
            return;
        }
        JailState.Active advanced = active.tickOnline(onlineElapsed);
        if (advanced.isServed()) {
            release(who);
        } else {
            repository.saveJail(who, advanced);
        }
    }

    private void release(PlayerRef who) {
        Instant now = clock.instant();
        repository.saveJail(who, JailState.none());
        sanctions.releaseFromJail(who);
        events.publish(new PlayerUnjailed(who, now));
        // An online-only sentence served in full is released by the server, not an operator, so the audit
        // line is attributed to a stable system actor rather than any staff member.
        audit.unjailed(SYSTEM_ACTOR, who, true, java.util.Optional.of("served"));
    }

    private static final PlayerRef SYSTEM_ACTOR = PlayerRef.system("system");
}
