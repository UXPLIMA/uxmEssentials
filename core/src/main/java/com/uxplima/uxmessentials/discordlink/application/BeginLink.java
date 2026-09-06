package com.uxplima.uxmessentials.discordlink.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.random.RandomGenerator;

import com.uxplima.uxmessentials.discordlink.application.port.DiscordLinkStore;
import com.uxplima.uxmessentials.discordlink.domain.LinkCode;
import com.uxplima.uxmessentials.discordlink.domain.PendingLink;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Issues a fresh one-time link code for a player. It generates a code from the unambiguous alphabet, retries a
 * bounded number of times if the drawn code collides with another player's outstanding code, and upserts the
 * player's pending row with an expiry {@code ttl} from now, so a second {@code /discordlink} simply replaces
 * the player's previous code rather than accumulating rows.
 *
 * <p>Pure application code: it returns the issued {@link LinkCode} for the command adapter to render through a
 * {@code MessageKey}; it sends no message itself.
 */
public final class BeginLink {

    private static final int MAX_ATTEMPTS = 5;

    private final DiscordLinkStore store;
    private final Clock clock;
    private final RandomGenerator rng;
    private final Duration ttl;

    public BeginLink(DiscordLinkStore store, Clock clock, RandomGenerator rng, Duration ttl) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.rng = Objects.requireNonNull(rng, "rng");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    /** Generate and persist a fresh code for {@code player}, returning it for the adapter to render. */
    public LinkCode begin(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        Instant expiresAt = clock.instant().plus(ttl);
        LinkCode code = freshCode();
        store.savePending(new PendingLink(player.uuid(), code, expiresAt));
        return code;
    }

    private LinkCode freshCode() {
        LinkCode code = LinkCode.generate(rng);
        for (int attempt = 1; attempt < MAX_ATTEMPTS && collides(code); attempt++) {
            code = LinkCode.generate(rng);
        }
        return code;
    }

    private boolean collides(LinkCode code) {
        return store.findPendingByCode(code).isPresent();
    }
}
