package com.uxplima.uxmessentials.vote.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;

/**
 * Admin use case: set the global vote-party counter to a specific value. Does not fire a party even
 * if the new count exceeds the threshold: use {@link AddPartyCount} when you want threshold-checking.
 */
public final class SetPartyCount {

    private final VoteRepository repository;
    private final Notifier notifier;

    public SetPartyCount(VoteRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * Set the party counter to {@code count} and notify {@code actor}. Also clears any stored
     * threshold override so the effective threshold reverts to the configured base, matching the
     * documented behaviour of {@code /voteparty set <n>} and making "reset escalation" an explicit
     * admin action rather than an implicit side effect.
     *
     * @param actor the admin running the command (receives confirmation)
     * @param count the new counter value (must not be negative)
     */
    public void set(PlayerRef actor, int count) {
        Objects.requireNonNull(actor, "actor");
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + count);
        }
        repository.setPartyCount(count);
        repository.setThresholdOverride(0);
        notifier.send(actor, VoteMessageKey.VOTEPARTY_COUNT_SET, Map.of("count", Integer.toString(count)));
    }
}
