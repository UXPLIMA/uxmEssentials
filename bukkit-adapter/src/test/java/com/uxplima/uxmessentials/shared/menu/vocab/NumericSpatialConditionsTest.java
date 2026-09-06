package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

import org.bukkit.Location;
import org.bukkit.World;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.NumericSpatialConditions;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Coverage of the numeric/spatial condition pack. {@code compare} is pure string/number work and is fired through the
 * registered {@link BiPredicate} against placeholders that resolve {@code %bal%}/{@code %name%} to per-test values;
 * the spatial gates ({@code is-near}, {@code cuboid}, {@code world}) read the {@link PlayerMock}'s own live location and
 * world. Every condition is asserted both ways, and the fail-closed contract is pinned by the offline-viewer and
 * malformed-argument cases. The two Bukkit-free helpers ({@code compareResolved}, {@code parseCoords}) are exercised
 * directly for the numeric/string branches and the coordinate grammar.
 */
class NumericSpatialConditionsTest {

    private ServerMock server;
    private World world;
    private PlayerMock viewer;
    private MenuBindings bindings;
    private String balValue = "0";
    private String nameValue = "";

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        viewer = server.addPlayer("Viewer");
        // A known location so the is-near/cuboid distances are deterministic regardless of the mock's default spawn.
        viewer.teleport(new Location(world, 100, 64, 100));
        bindings = new MenuBindings();
        bindings.placeholder("bal", ctx -> balValue);
        bindings.placeholder("name", ctx -> nameValue);
        NumericSpatialConditions.register(bindings, new NoopLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- compare -------------------------------------------------------------------------------------------------

    @Test
    void compareNumericThreshold() {
        balValue = "150";
        assertThat(test("compare", "%bal% >= 100")).isTrue();
        balValue = "50";
        assertThat(test("compare", "%bal% >= 100")).isFalse();
    }

    @Test
    void compareStringFallback() {
        nameValue = "Steve";
        assertThat(test("compare", "%name% = Steve")).isTrue();
        assertThat(test("compare", "%name% != Steve")).isFalse();
        assertThat(test("compare", "%name% == Steve")).isTrue();
    }

    @Test
    void compareFailsClosedOnAWrongTokenCount() {
        balValue = "150";
        assertThat(test("compare", "%bal% >=")).as("two tokens").isFalse();
        assertThat(test("compare", "1 2 3 4")).as("four tokens").isFalse();
    }

    // --- is-near -------------------------------------------------------------------------------------------------

    @Test
    void isNearIsTrueWithinTheRadius() {
        assertThat(test("is-near", "100 64 100 5")).as("exactly on the point").isTrue();
        assertThat(test("is-near", "103 64 100 5"))
                .as("three blocks away, radius five")
                .isTrue();
    }

    @Test
    void isNearIsFalseOutsideTheRadius() {
        assertThat(test("is-near", "200 64 100 5")).as("a hundred blocks away").isFalse();
    }

    @Test
    void isNearFailsClosedOnANegativeRadiusOrMalformedCoord() {
        assertThat(test("is-near", "100 64 100 -1")).as("negative radius").isFalse();
        assertThat(test("is-near", "x 64 100 5")).as("non-numeric coord").isFalse();
        assertThat(test("is-near", "100 64 100")).as("wrong token count").isFalse();
    }

    // --- cuboid --------------------------------------------------------------------------------------------------

    @Test
    void cuboidIsTrueInsideTheBoxRegardlessOfCornerOrder() {
        assertThat(test("cuboid", "90 60 90 110 70 110")).as("min corner first").isTrue();
        assertThat(test("cuboid", "110 70 110 90 60 90")).as("corners reversed").isTrue();
    }

    @Test
    void cuboidIsFalseOutsideTheBox() {
        assertThat(test("cuboid", "0 0 0 10 10 10")).isFalse();
    }

    @Test
    void cuboidFailsClosedOnMalformedGrammar() {
        assertThat(test("cuboid", "0 0 0 10 10")).as("five tokens").isFalse();
        assertThat(test("cuboid", "a b c d e f")).as("non-numeric coords").isFalse();
    }

    // --- world ---------------------------------------------------------------------------------------------------

    @Test
    void worldMatchesCaseInsensitively() {
        assertThat(test("world", "world")).isTrue();
        assertThat(test("world", "WORLD")).isTrue();
        assertThat(test("world", "nether")).isFalse();
    }

    @Test
    void worldFailsClosedOnABlankName() {
        assertThat(test("world", "")).isFalse();
    }

    // --- fail-closed ---------------------------------------------------------------------------------------------

    @Test
    void spatialConditionsFailClosedForAnOfflineViewer() {
        assertThat(testOffline("is-near", "100 64 100 5")).isFalse();
        assertThat(testOffline("cuboid", "90 60 90 110 70 110")).isFalse();
        assertThat(testOffline("world", "world")).isFalse();
    }

    // --- pure helpers --------------------------------------------------------------------------------------------

    @Test
    void compareResolvedNumericBranch() {
        assertThat(NumericSpatialConditions.compareResolved("150", ">=", "100")).isTrue();
        assertThat(NumericSpatialConditions.compareResolved("50", ">=", "100")).isFalse();
        assertThat(NumericSpatialConditions.compareResolved("5", "==", "5")).isTrue();
        assertThat(NumericSpatialConditions.compareResolved("5", "!=", "6")).isTrue();
        assertThat(NumericSpatialConditions.compareResolved("5", "?", "6"))
                .as("an unknown operator is false")
                .isFalse();
    }

    @Test
    void compareResolvedStringBranch() {
        assertThat(NumericSpatialConditions.compareResolved("Steve", "=", "Steve"))
                .isTrue();
        assertThat(NumericSpatialConditions.compareResolved("Steve", "==", "Steve"))
                .isTrue();
        assertThat(NumericSpatialConditions.compareResolved("Steve", "!=", "Notch"))
                .isTrue();
        assertThat(NumericSpatialConditions.compareResolved("Steve", "<", "Notch"))
                .as("a relational operator on non-numbers is false")
                .isFalse();
    }

    @Test
    void parseCoordsReadsAnExactTokenCount() {
        assertThat(NumericSpatialConditions.parseCoords("1 2 3 4", 4)).containsExactly(1, 2, 3, 4);
        assertThat(NumericSpatialConditions.parseCoords("1 2 3", 4))
                .as("too few tokens")
                .isNull();
        assertThat(NumericSpatialConditions.parseCoords("1 2 x 4", 4))
                .as("non-numeric token")
                .isNull();
        assertThat(NumericSpatialConditions.parseCoords("", 4))
                .as("blank value")
                .isNull();
    }

    // --- harness -------------------------------------------------------------------------------------------------

    /** Fire condition {@code id} with {@code arg} as the split-off value, for the online viewer. */
    private boolean test(String id, String arg) {
        return test(id, arg, new PlayerRef(viewer.getUniqueId(), viewer.getName()));
    }

    /** Fire condition {@code id} for a viewer UUID with no live player: the fail-closed offline case. */
    private boolean testOffline(String id, String arg) {
        return test(id, arg, new PlayerRef(UUID.randomUUID(), "Ghost"));
    }

    private boolean test(String id, String arg, PlayerRef ref) {
        BiPredicate<MenuContext, Map<String, String>> condition =
                bindings.condition(id).orElseThrow(() -> new AssertionError("condition not registered: " + id));
        return condition.test(MenuContext.of(ref, null, 0), Map.of("value", arg));
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
