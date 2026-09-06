package com.uxplima.uxmessentials.homes.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.World;

import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * MockBukkit coverage of {@link SafeLocationGuard}. Blocks are set explicitly so each scenario
 * exercises a specific rejection or allow path. MockBukkit's {@code BlockMock.isSolid()} delegates to
 * {@code Material.isSolid()} which works correctly; {@code isPassable()} is unimplemented, so the
 * guard uses {@code isSolid()} for both feet and head checks.
 */
class SafeLocationGuardTest {

    private static final int X = 0;
    private static final int Y = 64;
    private static final int Z = 0;
    private static final int MIDAIR_DEPTH = 5;

    private ServerMock server;
    private World world;
    private WorldRef worldRef;
    private PlayerRef owner;
    private HomeSlot slot;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        worldRef = new WorldRef(world.getUID(), world.getName());
        owner = new PlayerRef(UUID.randomUUID(), "Tester");
        slot = HomeSlot.of(0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void allowsWhenCheckIsDisabled() {
        // Place STONE at feet so that the location would normally be blocked.
        world.getBlockAt(X, Y, Z).setType(Material.STONE);
        SafeLocationGuard guard = guard(/* blockUnsafe= */ false, /* considerMidair= */ true);

        Result<Unit, HomeError> result = guard.check(owner, slot, position(Y));

        assertThat(result.isOk()).isTrue();
    }

    @Test
    void allowsSolidGroundUnderFeetWithAirAtFeetAndHead() {
        // Y-1 is solid ground, Y and Y+1 are air (MockBukkit default).
        world.getBlockAt(X, Y - 1, Z).setType(Material.STONE);
        SafeLocationGuard guard = guard(true, true);

        Result<Unit, HomeError> result = guard.check(owner, slot, position(Y));

        assertThat(result.isOk()).isTrue();
    }

    @Test
    void rejectsSolidBlockAtFeet() {
        // STONE directly at the feet position: player would be inside a block.
        world.getBlockAt(X, Y, Z).setType(Material.STONE);
        SafeLocationGuard guard = guard(true, false);

        Result<Unit, HomeError> result = guard.check(owner, slot, position(Y));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(HomeError.UNSAFE_LOCATION);
    }

    @Test
    void rejectsSolidBlockAtHead() {
        // Y is air, Y+1 is STONE: head would be inside a block.
        world.getBlockAt(X, Y + 1, Z).setType(Material.STONE);
        SafeLocationGuard guard = guard(true, false);

        Result<Unit, HomeError> result = guard.check(owner, slot, position(Y));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(HomeError.UNSAFE_LOCATION);
    }

    @Test
    void rejectsLavaDirectlyBelowFeet() {
        // Y-1 is LAVA, lethal underfoot.
        world.getBlockAt(X, Y - 1, Z).setType(Material.LAVA);
        SafeLocationGuard guard = guard(true, /* considerMidair= */ false);

        Result<Unit, HomeError> result = guard.check(owner, slot, position(Y));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(HomeError.UNSAFE_LOCATION);
    }

    @Test
    void rejectsFireDirectlyBelowFeet() {
        world.getBlockAt(X, Y - 1, Z).setType(Material.FIRE);
        SafeLocationGuard guard = guard(true, /* considerMidair= */ false);

        Result<Unit, HomeError> result = guard.check(owner, slot, position(Y));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(HomeError.UNSAFE_LOCATION);
    }

    @Test
    void rejectsFloatingWithNoGroundInRange() {
        // All blocks within MIDAIR_DEPTH below Y are AIR (MockBukkit default): player is floating.
        SafeLocationGuard guard = guard(true, /* considerMidair= */ true);

        Result<Unit, HomeError> result = guard.check(owner, slot, position(Y));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(HomeError.UNSAFE_LOCATION);
    }

    @Test
    void allowsWhenMidairCheckDisabledEvenWithNoGround() {
        // All blocks below Y are AIR, but the mid-air guard is off.
        SafeLocationGuard guard = guard(true, /* considerMidair= */ false);

        Result<Unit, HomeError> result = guard.check(owner, slot, position(Y));

        // No lava/fire below, no solid at feet/head, midair off → ok.
        assertThat(result.isOk()).isTrue();
    }

    @Test
    void allowsWhenSolidGroundJustAtEdgeOfDepth() {
        // Solid block exactly at Y - MIDAIR_DEPTH (the deepest the scan reaches).
        world.getBlockAt(X, Y - MIDAIR_DEPTH, Z).setType(Material.STONE);
        SafeLocationGuard guard = guard(true, true);

        Result<Unit, HomeError> result = guard.check(owner, slot, position(Y));

        assertThat(result.isOk()).isTrue();
    }

    private SafeLocationGuard guard(boolean blockUnsafe, boolean considerMidair) {
        return new SafeLocationGuard(server, blockUnsafe, considerMidair, MIDAIR_DEPTH);
    }

    private Position position(int y) {
        return Position.of(worldRef, X, y, Z);
    }
}
