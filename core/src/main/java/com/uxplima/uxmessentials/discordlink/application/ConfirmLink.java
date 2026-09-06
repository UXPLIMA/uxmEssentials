package com.uxplima.uxmessentials.discordlink.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.discordlink.application.port.DiscordLinkStore;
import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.DiscordId;
import com.uxplima.uxmessentials.discordlink.domain.DiscordLinkError;
import com.uxplima.uxmessentials.discordlink.domain.LinkCode;
import com.uxplima.uxmessentials.discordlink.domain.PendingLink;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * Redeems a link code presented in Discord, binding the presenting Discord account to the player who issued the
 * code. This is the use case the {@code /link} slash handler calls (on JDA's own thread, off any server tick)
 * through the {@code ServicesManager} seam.
 *
 * <p>The redemption is rejected when no pending row matches the code ({@code NO_PENDING_CODE}), when the code's
 * window has closed (the stale row is cleared and {@code CODE_EXPIRED} returned), or when the Discord account is
 * already bound to a <em>different</em> player ({@code DISCORD_ALREADY_LINKED}). Re-confirming the same code for
 * the same Discord account that is already bound to that same player is idempotent: it re-stamps the binding.
 * On success the binding is written and the pending row cleared in one store write.
 */
public final class ConfirmLink {

    private final DiscordLinkStore store;
    private final Clock clock;

    public ConfirmLink(DiscordLinkStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Redeem {@code code} for {@code discordId}; returns the bound link or a modelled failure. */
    public Result<ConfirmedLink, DiscordLinkError> confirm(LinkCode code, DiscordId discordId) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(discordId, "discordId");
        Optional<PendingLink> pending = store.findPendingByCode(code);
        if (pending.isEmpty()) {
            return Result.err(DiscordLinkError.NO_PENDING_CODE);
        }
        PendingLink row = pending.orElseThrow();
        Instant now = clock.instant();
        if (row.isExpired(now)) {
            store.deletePending(row.player());
            return Result.err(DiscordLinkError.CODE_EXPIRED);
        }
        if (boundElsewhere(discordId, row)) {
            return Result.err(DiscordLinkError.DISCORD_ALREADY_LINKED);
        }
        ConfirmedLink link = new ConfirmedLink(row.player(), discordId, now);
        store.confirm(link);
        return Result.ok(link);
    }

    private boolean boundElsewhere(DiscordId discordId, PendingLink row) {
        return store.findByDiscordId(discordId)
                .map(existing -> !existing.player().equals(row.player()))
                .orElse(false);
    }
}
