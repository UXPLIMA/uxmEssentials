package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpQuota;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpLimit;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * {@link PlayerwarpsPlaceholders} over the player-warps context's read ports: the {@link PlayerWarpRepository}
 * and the {@link PlayerWarpQuota} reducer. Built during playerwarps wiring from the same cached repository and
 * quota the {@code /pwarp} use cases hold, so the placeholder count and limit match what {@code /setpwarp}
 * enforces. The repository is the Caffeine read-cached one. The same cached set the {@code /pwarps} list and
 * the {@code /setpwarp} count check share, so the list read is a cache hit for a join-warmed owner rather than
 * a fresh query.
 *
 * <p>The limit is resolved unscoped (no world in hand on the placeholder surface) and an unlimited quota maps
 * to a negative value the resolver renders as the infinity marker. Every read is over the warps the player
 * owns; a player-warp the player does not own is never surfaced.
 */
@NullMarked
public final class RepositoryPlayerwarpsPlaceholders implements PlayerwarpsPlaceholders {

    private final PlayerWarpRepository repository;
    private final PlayerWarpQuota quota;

    public RepositoryPlayerwarpsPlaceholders(PlayerWarpRepository repository, PlayerWarpQuota quota) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.quota = Objects.requireNonNull(quota, "quota");
    }

    @Override
    public int count(PlayerRef who) {
        return repository.count(Objects.requireNonNull(who, "who"));
    }

    @Override
    public int limit(PlayerRef who) {
        PlayerWarpLimit limit = quota.resolve(Objects.requireNonNull(who, "who"), null);
        return limit.unlimited() ? -1 : limit.cap();
    }

    @Override
    public List<PlayerWarpView> list(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return repository.ownedBy(who).stream()
                .map(RepositoryPlayerwarpsPlaceholders::view)
                .toList();
    }

    @Override
    public Optional<PlayerWarpView> find(PlayerRef who, String name) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(name, "name");
        return repository.ownedBy(who).stream()
                .filter(warp -> warp.name().value().equalsIgnoreCase(name))
                .findFirst()
                .map(RepositoryPlayerwarpsPlaceholders::view);
    }

    private static PlayerWarpView view(PlayerWarp warp) {
        Position position = warp.location();
        return new PlayerWarpView(
                warp.name().value(),
                warp.owner().name(),
                position.world().name(),
                position.blockX(),
                position.blockY(),
                position.blockZ(),
                warp.visits().count());
    }
}
