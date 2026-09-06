package com.uxplima.uxmessentials.discordlink.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.discordlink.application.port.DiscordLinkStore;
import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.DiscordLinkError;
import com.uxplima.uxmessentials.discordlink.domain.event.AccountUnlinked;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Removes a player's confirmed Discord binding ({@code /discordunlink}). Returns success when a binding existed
 * and was removed, or {@code NOT_LINKED} when the player had none, so the command can tell the player whether
 * anything was actually unbound.
 *
 * <p>The binding is read before it is removed, because the fact published afterwards carries the Discord account
 * that was bound and by then there is nowhere left to look it up.
 */
public final class Unlink {

    private final DiscordLinkStore store;
    private final DomainEventPublisher events;

    public Unlink(DiscordLinkStore store, DomainEventPublisher events) {
        this.store = Objects.requireNonNull(store, "store");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Unbind {@code player}; success when a binding was removed, {@code NOT_LINKED} when there was none. */
    public Result<Unit, DiscordLinkError> unlink(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        Optional<ConfirmedLink> bound = store.findByPlayer(player.uuid());
        if (!store.unlink(player.uuid())) {
            return Result.err(DiscordLinkError.NOT_LINKED);
        }
        bound.ifPresent(link -> events.publish(new AccountUnlinked(player, link.discordId())));
        return Result.ok();
    }
}
