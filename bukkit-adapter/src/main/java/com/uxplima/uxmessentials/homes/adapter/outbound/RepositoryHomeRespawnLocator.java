package com.uxplima.uxmessentials.homes.adapter.outbound;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.application.HomeRespawnLocator;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link HomeRespawnLocator} backed by the home repository's non-blocking {@link HomeRepository#peek}
 * cache read. On a cache hit it returns the owner's lowest-slot home position; on a cold miss or an owner
 * with no homes it returns empty so the respawn chain falls through to its next step. It never calls
 * {@link HomeRepository#load}. The respawn listener runs on the player's region thread inside the respawn
 * event, where a disk read is forbidden.
 */
@NullMarked
public final class RepositoryHomeRespawnLocator implements HomeRespawnLocator {

    private final HomeRepository repository;

    public RepositoryHomeRespawnLocator(HomeRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Optional<Position> respawnHome(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return repository.peek(who).flatMap(homes -> lowestSlot(homes).map(Home::location));
    }

    private static Optional<Home> lowestSlot(List<Home> homes) {
        return homes.stream().min(Comparator.comparingInt(home -> home.slot().index()));
    }
}
