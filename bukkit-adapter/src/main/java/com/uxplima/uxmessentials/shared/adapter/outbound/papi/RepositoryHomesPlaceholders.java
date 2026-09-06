package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.HomeQuota;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeLimit;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * {@link HomesPlaceholders} over the homes context's read ports: the {@link HomeRepository} and the {@link
 * HomeQuota} reducer. Built during homes wiring from the same repository and quota the {@code /home} use
 * cases hold, so the placeholder count and limit match what {@code /sethome} enforces. The repository is the
 * Caffeine read-cached one. The same cached set {@code /home} and the {@code /sethome} count check share, so
 * the list read is a cache hit for an online (join-warmed) player rather than a fresh query.
 *
 * <p>The limit is resolved unscoped (no world in hand on the placeholder surface) and an unlimited quota maps
 * to a negative value the resolver renders as the infinity marker. A home's display name is its player-set
 * label when present, otherwise the 1-based slot number, matching what the {@code /home} list shows.
 */
@NullMarked
public final class RepositoryHomesPlaceholders implements HomesPlaceholders {

    private final HomeRepository repository;
    private final HomeQuota quota;

    public RepositoryHomesPlaceholders(HomeRepository repository, HomeQuota quota) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.quota = Objects.requireNonNull(quota, "quota");
    }

    @Override
    public int count(PlayerRef who) {
        return repository.count(Objects.requireNonNull(who, "who"));
    }

    @Override
    public int limit(PlayerRef who) {
        HomeLimit limit = quota.resolve(Objects.requireNonNull(who, "who"), null);
        return limit.unlimited() ? -1 : limit.cap();
    }

    @Override
    public List<HomeView> list(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return repository.load(who).all().stream()
                .map(RepositoryHomesPlaceholders::view)
                .toList();
    }

    private static HomeView view(Home home) {
        Position position = home.location();
        String name = home.label()
                .map(label -> label.value())
                .orElseGet(() -> Integer.toString(home.slot().displayNumber()));
        return new HomeView(name, position.world().name(), position.blockX(), position.blockY(), position.blockZ());
    }
}
