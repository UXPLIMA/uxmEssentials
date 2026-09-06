package com.uxplima.uxmessentials.vote.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.VotePartyCounter;

/**
 * Serves the {@code /voteparty} display: the current accumulated count against the effective threshold,
 * and the number of votes still needed. Reads the durable counter and threshold override through the
 * repository so the display stays accurate under escalation. Showing the escalated threshold when an
 * override is stored, the configured base otherwise.
 */
public final class VotePartyStatus {

    private final VoteRepository repository;
    private final Notifier notifier;
    private final int baseThreshold;

    public VotePartyStatus(VoteRepository repository, Notifier notifier, int baseThreshold) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        if (baseThreshold < 1) {
            throw new IllegalArgumentException("baseThreshold must be at least one: " + baseThreshold);
        }
        this.baseThreshold = baseThreshold;
    }

    /** Show the current vote-party progress (count, effective threshold, remaining) to {@code viewer}. */
    public void show(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        int effectiveThreshold = effectiveThreshold();
        VotePartyCounter counter =
                new VotePartyCounter(Math.min(repository.partyCount(), effectiveThreshold), effectiveThreshold);
        int remaining = Math.max(0, effectiveThreshold - counter.count());
        notifier.send(
                viewer,
                VoteMessageKey.VOTEPARTY_STATUS,
                Map.of(
                        "count", Integer.toString(counter.count()),
                        "threshold", Integer.toString(effectiveThreshold),
                        "remaining", Integer.toString(remaining)));
    }

    private int effectiveThreshold() {
        int override = repository.thresholdOverride();
        return override > 0 ? override : baseThreshold;
    }
}
