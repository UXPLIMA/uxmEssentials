package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MovementActions;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.BukkitServerConnector;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ServerConnector;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the movement action pack. {@code teleport} rides Paper's {@code teleportAsync}, which
 * MockBukkit stubs to throw {@code UnimplementedOperationException}, so the fail-soft wrapper swallows it and the
 * mock never actually moves. The valid-destination cases are therefore asserted as no-crash, with the coordinate
 * mapping pinned by the pure {@link MovementActions.ParsedTeleport} grammar tests plus a real synchronous teleport
 * (a path MockBukkit does model) that lands on the same location the action would build. The skip paths (unknown
 * world, malformed coordinates) and {@code connect}, proved against a {@link RecordingConnector} fake and the real
 * {@link BukkitServerConnector}: carry concrete assertions.
 */
class MovementActionsTest {

    private ServerMock server;
    private World world;
    private PlayerMock viewer;
    private MenuBindings bindings;
    private RecordingLogger log;
    private RecordingConnector connector;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        viewer = server.addPlayer("Viewer");
        bindings = new MenuBindings();
        log = new RecordingLogger();
        connector = new RecordingConnector();
        MovementActions.register(bindings, connector, log);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- teleport ------------------------------------------------------------------------------------------------

    @Test
    void teleportWithAValidDestinationDoesNotCrash() {
        // MockBukkit's teleportAsync stub throws; the fail-soft wrapper turns that into a logged no-op. The point
        // here is only that a well-formed teleport never propagates an exception into the click dispatch.
        assertThatCode(() -> invoke("teleport", "world 100 64 200")).doesNotThrowAnyException();
    }

    @Test
    void tpIsAnAliasOfTeleport() {
        assertThatCode(() -> invoke("tp", "world 1 2 3")).doesNotThrowAnyException();
        assertThat(bindings.action("tp")).as("tp is registered").isPresent();
    }

    @Test
    void aValidDestinationBuildsALandableLocation() {
        // teleportAsync is unmodelled by the mock, so prove the parsed destination is a real, landable location by
        // building it exactly as the action does and applying it through the synchronous teleport the mock supports.
        MovementActions.ParsedTeleport dest =
                MovementActions.ParsedTeleport.parse("world 100 64 200").orElseThrow();
        Location target = new Location(world, dest.x(), dest.y(), dest.z(), dest.yaw(), dest.pitch());

        viewer.teleport(target);

        assertThat(viewer.getLocation().getX()).isEqualTo(100.0);
        assertThat(viewer.getLocation().getY()).isEqualTo(64.0);
        assertThat(viewer.getLocation().getZ()).isEqualTo(200.0);
    }

    @Test
    void aDestinationWithYawAndPitchCarriesThemIntoTheLocation() {
        MovementActions.ParsedTeleport dest =
                MovementActions.ParsedTeleport.parse("world 10 70 10 90 45").orElseThrow();
        Location target = new Location(world, dest.x(), dest.y(), dest.z(), dest.yaw(), dest.pitch());

        viewer.teleport(target);

        assertThat(viewer.getLocation().getYaw()).isEqualTo(90f);
        assertThat(viewer.getLocation().getPitch()).isEqualTo(45f);
    }

    @Test
    void teleportToAnUnknownWorldIsAFailSoftNoOpThatWarns() {
        Location before = viewer.getLocation().clone();

        assertThatCode(() -> invoke("teleport", "nope 1 2 3")).doesNotThrowAnyException();

        assertThat(viewer.getLocation()).isEqualTo(before);
        assertThat(log.warnings).anyMatch(line -> line.contains("unknown_world"));
    }

    @Test
    void teleportWithMalformedCoordinatesIsAFailSoftNoOpThatWarns() {
        Location before = viewer.getLocation().clone();

        assertThatCode(() -> invoke("teleport", "world x y z")).doesNotThrowAnyException();

        assertThat(viewer.getLocation()).isEqualTo(before);
        assertThat(log.warnings).anyMatch(line -> line.contains("malformed_destination"));
    }

    // --- connect -------------------------------------------------------------------------------------------------

    @Test
    void connectSendsTheViewerToTheNamedBackend() {
        invoke("connect", "lobby");

        assertThat(connector.servers).containsExactly("lobby");
        assertThat(connector.players).containsExactly(viewer);
    }

