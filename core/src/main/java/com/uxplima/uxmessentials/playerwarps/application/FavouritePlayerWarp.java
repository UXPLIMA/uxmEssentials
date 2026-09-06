package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpFavouriteStore;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /pwarp favourite <name>} and its un-favourite counterpart: any viewer stars or un-stars a warp for their own
 * favourites list, and the warp's denormalised {@code favourite_count} is kept in step. Favourite is not role-gated
 * anyone who can see a warp may star it.
 *
 * <p>Each verb resolves the warp ({@link PlayerWarpError#NOT_FOUND}), no-ops with a distinct notice when it is already
 * in the wanted state, and otherwise mutates the favourite store then asks the repository to recompute the count from
 * the live favourite rows (a recompute, never a {@code +1}/{@code -1} bump) so a double-click that races the
 * membership check can never drift the stored count away from the true row count.
 */
public final class FavouritePlayerWarp {

    private final PlayerWarpRepository repository;
    private final WarpFavouriteStore favourites;
    private final Notifier notifier;

    public FavouritePlayerWarp(PlayerWarpRepository repository, WarpFavouriteStore favourites, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.favourites = Objects.requireNonNull(favourites, "favourites");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Star warp {@code name} for {@code actor}; a no-op notice when it is already a favourite. */
    public Result<Unit, PlayerWarpError> favourite(PlayerRef actor, PlayerWarpName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<PlayerWarpId> resolved = resolve(actor, name);
        if (resolved.isEmpty()) {
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        PlayerWarpId id = resolved.get();
        if (favourites.contains(actor.uuid(), id)) {
            notifier.send(actor, PlayerwarpsMessageKey.PWARP_ALREADY_FAVOURITED, Map.of("warp", name.value()));
            return Result.ok();
        }
        favourites.add(actor.uuid(), id);
        repository.refreshFavouriteCount(id);
        notifier.send(actor, PlayerwarpsMessageKey.PWARP_FAVOURITED, Map.of("warp", name.value()));
        return Result.ok();
    }

    /** Un-star warp {@code name} for {@code actor}; a no-op notice when it was not a favourite. */
    public Result<Unit, PlayerWarpError> unfavourite(PlayerRef actor, PlayerWarpName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<PlayerWarpId> resolved = resolve(actor, name);
        if (resolved.isEmpty()) {
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        PlayerWarpId id = resolved.get();
        if (!favourites.contains(actor.uuid(), id)) {
            notifier.send(actor, PlayerwarpsMessageKey.PWARP_NOT_FAVOURITED, Map.of("warp", name.value()));
            return Result.ok();
        }
        favourites.remove(actor.uuid(), id);
        repository.refreshFavouriteCount(id);
        notifier.send(actor, PlayerwarpsMessageKey.PWARP_UNFAVOURITED, Map.of("warp", name.value()));
        return Result.ok();
    }

    /** Resolve the warp's id, sending {@link PlayerWarpError#NOT_FOUND} to {@code actor} when no warp bears the name. */
    private Optional<PlayerWarpId> resolve(PlayerRef actor, PlayerWarpName name) {
        Optional<PlayerWarp> found = repository.findByName(name);
        if (found.isEmpty()) {
            notifier.send(actor, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Optional.empty();
        }
        return Optional.of(found.get().id().orElseThrow());
    }
}
