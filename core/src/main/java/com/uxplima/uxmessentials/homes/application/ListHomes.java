package com.uxplima.uxmessentials.homes.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Reads an owner's homes for the {@code /homes} GUI to render. Homes are per-player with no access filter
 * every home in the owner's own set is theirs to teleport to, and are returned in slot-ascending order.
 * The chat-list feedback path is gone: the slot-grid GUI lists the homes now, so this use case owns only
 * the side-effect-free read.
 */
public final class ListHomes {

    private final HomeRepository repository;

    public ListHomes(HomeRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** The owner's homes, in slot-ascending order, with no side effect. */
    public List<Home> homesOf(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return repository.load(owner).all();
    }
}
