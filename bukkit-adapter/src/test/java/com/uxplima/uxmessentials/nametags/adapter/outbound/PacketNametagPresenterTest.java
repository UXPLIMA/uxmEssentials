package com.uxplima.uxmessentials.nametags.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.nametags.application.port.NametagVanish;
import com.uxplima.uxmessentials.nametags.domain.NametagAppearance;
import com.uxplima.uxmessentials.nametags.domain.NametagConfig;
import com.uxplima.uxmessentials.nametags.domain.NametagFormat;
import com.uxplima.uxmessentials.nametags.domain.NametagVisibility;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.team.PlayerTeamCoordinator;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmlib.nametag.Appearance;
import com.uxplima.uxmlib.nametag.NametagPackets;
import com.uxplima.uxmlib.nametag.NametagRenderer;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers the {@link PacketNametagPresenter} wired onto the real uxmLib packet {@link NametagRenderer}, with the NMS
 * {@code NametagPackets} port and the per-wearer refresh scheduler faked out so the test runs under MockBukkit. The
 * presenter's selection and eligible-viewer cull are exercised directly through the package-private
 * {@link PacketNametagPresenter#selectFor} and {@link PacketNametagPresenter#eligibleViewers} seams; the show/update/
 * remove handle bookkeeping is exercised end to end against the lib renderer (which builds packets through the fake
 * port and so leaves no display entity to spawn under the mock).
 */
class PacketNametagPresenterTest {

    private static final String STAFF_NODE = "uxmessentials.staff";

    private ServerMock server;
    private RecordingPackets packets;
    private NoTimerScheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        packets = new RecordingPackets();
        scheduler = new NoTimerScheduler();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void selectsTheStaffFormatForAStaffWearerAndNothingForOthers() {
        PlayerMock staff = server.addPlayer();
        staff.addAttachment(MockBukkit.createMockPlugin(), STAFF_NODE, true);
        PlayerMock regular = server.addPlayer();
        PacketNametagPresenter presenter =
                presenter(singleFormat("staff", new DisplayCondition.Permission(STAFF_NODE)), alwaysVisible());

        assertThat(presenter.selectFor(staff)).map(NametagFormat::name).hasValue("staff");
        assertThat(presenter.selectFor(regular)).isEmpty();
    }

    @Test
    void aFormatWhoseShowWhenFailsSelectsNothing() {
        PlayerMock wearer = server.addPlayer();
        NametagConfig config = new NametagConfig(List.of(format(
                "default",
                DisplayCondition.always(),
                new NametagVisibility(
                        new DisplayCondition.Permission(STAFF_NODE), false, true, OptionalDouble.empty()))));
        PacketNametagPresenter presenter = presenter(config, alwaysVisible());

        assertThat(presenter.selectFor(wearer)).isEmpty();
    }

    @Test
    void eligibleViewersExcludesTheWearerAndAViewerVanishedFromThem() {
        PlayerMock wearer = server.addPlayer();
        PlayerMock seer = server.addPlayer();
        PlayerMock blind = server.addPlayer();
        NametagVanish vanish = (viewer, target) -> !viewer.uuid().equals(blind.getUniqueId());
        PacketNametagPresenter presenter = presenter(singleFormat("default", DisplayCondition.always()), vanish);
        NametagFormat format = presenter.selectFor(wearer).orElseThrow();
        // Each player publishes their own position on their own thread before any cull runs in production; mirror that.
        presenter.snapshotForTest(wearer);
        presenter.snapshotForTest(seer);
        presenter.snapshotForTest(blind);

        List<Player> eligible = presenter.eligibleViewers(wearer, format);

        assertThat(eligible).contains(seer);
        assertThat(eligible).doesNotContain(wearer, blind);
    }

    @Test
    void hideWhileSneakingHidesTheNametagFromEveryone() {
        PlayerMock wearer = server.addPlayer();
        server.addPlayer();
        wearer.setSneaking(true);
        NametagConfig config = new NametagConfig(List.of(format(
                "default",
                DisplayCondition.always(),
                new NametagVisibility(DisplayCondition.always(), true, true, OptionalDouble.empty()))));
        PacketNametagPresenter presenter = presenter(config, alwaysVisible());
        NametagFormat format = presenter.selectFor(wearer).orElseThrow();

        assertThat(presenter.eligibleViewers(wearer, format)).isEmpty();
    }

    @Test
    void respectVanishTrueExcludesAVanishedViewer() {
        PlayerMock wearer = server.addPlayer();
        PlayerMock blind = server.addPlayer();
        NametagVanish vanish = (viewer, target) -> !viewer.uuid().equals(blind.getUniqueId());
        PacketNametagPresenter presenter = presenter(singleFormat("default", DisplayCondition.always()), vanish);
        NametagFormat selected = presenter.selectFor(wearer).orElseThrow();
        presenter.snapshotForTest(wearer);
        presenter.snapshotForTest(blind);

        assertThat(presenter.eligibleViewers(wearer, selected)).doesNotContain(blind);
    }

    @Test
    void theCullUsesViewerDistanceNotTheAppearanceViewRange() {
        PlayerMock wearer = server.addPlayer();
        PlayerMock near = server.addPlayer();
        PlayerMock far = server.addPlayer();
        near.teleport(wearer.getLocation().clone().add(5, 0, 0));
        far.teleport(wearer.getLocation().clone().add(40, 0, 0));
        NametagConfig config = new NametagConfig(List.of(formatWith(
                appearance(OptionalDouble.of(1.0)),
                new NametagVisibility(DisplayCondition.always(), false, true, OptionalDouble.of(10.0)))));
        PacketNametagPresenter presenter = presenter(config, alwaysVisible());
        NametagFormat selected = presenter.selectFor(wearer).orElseThrow();
        // Publish each viewer's post-teleport position the way each player's own reconcile would before a cull.
        presenter.snapshotForTest(wearer);
        presenter.snapshotForTest(near);
        presenter.snapshotForTest(far);

        List<Player> eligible = presenter.eligibleViewers(wearer, selected);

        assertThat(eligible).contains(near);
        assertThat(eligible).doesNotContain(far);
    }

    @Test
    void showTracksAWearerAndSendsSpawnPacketsToEligibleViewers() {
        PlayerMock wearer = server.addPlayer();
        PlayerMock viewer = server.addPlayer();
        PacketNametagPresenter presenter =
                presenter(singleFormat("default", DisplayCondition.always()), alwaysVisible());
        // The viewer was already online and reconciled, so they have published their position for the wearer's cull.
        presenter.snapshotForTest(viewer);

        presenter.show(wearer);

        assertThat(presenter.isTracked(wearer.getUniqueId())).isTrue();
        // The lib renderer sent the spawn bundle to the eligible viewer (and not to the wearer themselves).
        assertThat(packets.sentTo(viewer)).isGreaterThan(0);
        assertThat(packets.sentTo(wearer)).isZero();
    }

    @Test
    void showForAnUnmatchedWearerTracksNothing() {
        PlayerMock wearer = server.addPlayer();
        PacketNametagPresenter presenter =
                presenter(singleFormat("staff", new DisplayCondition.Permission(STAFF_NODE)), alwaysVisible());

        presenter.show(wearer);

        assertThat(presenter.isTracked(wearer.getUniqueId())).isFalse();
    }

    @Test
    void showForAnAlreadyTrackedWearerDoesNotShowASecondHandle() {
        PlayerMock wearer = server.addPlayer();
        server.addPlayer();
        PacketNametagPresenter presenter =
                presenter(singleFormat("default", DisplayCondition.always()), alwaysVisible());

        presenter.show(wearer);
        presenter.show(wearer);

        // A second show for the same wearer reconciles through update on the same format: still one tracked entry.
        assertThat(presenter.trackedCount()).isEqualTo(1);
    }

    @Test
    void updateRemovesTheNametagWhenNoFormatAppliesAnyMore() {
        PlayerMock wearer = server.addPlayer();
        server.addPlayer();
        // Format requires staff; the wearer starts with it, then loses it before the reconcile.
        var attachment = wearer.addAttachment(MockBukkit.createMockPlugin(), STAFF_NODE, true);
        PacketNametagPresenter presenter =
                presenter(singleFormat("staff", new DisplayCondition.Permission(STAFF_NODE)), alwaysVisible());
        presenter.show(wearer);
        assertThat(presenter.isTracked(wearer.getUniqueId())).isTrue();

        wearer.removeAttachment(attachment);
        presenter.update(wearer);

        assertThat(presenter.isTracked(wearer.getUniqueId())).isFalse();
    }

    @Test
    void removeUntracksAndIsASafeNoOpForAnUntrackedPlayer() {
        PlayerMock wearer = server.addPlayer();
        server.addPlayer();
        PacketNametagPresenter presenter =
                presenter(singleFormat("default", DisplayCondition.always()), alwaysVisible());
        presenter.show(wearer);

        presenter.remove(wearer.getUniqueId());
        assertThat(presenter.isTracked(wearer.getUniqueId())).isFalse();
        // A second remove (now untracked) must not throw.
        assertThatCode(() -> presenter.remove(wearer.getUniqueId())).doesNotThrowAnyException();
    }

    @Test
    void removeAllClearsEveryTrackedNametag() {
        PlayerMock first = server.addPlayer();
        PlayerMock second = server.addPlayer();
        PacketNametagPresenter presenter =
                presenter(singleFormat("default", DisplayCondition.always()), alwaysVisible());
        presenter.show(first);
        presenter.show(second);

        presenter.removeAll();

        assertThat(presenter.isTracked(first.getUniqueId())).isFalse();
        assertThat(presenter.isTracked(second.getUniqueId())).isFalse();
        assertThat(presenter.trackedCount()).isZero();
    }

    @Test
    void showHidesTheWearersVanillaNameWhenHideVanillaNameIsOn() {
        PlayerMock wearer = server.addPlayer();
        server.addPlayer();
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        PacketNametagPresenter presenter =
                presenter(singleFormat("default", DisplayCondition.always()), alwaysVisible(), coordinator, true);

        presenter.show(wearer);

        assertThat(coordinator.isHidden(wearer.getUniqueId())).isTrue();
        var team = wearer.getScoreboard().getTeam(PlayerTeamCoordinator.TEAM_NAME);
        assertThat(team).isNotNull();
        assertThat(team.hasEntry(wearer.getName())).isTrue();
    }

    @Test
    void showLeavesTheVanillaNameWhenHideVanillaNameIsOff() {
        PlayerMock wearer = server.addPlayer();
        server.addPlayer();
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        PacketNametagPresenter presenter =
                presenter(singleFormat("default", DisplayCondition.always()), alwaysVisible(), coordinator, false);

        presenter.show(wearer);

        // The nametag is still live, but the vanilla name is untouched: no hide-team and no hidden mark.
        assertThat(presenter.isTracked(wearer.getUniqueId())).isTrue();
        assertThat(coordinator.isHidden(wearer.getUniqueId())).isFalse();
        assertThat(wearer.getScoreboard().getTeam(PlayerTeamCoordinator.TEAM_NAME))
                .isNull();
    }

    @Test
    void updateRestoresTheVanillaNameWhenNoFormatAppliesAnyMore() {
        PlayerMock wearer = server.addPlayer();
        server.addPlayer();
        var attachment = wearer.addAttachment(MockBukkit.createMockPlugin(), STAFF_NODE, true);
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        PacketNametagPresenter presenter = presenter(
                singleFormat("staff", new DisplayCondition.Permission(STAFF_NODE)), alwaysVisible(), coordinator, true);
        presenter.show(wearer);
        assertThat(coordinator.isHidden(wearer.getUniqueId())).isTrue();

        wearer.removeAttachment(attachment);
        presenter.update(wearer);

        assertThat(presenter.isTracked(wearer.getUniqueId())).isFalse();
        assertThat(coordinator.isHidden(wearer.getUniqueId())).isFalse();
        var team = wearer.getScoreboard().getTeam(PlayerTeamCoordinator.TEAM_NAME);
        assertThat(team).isNotNull();
        assertThat(team.hasEntry(wearer.getName())).isFalse();
    }

    @Test
    void theHideTeamSurvivesAScoreboardBoardSwitchViaTheReapplyCallback() {
        PlayerMock wearer = server.addPlayer();
        server.addPlayer();
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        PacketNametagPresenter presenter =
                presenter(singleFormat("default", DisplayCondition.always()), alwaysVisible(), coordinator, true);
        presenter.show(wearer);

        // The scoreboard module hands the player a fresh per-player board on every sidebar create; setScoreboard
        // resets the client team registry, so the new board has no hide-team. uxmLib's SidebarManager fires its
        // onBoardSwitch callback (wired to coordinator::reapply) right after, which re-creates it on the new board.
        var freshBoard = server.getScoreboardManager().getNewScoreboard();
        wearer.setScoreboard(freshBoard);
        assertThat(freshBoard.getTeam(PlayerTeamCoordinator.TEAM_NAME)).isNull();

        coordinator.reapply(wearer, wearer.getScoreboard());

        var team = wearer.getScoreboard().getTeam(PlayerTeamCoordinator.TEAM_NAME);
        assertThat(team).isNotNull();
        assertThat(team.hasEntry(wearer.getName())).isTrue();
    }

    @Test
    void removeWithThePlayerRestoresTheVanillaNameOnTheBoard() {
        PlayerMock wearer = server.addPlayer();
        server.addPlayer();
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        PacketNametagPresenter presenter =
                presenter(singleFormat("default", DisplayCondition.always()), alwaysVisible(), coordinator, true);
        presenter.show(wearer);
        assertThat(coordinator.isHidden(wearer.getUniqueId())).isTrue();

        // The quit path passes the live player so the stranded hide-team entry is removed from the board, not just the
        // bookkeeping (remove(uuid) would leave the entry on a server-lifetime board).
        presenter.remove(wearer);

        assertThat(presenter.isTracked(wearer.getUniqueId())).isFalse();
        assertThat(coordinator.isHidden(wearer.getUniqueId())).isFalse();
        var team = wearer.getScoreboard().getTeam(PlayerTeamCoordinator.TEAM_NAME);
        assertThat(team).isNotNull();
        assertThat(team.hasEntry(wearer.getName())).isFalse();
    }

    @Test
    void quitLeavesNoStaleHideEntryOnTheMainBoard() {
        PlayerMock wearer = server.addPlayer();
        server.addPlayer();
        // Reproduce the leak face: the player is on the main shared board (scoreboard module off, or restored to main),
        // whose hide-team is a server-lifetime singleton. The quit path must remove the entry, not just unmark the map.
        wearer.setScoreboard(server.getScoreboardManager().getMainScoreboard());
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        PacketNametagPresenter presenter =
                presenter(singleFormat("default", DisplayCondition.always()), alwaysVisible(), coordinator, true);
        presenter.show(wearer);
        var mainTeam = server.getScoreboardManager().getMainScoreboard().getTeam(PlayerTeamCoordinator.TEAM_NAME);
        assertThat(mainTeam).isNotNull();
        assertThat(mainTeam.hasEntry(wearer.getName())).isTrue();

        presenter.remove(wearer);

        assertThat(mainTeam.hasEntry(wearer.getName())).isFalse();
    }

    @Test
    void reconnectWithNoMatchingFormatRestoresTheVanillaNameEvenIfAStaleEntryExists() {
        PlayerMock wearer = server.addPlayer();
        server.addPlayer();
        wearer.setScoreboard(server.getScoreboardManager().getMainScoreboard());
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        // Defense-in-depth: simulate a stranded hide entry from an earlier session that fix #2 normally prevents.
        coordinator.hide(wearer);
        var mainTeam = server.getScoreboardManager().getMainScoreboard().getTeam(PlayerTeamCoordinator.TEAM_NAME);
        assertThat(mainTeam).isNotNull();
        assertThat(mainTeam.hasEntry(wearer.getName())).isTrue();
        // The reconnecting player now matches no format (staff format, non-staff player).
        PacketNametagPresenter presenter = presenter(
                singleFormat("staff", new DisplayCondition.Permission(STAFF_NODE)), alwaysVisible(), coordinator, true);

        presenter.show(wearer);

        // show early-returns (nothing tracked) but still unhides, so the player is not left nameless.
        assertThat(presenter.isTracked(wearer.getUniqueId())).isFalse();
        assertThat(coordinator.isHidden(wearer.getUniqueId())).isFalse();
        assertThat(mainTeam.hasEntry(wearer.getName())).isFalse();
    }

    private PacketNametagPresenter presenter(NametagConfig config, NametagVanish vanish) {
        return presenter(config, vanish, new PlayerTeamCoordinator(), false);
    }

    private PacketNametagPresenter presenter(
            NametagConfig config, NametagVanish vanish, PlayerTeamCoordinator coordinator, boolean hideVanillaName) {
        // Never obstructed: the default appearance does not fade through blocks anyway, so the line-of-sight check is
        // not even invoked, but a benign fake keeps the wiring explicit.
        NametagRenderer libRenderer = new NametagRenderer(packets, scheduler, (player, entity) -> false);
        return new PacketNametagPresenter(
                () -> config,
                libRenderer,
                new AnimationRegistry(List.of()),
                vanish,
                () -> Duration.ofSeconds(1),
                coordinator,
                () -> hideVanillaName);
    }

    private static NametagConfig singleFormat(String name, DisplayCondition condition) {
        return new NametagConfig(List.of(format(name, condition, NametagVisibility.alwaysVisible())));
    }

    private static NametagFormat format(String name, DisplayCondition condition, NametagVisibility visibility) {
        return new NametagFormat(
                name, condition, 0, List.of("<white>{player}"), appearance(OptionalDouble.empty()), visibility);
    }

    private static NametagFormat formatWith(NametagAppearance appearance, NametagVisibility visibility) {
        return new NametagFormat(
                "default", DisplayCondition.always(), 0, List.of("<white>{player}"), appearance, visibility);
    }

    private static NametagAppearance appearance(OptionalDouble viewRange) {
        return new NametagAppearance(
                "CENTER",
                false,
                Optional.empty(),
                OptionalInt.empty(),
                false,
                Optional.empty(),
                OptionalInt.empty(),
                viewRange,
                1.0,
                0.3,
                false,
                OptionalInt.empty());
    }

    private static NametagVanish alwaysVisible() {
        return NametagVanish.ALWAYS_VISIBLE;
    }

    /** A {@link NametagPackets} fake that records how many packets each viewer received, with opaque packet objects. */
    private static final class RecordingPackets implements NametagPackets {

        private final AtomicInteger ids = new AtomicInteger(1);
        private final java.util.Map<UUID, Integer> sends = new java.util.HashMap<>();

        int sentTo(Player viewer) {
            return sends.getOrDefault(viewer.getUniqueId(), 0);
        }

        @Override
        public int allocateEntityId() {
            return ids.getAndIncrement();
        }

        @Override
        public Object spawnPacket(int entityId, double x, double y, double z) {
            return new Object();
        }

        @Override
        public Object metadataPacket(
                int entityId, Component line, Appearance appearance, int opacity, org.joml.Vector3f translation) {
            return new Object();
        }

        @Override
        public Object mountPacket(int vehicleId, int[] passengerIds) {
            return new Object();
        }

        @Override
        public Object removePacket(int[] entityIds) {
            return new Object();
        }

        @Override
        public Object bundle(List<Object> packets) {
            return new Object();
        }

        @Override
        public void send(Player viewer, Object packet) {
            sends.merge(viewer.getUniqueId(), 1, Integer::sum);
        }
    }

    /** A uxmLib {@link Scheduler} whose timers never fire, only the inline first frame of {@code show} runs. */
    private static final class NoTimerScheduler implements Scheduler {

        @Override
        public TaskHandle global(Runnable task) {
            task.run();
            return new NoopHandle();
        }

        @Override
        public TaskHandle globalLater(Duration delay, Runnable task) {
            return new NoopHandle();
        }

        @Override
        public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            return new NoopHandle();
        }

        @Override
        public TaskHandle region(Location location, Runnable task) {
            task.run();
            return new NoopHandle();
        }

        @Override
        public TaskHandle regionLater(Location location, Duration delay, Runnable task) {
            return new NoopHandle();
        }

        @Override
        public TaskHandle regionTimer(Location location, Duration delay, Duration period, Consumer<TaskHandle> task) {
            return new NoopHandle();
        }

        @Override
        public TaskHandle entity(Entity entity, Runnable task) {
            task.run();
            return new NoopHandle();
        }

        @Override
        public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
            return new NoopHandle();
        }

        @Override
        public TaskHandle entityTimer(Entity entity, Duration delay, Duration period, Consumer<TaskHandle> task) {
            return new NoopHandle();
        }

        @Override
        public TaskHandle async(Runnable task) {
            task.run();
            return new NoopHandle();
        }

        @Override
        public TaskHandle asyncLater(Duration delay, Runnable task) {
            return new NoopHandle();
        }

        @Override
        public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            return new NoopHandle();
        }
    }

    private static final class NoopHandle implements TaskHandle {

        private boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
