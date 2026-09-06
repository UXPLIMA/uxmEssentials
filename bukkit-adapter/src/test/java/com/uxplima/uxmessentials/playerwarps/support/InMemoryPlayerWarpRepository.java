package com.uxplima.uxmessentials.playerwarps.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A small in-memory {@link PlayerWarpRepository} test double on the surrogate-id model, shared by the player-warp
 * GUI and golden tests so they do not each re-declare the same fake. Warp names are globally unique, so it keys on
 * the name and assigns a fresh {@link PlayerWarpId} on the first save of a warp that has none, mirroring the real
 * store, so every stored warp carries an id and the use cases that resolve a warp's id (delete, visit) work against
 * it. Insertion order is preserved so a list read renders in the order warps were stored.
 */
public final class InMemoryPlayerWarpRepository implements PlayerWarpRepository {

    private final Map<String, PlayerWarp> byName = new LinkedHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    @Override
    public Optional<PlayerWarp> findByName(PlayerWarpName name) {
        return Optional.ofNullable(byName.get(name.value()));
    }

    @Override
    public Optional<PlayerWarp> findById(PlayerWarpId id) {
        return byName.values().stream()
                .filter(warp -> warp.id().filter(id::equals).isPresent())
                .findFirst();
    }

    @Override
    public List<PlayerWarp> ownedBy(PlayerRef owner) {
        List<PlayerWarp> owned = new ArrayList<>();
        for (PlayerWarp warp : byName.values()) {
            if (warp.owner().equals(owner)) {
                owned.add(warp);
            }
        }
        return List.copyOf(owned);
    }

    @Override
    public List<PlayerWarp> publicOwnedBy(PlayerRef owner) {
        return ownedBy(owner).stream()
                .filter(warp -> warp.status() == WarpStatus.ACTIVE && warp.access() == WarpAccess.PUBLIC)
                .toList();
    }

    @Override
    public List<PlayerWarp> all() {
        return List.copyOf(byName.values());
    }

    @Override
    public int count(PlayerRef owner) {
        return ownedBy(owner).size();
    }

    @Override
    public boolean existsByName(PlayerWarpName name) {
        return byName.containsKey(name.value());
    }

    @Override
    public PlayerWarpId save(PlayerWarp warp) {
        PlayerWarpId id = warp.id().orElseGet(() -> PlayerWarpId.of(ids.incrementAndGet()));
        byName.put(warp.name().value(), warp.id().isPresent() ? warp : warp.withId(id));
        return id;
    }

    @Override
    public void deleteById(PlayerWarpId id) {
        byName.values().removeIf(warp -> warp.id().filter(id::equals).isPresent());
    }

    @Override
    public void recordVisit(PlayerWarpId id) {
        // A visit is a durable, out-of-band counter bump in the real store; no test double reads it back, so this
        // stays a no-op rather than reconstructing the aggregate just to advance a count nothing asserts.
    }

    @Override
    public void updateRating(PlayerWarpId id, RatingSummary summary) {
        // Like the visit counter, the rating rollup is a durable side-write the GUI and golden tests never read back.
    }

    @Override
    public void refreshFavouriteCount(PlayerWarpId id) {
        // The favourite count is a durable side-write these tests never read back, so it stays a no-op.
    }

    @Override
    public Optional<List<PlayerWarp>> peekOwned(PlayerRef owner) {
        // Behave like a warm cache so a name-argument suggester test sees the owner's warps without blocking.
        return Optional.of(ownedBy(owner));
    }
}
