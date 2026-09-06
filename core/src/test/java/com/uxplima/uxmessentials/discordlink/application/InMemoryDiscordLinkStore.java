package com.uxplima.uxmessentials.discordlink.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.discordlink.application.port.DiscordLinkStore;
import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.DiscordId;
import com.uxplima.uxmessentials.discordlink.domain.LinkCode;
import com.uxplima.uxmessentials.discordlink.domain.PendingLink;

/**
 * An in-memory {@link DiscordLinkStore} for the pure use-case tests: two maps keyed by player, mirroring the
 * jOOQ store's invariants. One pending row per player (the code unique across rows), one confirmed row per
 * player (the Discord id unique across rows), and a confirm that binds and clears the player's pending row in
 * one step. No persistence, no concurrency; the jOOQ adapter's own round-trip test covers the durable side.
 */
final class InMemoryDiscordLinkStore implements DiscordLinkStore {

    private final Map<UUID, PendingLink> pending = new HashMap<>();
    private final Map<UUID, ConfirmedLink> confirmed = new HashMap<>();

    @Override
    public void savePending(PendingLink row) {
        pending.put(row.player(), row);
    }

    @Override
    public Optional<PendingLink> findPendingByCode(LinkCode code) {
        return pending.values().stream().filter(row -> row.code().equals(code)).findFirst();
    }

    @Override
    public void deletePending(UUID player) {
        pending.remove(player);
    }

    @Override
    public void confirm(ConfirmedLink link) {
        confirmed.put(link.player(), link);
        pending.remove(link.player());
    }

    @Override
    public Optional<ConfirmedLink> findByPlayer(UUID player) {
        return Optional.ofNullable(confirmed.get(player));
    }

    @Override
    public Optional<ConfirmedLink> findByDiscordId(DiscordId discordId) {
        return confirmed.values().stream()
                .filter(link -> link.discordId().equals(discordId))
                .findFirst();
    }

    @Override
    public boolean unlink(UUID player) {
        return confirmed.remove(player) != null;
    }

    /** Test seam: the player's pending row, if any. */
    Optional<PendingLink> pendingOf(UUID player) {
        return Optional.ofNullable(pending.get(player));
    }
}
