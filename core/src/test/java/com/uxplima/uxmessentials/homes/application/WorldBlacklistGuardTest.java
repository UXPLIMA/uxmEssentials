package com.uxplima.uxmessentials.homes.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * {@link WorldBlacklistGuard} must block a home in a disabled world, allow a home in a non-disabled world,
 * and match world names case-insensitively regardless of how the config or the world name is cased.
 */
class WorldBlacklistGuardTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final HomeSlot SLOT = HomeSlot.of(0);

    @Test
    void blacklistedWorldReturnsWorldDisabledError() {
        WorldBlacklistGuard guard = new WorldBlacklistGuard(Set.of("world_nether"));
        Position at = Position.of(new WorldRef(UUID.randomUUID(), "world_nether"), 0, 64, 0);

        Result<Unit, HomeError> result = guard.check(OWNER, SLOT, at);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(HomeError.WORLD_DISABLED);
    }

    @Test
    void allowedWorldReturnsOk() {
        WorldBlacklistGuard guard = new WorldBlacklistGuard(Set.of("world_nether"));
        Position at = Position.of(new WorldRef(UUID.randomUUID(), "world"), 0, 64, 0);

        Result<Unit, HomeError> result = guard.check(OWNER, SLOT, at);

        assertThat(result.isOk()).isTrue();
    }

    @Test
    void matchingIsCaseInsensitive() {
        // Config entry is uppercase; world name from the server is lowercase, must still match.
        WorldBlacklistGuard guard = new WorldBlacklistGuard(Set.of("WORLD_NETHER"));
        Position at = Position.of(new WorldRef(UUID.randomUUID(), "world_nether"), 0, 64, 0);

        Result<Unit, HomeError> result = guard.check(OWNER, SLOT, at);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(HomeError.WORLD_DISABLED);
    }

    @Test
    void emptyBlacklistAllowsEverything() {
        WorldBlacklistGuard guard = new WorldBlacklistGuard(Set.of());
        Position at = Position.of(new WorldRef(UUID.randomUUID(), "any_world"), 0, 64, 0);

        Result<Unit, HomeError> result = guard.check(OWNER, SLOT, at);

        assertThat(result.isOk()).isTrue();
    }
}
