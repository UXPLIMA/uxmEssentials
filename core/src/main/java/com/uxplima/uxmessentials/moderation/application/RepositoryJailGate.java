package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;

/**
 * The real {@link JailGate} the moderation context provides for the teleport context to consume. It reads the
 * live jail state from the {@link ModerationRepository} and answers "is this player jailed right now" against
 * the injected {@link Clock}, so a wall-clock jail that has expired no longer holds. An online-only jail is
 * active until its remainder is exhausted; the join/quit tick advances that remainder, not this gate.
 *
 * <p>This is the implementation that replaces the {@code JailGate.NEVER} placeholder teleport binds until
 * moderation lands. It lives in {@code application} (pure) and imports only the teleport <em>port</em> and
 * the moderation repository port, no adapter type, so the soft couple stays a contract, not a hard edge.
 */
public final class RepositoryJailGate implements JailGate {

    private final ModerationRepository repository;
    private final Clock clock;

    public RepositoryJailGate(ModerationRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean isJailed(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return repository.loadJail(who).isActiveAt(clock.instant());
    }
}