    @Test
    void serverIsAnAliasOfConnect() {
        invoke("server", "hub");

        assertThat(connector.servers).containsExactly("hub");
    }

    @Test
    void connectWithABlankArgumentIsANoOp() {
        invoke("connect", "   ");

        assertThat(connector.servers).isEmpty();
    }

    @Test
    void connectOverTheRealConnectorDoesNotThrowWithNoProxy() {
        MenuBindings realBindings = new MenuBindings();
        ServerConnector real = new BukkitServerConnector(MockBukkit.createMockPlugin(), log);
        MovementActions.register(realBindings, real, log);

        Consumer<MenuActionContext> connect =
                realBindings.action("connect").orElseThrow(() -> new AssertionError("connect not registered"));
        PlayerRef ref = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        MenuActionContext ctx =
                new MenuActionContext(MenuContext.of(ref, null, 0), viewer, ClickKind.LEFT, Map.of("value", "lobby"));

        assertThatCode(() -> connect.accept(ctx)).doesNotThrowAnyException();
    }

    // --- pure parsing edge cases (no server needed) --------------------------------------------------------------

    @Test
    void parsingReadsTheWorldAndCoordinates() {
        MovementActions.ParsedTeleport parsed =
                MovementActions.ParsedTeleport.parse("nether 10.5 64 -20").orElseThrow();

        assertThat(parsed.world()).isEqualTo("nether");
        assertThat(parsed.x()).isEqualTo(10.5);
        assertThat(parsed.y()).isEqualTo(64.0);
        assertThat(parsed.z()).isEqualTo(-20.0);
    }

    @Test
    void parsingWithTooFewTokensIsEmpty() {
        assertThat(MovementActions.ParsedTeleport.parse("world 1 2")).isEmpty();
    }

    @Test
    void parsingABlankValueIsEmpty() {
        assertThat(MovementActions.ParsedTeleport.parse("   ")).isEmpty();
    }

    @Test
    void parsingANonNumericCoordinateIsEmpty() {
        assertThat(MovementActions.ParsedTeleport.parse("world a b c")).isEmpty();
    }

    @Test
    void parsingWithoutYawAndPitchDefaultsThemToZero() {
        MovementActions.ParsedTeleport parsed =
                MovementActions.ParsedTeleport.parse("world 1 2 3").orElseThrow();

        assertThat(parsed.yaw()).isZero();
        assertThat(parsed.pitch()).isZero();
    }

    @Test
    void parsingWithYawAndPitchReadsBoth() {
        MovementActions.ParsedTeleport parsed =
                MovementActions.ParsedTeleport.parse("world 1 2 3 90 45").orElseThrow();

        assertThat(parsed.yaw()).isEqualTo(90f);
        assertThat(parsed.pitch()).isEqualTo(45f);
    }

    /** Builds the context the click listener would build, then fires the handler registered under {@code id}. */
    private void invoke(String id, String arg) {
        Consumer<MenuActionContext> handler =
                bindings.action(id).orElseThrow(() -> new AssertionError("action not registered: " + id));
        PlayerRef ref = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        MenuActionContext ctx =
                new MenuActionContext(MenuContext.of(ref, null, 0), viewer, ClickKind.LEFT, Map.of("value", arg));
        handler.accept(ctx);
    }

    /** Records the backend name and player each {@code connect} passes, standing in for the proxy plugin-message. */
    private static final class RecordingConnector implements ServerConnector {
        private final List<String> servers = new ArrayList<>();
        private final List<Player> players = new ArrayList<>();

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void connect(Player player, String server) {
            players.add(player);
            servers.add(server);
        }
    }

    /** Captures expanded info/warn lines so a test can assert what the console operator logger recorded. */
    private static final class RecordingLogger implements Logger {
        private final List<String> infos = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            infos.add(expand(message, args));
        }

        @Override
        public void warn(String message, Object... args) {
            warnings.add(expand(message, args));
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}

        private static String expand(String message, Object... args) {
            String expanded = message;
            for (Object arg : args) {
                expanded = expanded.replaceFirst("\\{}", String.valueOf(arg));
            }
            return expanded;
        }
    }
}
