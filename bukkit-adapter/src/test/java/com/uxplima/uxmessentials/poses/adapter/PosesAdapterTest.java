package com.uxplima.uxmessentials.poses.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.poses.adapter.inbound.command.PoseCommand;
import com.uxplima.uxmessentials.poses.adapter.inbound.command.PoseCooldownNotice;
import com.uxplima.uxmessentials.poses.adapter.inbound.command.SitCommand;
import com.uxplima.uxmessentials.poses.adapter.inbound.listener.CrawlMoveListener;
import com.uxplima.uxmessentials.poses.adapter.inbound.listener.PlayerSitInteractListener;
import com.uxplima.uxmessentials.poses.adapter.inbound.listener.PoseCancelListener;
import com.uxplima.uxmessentials.poses.adapter.inbound.listener.SeatInteractListener;
import com.uxplima.uxmessentials.poses.adapter.outbound.BukkitPacketPosePort;
import com.uxplima.uxmessentials.poses.adapter.outbound.BukkitPoseReturn;
import com.uxplima.uxmessentials.poses.adapter.outbound.BukkitSeatPort;
import com.uxplima.uxmessentials.poses.adapter.outbound.BukkitSnores;
import com.uxplima.uxmessentials.poses.adapter.outbound.PdcPlayerSitPreferences;
import com.uxplima.uxmessentials.poses.application.AllowAllRegionGate;
import com.uxplima.uxmessentials.poses.application.PoseCooldown;
import com.uxplima.uxmessentials.poses.application.PoseSessions;
import com.uxplima.uxmessentials.poses.application.PosesMessageKey;
import com.uxplima.uxmessentials.poses.application.StartCrawl;
import com.uxplima.uxmessentials.poses.application.StartPlayerSit;
import com.uxplima.uxmessentials.poses.application.StartPose;
import com.uxplima.uxmessentials.poses.application.StartSit;
import com.uxplima.uxmessentials.poses.application.StopPose;
import com.uxplima.uxmessentials.poses.application.TogglePlayerSit;
import com.uxplima.uxmessentials.poses.application.port.CrawlView;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.poses.domain.SittableBlocks;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.StorePosesPlaceholders;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLocator;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.packet.npc.NpcPackets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of the poses adapter end-to-end over a real {@link BukkitSeatPort}: right-clicking a stair
 * seats the player on a tagged, non-persistent armour stand; right-clicking a player stacks them on as a passenger;
 * the cancel triggers (quit, sneak, damage, teleport) end the pose and remove the seat; {@code sweepOrphans} reaps a
 * stray tagged seat; and the {@code poses_sitting} / {@code poses_toggle} placeholders track the live state.
 *
 * <p>The ghost-prevention proof is {@link #quitRemovesTheSeatSoNoGhostRemains()}: after the seated player quits,
 * the world holds <em>zero</em> entities carrying the {@code poses_seat} tag. The stacking-cleanup proof is
 * {@link #whenTheCarrierQuitsTheRidersSessionIsClearedAndTheyAreNoLongerAPassenger()}: a carrier leaving ends its
 * rider's session and takes the rider off as a passenger.
 */
class PosesAdapterTest {

    private ServerMock server;
    private WorldMock world;
    private org.bukkit.plugin.Plugin plugin;
    private NamespacedKey seatKey;

    private PoseSessions sessions;
    private BukkitSeatPort seats;
    private NpcPackets packets;
    private BukkitPacketPosePort posePort;
    private BukkitSnores snores;
    private RecordingCrawlView crawlView;
    private StartSit startSit;
    private StartPose startPose;
    private StartCrawl startCrawl;
    private StopPose stopPose;
    private SeatInteractListener interactListener;
    private PlayerSitInteractListener playerSitInteractListener;
    private PoseCancelListener cancelListener;
    private CrawlMoveListener crawlMoveListener;
    private PdcPlayerSitPreferences playerSitPreferences;
    private TogglePlayerSit togglePlayerSit;
    private StorePosesPlaceholders placeholders;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        seatKey = new NamespacedKey(plugin, "poses_seat");

        Scheduler scheduler = new InlineScheduler();
        sessions = new PoseSessions();
        crawlView = new RecordingCrawlView();
        seats = new BukkitSeatPort(plugin, scheduler, new NoopLogger());
        SittableBlocks sittableBlocks = new SittableBlocks(List.of("*_STAIRS", "*_SLAB", "*_CARPET"));
        PlayerLocator locator = who -> Optional.ofNullable(server.getPlayer(who.uuid()))
                .map(p -> BukkitRefs.toPosition(Objects.requireNonNull(p.getLocation(), "location")));
        DomainEventPublisher events = event -> {};

        // A spin step of 30 degrees per pass makes the seat's yaw advance visibly across the ticks the spin test
        // drives; the snore loop is exercised only through isSnoring/tick, so its sound is a soft fox-sleep default.
        packets = mock(NpcPackets.class);
        // Hand back a distinct built packet so the pose-broadcast tests can verify which viewers it was sent to.
        when(packets.pose(anyInt(), any())).thenReturn(new Object());
        posePort = new BukkitPacketPosePort(server, scheduler, packets, new NoopLogger(), 1);
        snores = new BukkitSnores(server, scheduler, new NoopLogger(), "minecraft:entity.fox.sleep", 0.5f, 1.0f, 20);

        PoseCooldownNotice cooldownNotice = new PoseCooldownNotice(PoseCooldown.unlimited(), new KeyMessages());
        startSit = new StartSit(
                sessions,
                seats,
                new AllowAllRegionGate(),
                locator,
                events,
                Clock.systemUTC(),
                true,
                true,
                PoseCooldown.unlimited());
        startPose = new StartPose(
                sessions,
                seats,
                new AllowAllRegionGate(),
                posePort,
                snores,
                events,
                Clock.systemUTC(),
                true,
                true,
                true,
                true,
                PoseCooldown.unlimited());
        startCrawl = new StartCrawl(
                sessions,
                crawlView,
                new AllowAllRegionGate(),
                events,
                Clock.systemUTC(),
                true,
                PoseCooldown.unlimited());
        playerSitPreferences = new PdcPlayerSitPreferences();
        StartPlayerSit startPlayerSit = new StartPlayerSit(
                sessions,
                seats,
                playerSitPreferences,
                new AllowAllRegionGate(),
                locator,
                events,
                Clock.systemUTC(),
                true,
                PoseCooldown.unlimited());
        togglePlayerSit = new TogglePlayerSit(playerSitPreferences);
        stopPose = new StopPose(
                sessions, seats, posePort, snores, crawlView, new BukkitPoseReturn(plugin, scheduler), events, true);
        interactListener =
                new SeatInteractListener(startSit, seats, sittableBlocks, new KeyMessages(), cooldownNotice, true, 5.0);
        playerSitInteractListener = new PlayerSitInteractListener(startPlayerSit, new KeyMessages(), cooldownNotice);
        cancelListener = new PoseCancelListener(stopPose, sessions);
        crawlMoveListener = new CrawlMoveListener(sessions, crawlView, stopPose);
        placeholders = new StorePosesPlaceholders(sessions, playerSitPreferences);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rightClickingAStairSeatsThePlayerOnATaggedSeat() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        Block stair = stairAt(1, 64, 0);

        interactListener.onInteract(rightClick(player, stair));

        List<Entity> tagged = taggedSeats();
        assertThat(tagged).hasSize(1);
        assertThat(tagged.get(0)).isInstanceOf(ArmorStand.class);
        assertThat(tagged.get(0).getPassengers()).contains(player);
        assertThat(sessions.isPosing(BukkitRefs.toRef(player))).isTrue();
    }

    @Test
    void theSeatIsAGroundedMarkerSoTheRiderSitsOnTheBlockNotFloatingAboveIt() {
        PlayerMock player = playerAt(0.5, 64, 0.5);

        interactListener.onInteract(rightClick(player, stairAt(1, 64, 0)));

        ArmorStand seat = (ArmorStand) taggedSeats().get(0);
        // A marker stand seats its rider at its own Y, so the seat sits on the stair surface (blockY + half a block +
        // the small mount tune) instead of a full-bodied stand that would push the rider about a block into the air.
        assertThat(seat.isMarker()).isTrue();
        double seatY = Objects.requireNonNull(seat.getLocation(), "location").getY();
        assertThat(seatY).isCloseTo(64.55, within(1e-6));
    }

    @Test
    void quitRemovesTheSeatSoNoGhostRemains() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        interactListener.onInteract(rightClick(player, stairAt(1, 64, 0)));
        assertThat(taggedSeats()).hasSize(1);

        cancelListener.onQuit(
                new PlayerQuitEvent(player, Component.text("bye"), PlayerQuitEvent.QuitReason.DISCONNECTED));

        // The ghost-prevention proof: not one tagged seat entity is left in the world after the seated player quits.
        assertThat(taggedSeats()).isEmpty();
        assertThat(sessions.isPosing(BukkitRefs.toRef(player))).isFalse();
    }

    @Test
    void sweepOrphansRemovesAStrayTaggedSeat() {
        ArmorStand stray = world.spawn(new Location(world, 5, 64, 5), ArmorStand.class);
        stray.getPersistentDataContainer().set(seatKey, PersistentDataType.STRING, "stray");
        assertThat(taggedSeats()).hasSize(1);

        int removed = seats.sweepOrphans();

        assertThat(removed).isEqualTo(1);
        assertThat(taggedSeats()).isEmpty();
    }

    @Test
    void sneakingEndsThePoseAndReturnsThePlayerToWhereTheySat() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        interactListener.onInteract(rightClick(player, stairAt(1, 64, 0)));
        // Move the player away from where they sat; standing up must teleport them back to the captured start.
        player.teleport(new Location(world, 20, 70, 20));

        cancelListener.onSneak(new PlayerToggleSneakEvent(player, true));

        assertThat(sessions.isPosing(BukkitRefs.toRef(player))).isFalse();
        assertThat(taggedSeats()).isEmpty();
        Location back = Objects.requireNonNull(player.getLocation(), "location");
        assertThat(back.getX()).isEqualTo(0.5);
        assertThat(back.getZ()).isEqualTo(0.5);
    }

    @Test
    void takingDamageEndsThePoseAndRemovesTheSeat() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerMock attacker = server.addPlayer("Attacker");
        interactListener.onInteract(rightClick(player, stairAt(1, 64, 0)));

        EntityDamageEvent damage = player.simulateDamage(1.0, attacker);
        cancelListener.onDamage(damage);

        assertThat(sessions.isPosing(BukkitRefs.toRef(player))).isFalse();
        assertThat(taggedSeats()).isEmpty();
    }

    @Test
    void teleportingEndsThePoseAndRemovesTheSeat() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        interactListener.onInteract(rightClick(player, stairAt(1, 64, 0)));

        cancelListener.onTeleport(
                new PlayerTeleportEvent(player, new Location(world, 0.5, 64, 0.5), new Location(world, 30, 64, 30)));

        assertThat(sessions.isPosing(BukkitRefs.toRef(player))).isFalse();
        assertThat(taggedSeats()).isEmpty();
    }

    @Test
    void theSittingPlaceholderTracksTheLiveSession() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        assertThat(placeholders.sitting(who)).isFalse();

        interactListener.onInteract(rightClick(player, stairAt(1, 64, 0)));
        assertThat(placeholders.sitting(who)).isTrue();

        stopPose.stop(who);
        assertThat(placeholders.sitting(who)).isFalse();
    }

    @Test
    void layingAnchorsThePlayerOnATaggedSeatAndThePlaceholdersReport() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        Position feet = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));

        startPose.start(who, PoseType.LAY, feet, feet.yaw());

        List<Entity> tagged = taggedSeats();
        assertThat(tagged).hasSize(1);
        assertThat(tagged.get(0)).isInstanceOf(ArmorStand.class);
        assertThat(tagged.get(0).getPassengers()).contains(player);
        assertThat(sessions.current(who).orElseThrow().type()).isEqualTo(PoseType.LAY);
        // The free-pose placeholders report the live pose; the plain-sit placeholder stays false for a lay.
        assertThat(placeholders.posing(who)).isTrue();
        assertThat(placeholders.pose(who)).isEqualTo("lay");
        assertThat(placeholders.sitting(who)).isFalse();
    }

    @Test
    void layingSpawnsTheCopyOnceAndLaterPassesOnlyKeepItUp() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        Position feet = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));

        startPose.start(who, PoseType.LAY, feet, feet.yaw());
        posePort.tick();
        posePort.tick();

        // The copy is spawned once for a viewer already watching; the later passes only re-state what the server
        // undoes (the owner's stripped gear), rather than spawning a second body every half second.
        verify(packets, times(1))
                .spawnPlayer(anyInt(), any(), anyDouble(), anyDouble(), anyDouble(), anyFloat(), anyFloat());
        verify(packets, atLeast(2)).equipment(eq(player.getEntityId()), any());
    }

    @Test
    void theSleepingCopyIsPutBackOverTheBedAfterTheClientWouldHaveDraggedItDown() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        Position feet = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));

        startPose.start(who, PoseType.LAY, feet, feet.yaw());

        // A client told a body is asleep drags it to its bed, and the bed is at the bottom of the world, so the
        // copy has to be put back more than once: in the spawn bundle and again on the ticks that follow. One
        // correction alone is what left the poser looking as though they had vanished.
        verify(packets, atLeast(2)).teleport(anyInt(), eq(0.5), eq(64.1125), eq(0.5), anyFloat(), anyFloat());
    }

    @Test
    void aPlayerWhoWalksIntoRangeIsShownTheCopy() {
        PlayerMock poser = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(poser);
        Position feet = BukkitRefs.toPosition(Objects.requireNonNull(poser.getLocation(), "location"));
        startPose.start(who, PoseType.LAY, feet, feet.yaw());

        PlayerMock latecomer = server.addPlayer("Latecomer");
        latecomer.teleport(new Location(world, 5, 64, 5));
        posePort.tick();

        // Someone arriving after the pose began still sees it: the refresh pass hands them the copy (and, for a
        // lie-down, the follow-up that puts the sleeping body back over its bed).
        verify(packets, atLeast(1)).send(eq(latecomer), any());
    }

    @Test
    void crawlSendsNoPoseMetadataBecauseTheServerOwnsTheSwimmingPose() {
        PlayerMock crawler = playerAt(0.5, 64, 0.5);
        PlayerMock observer = server.addPlayer("Observer");
        observer.teleport(new Location(world, 2, 64, 2)); // a second player sharing the crawler's world
        PlayerRef who = BukkitRefs.toRef(crawler);

        startCrawl.start(who, feetOf(crawler));

        // A crawl is a real server-side pose now, which the server syncs to every viewer on its own, so the pose
        // port stays out of it: no metadata override is broadcast to anyone, the crawler included.
        verify(packets, never()).pose(anyInt(), any());
        verify(packets, never()).send(eq(observer), any());
        verify(packets, never()).send(eq(crawler), any());
    }

    @Test
    void spinningRendersACopyAndStoppingTakesItDown() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        Position feet = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));

        startPose.start(who, PoseType.SPIN, feet, feet.yaw());
        ArmorStand seat = (ArmorStand) taggedSeats().get(0);
        assertThat(seat.getPassengers()).contains(player);
        // The spinner stays seated and invisible while their copy does the spinning for them.
        assertThat(posePort.isRendering(player.getUniqueId())).isTrue();
        assertThat(player.isInvisible()).isTrue();

        posePort.tick(); // a further pass keeps the copy up rather than doubling it

        stopPose.stop(who);

        assertThat(posePort.isRendering(player.getUniqueId())).isFalse();
        assertThat(player.isInvisible()).isFalse();
        assertThat(taggedSeats()).isEmpty(); // no ghost seat left behind
        posePort.tick(); // a further pass is a harmless no-op: the copy is gone
    }

    @Test
    void snoringStartsOnLayAndStopsOnStop() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        Position feet = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));

        startPose.start(who, PoseType.LAY, feet, feet.yaw());
        assertThat(snores.isSnoring(player.getUniqueId())).isTrue();
        snores.tick(); // the loop runs without throwing. It plays the snore sound at the laying player

        stopPose.stop(who);
        assertThat(snores.isSnoring(player.getUniqueId())).isFalse();
    }

    @Test
    void sneakingEndsAFreePoseClearsItAndRemovesTheSeat() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        Position feet = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));
        startPose.start(who, PoseType.LAY, feet, feet.yaw());
        assertThat(taggedSeats()).hasSize(1);

        cancelListener.onSneak(new PlayerToggleSneakEvent(player, true));

        assertThat(sessions.isPosing(who)).isFalse();
        assertThat(taggedSeats()).isEmpty(); // no ghost seat left behind
        assertThat(placeholders.posing(who)).isFalse();
        assertThat(snores.isSnoring(player.getUniqueId())).isFalse();
    }

    @Test
    void dismountingTheSeatEndsTheFreePoseSoTheSneakKeyReachesIt() {
        // A player riding a seat sends no sneak at all: the client turns the sneak key into a dismount. An armour
        // stand is no Vehicle, so only the entity-dismount exit can catch it, and without it the session would
        // linger while the player stood up.
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        Position feet = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));
        startPose.start(who, PoseType.LAY, feet, feet.yaw());
        Entity seat = taggedSeats().getFirst();

        cancelListener.onDismount(new EntityDismountEvent(player, seat));

        assertThat(sessions.isPosing(who)).isFalse();
        assertThat(taggedSeats()).isEmpty();
        assertThat(snores.isSnoring(player.getUniqueId())).isFalse();
    }

    @Test
    void dismountingASeatEndsAPlainSitToo() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        interactListener.onInteract(rightClick(player, stairAt(1, 64, 0)));
        Entity seat = taggedSeats().getFirst();

        cancelListener.onDismount(new EntityDismountEvent(player, seat));

        assertThat(sessions.isPosing(BukkitRefs.toRef(player))).isFalse();
        assertThat(taggedSeats()).isEmpty();
    }

    @Test
    void sneakingEndsACrawlAndRestoresTheFakeBlock() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        Position feet = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));
        startCrawl.start(who, feet);
        assertThat(sessions.isPosing(who)).isTrue();

        cancelListener.onSneak(new PlayerToggleSneakEvent(player, true));

        assertThat(sessions.isPosing(who)).isFalse();
        assertThat(crawlView.released).containsExactly(who);
    }

    @Test
    void runningTheSamePoseCommandTwiceStandsThePlayerBackUp() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        PoseCommand lay = new PoseCommand(
                "lay",
                "uxmessentials.lay.use",
                PoseType.LAY,
                PosesMessageKey.POSES_NOW_LAYING,
                "Lie down where you stand; run again to stand up.",
                startPose,
                stopPose,
                sessions,
                new KeyMessages(),
                new PoseCooldownNotice(PoseCooldown.unlimited(), new KeyMessages()));

        runAsPlayer(lay.build(), player);
        assertThat(sessions.isPosing(who)).isTrue();

        runAsPlayer(lay.build(), player);
        assertThat(sessions.isPosing(who)).isFalse();
        assertThat(taggedSeats()).isEmpty();
    }

    @Test
    void runningTheSitCommandTwiceStandsThePlayerBackUp() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        SitCommand sit = new SitCommand(
                startSit,
                stopPose,
                sessions,
                new SittableBlocks(List.of("*_STAIRS")),
                new KeyMessages(),
                new PoseCooldownNotice(PoseCooldown.unlimited(), new KeyMessages()),
                false,
                2.0);

        runAsPlayer(sit.build(), player);
        assertThat(sessions.isPosing(who)).isTrue();

        runAsPlayer(sit.build(), player);
        assertThat(sessions.isPosing(who)).isFalse();
        assertThat(taggedSeats()).isEmpty();
    }

    @Test
    void rightClickingAPlayerSeatsTheClickerOnThemAsAPassenger() {
        PlayerMock rider = playerAt(0.5, 64, 0.5);
        rider.addAttachment(plugin, "uxmessentials.playersit.use", true);
        PlayerMock target = server.addPlayer("Carrier");

        playerSitInteractListener.onInteract(rightClickPlayer(rider, target));

        // Stacking mount: the clicker is now a passenger of the target (addPassenger chains for A-on-B-on-C).
        assertThat(target.getPassengers()).contains(rider);
        assertThat(sessions.isPosing(BukkitRefs.toRef(rider))).isTrue();
    }

    @Test
    void aRefusingTargetRejectsThePlayerSit() {
        PlayerMock rider = playerAt(0.5, 64, 0.5);
        rider.addAttachment(plugin, "uxmessentials.playersit.use", true);
        PlayerMock target = server.addPlayer("Carrier");
        // The target flips their /poses toggle to refuse being sat on.
        togglePlayerSit.toggle(BukkitRefs.toRef(target));

        playerSitInteractListener.onInteract(rightClickPlayer(rider, target));

        assertThat(target.getPassengers()).doesNotContain(rider);
        assertThat(sessions.isPosing(BukkitRefs.toRef(rider))).isFalse();
    }

    @Test
    void togglingFlipsThePdcBackedPreferenceAndThePlaceholderReflectsIt() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        // The GSit default is to allow being sat on.
        assertThat(placeholders.allowsSitting(who)).isTrue();

        assertThat(togglePlayerSit.toggle(who)).isFalse();
        assertThat(placeholders.allowsSitting(who)).isFalse();

        assertThat(togglePlayerSit.toggle(who)).isTrue();
        assertThat(placeholders.allowsSitting(who)).isTrue();
    }

    @Test
    void whenTheCarrierQuitsTheRidersSessionIsClearedAndTheyAreNoLongerAPassenger() {
        PlayerMock rider = playerAt(0.5, 64, 0.5);
        rider.addAttachment(plugin, "uxmessentials.playersit.use", true);
        PlayerMock target = server.addPlayer("Carrier");
        playerSitInteractListener.onInteract(rightClickPlayer(rider, target));
        assertThat(target.getPassengers()).contains(rider);

        cancelListener.onQuit(
                new PlayerQuitEvent(target, Component.text("bye"), PlayerQuitEvent.QuitReason.DISCONNECTED));

        // Stacking cleanup proof: the carrier leaving ends the rider's session and takes them off as a passenger,
        // so no stuck PoseSession and no ghost passenger remain.
        assertThat(sessions.isPosing(BukkitRefs.toRef(rider))).isFalse();
        assertThat(rider.getVehicle()).isNull();
        assertThat(target.getPassengers()).doesNotContain(rider);
    }

    @Test
    void removeAllDrainsEverySeatOnStop() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        interactListener.onInteract(rightClick(player, stairAt(1, 64, 0)));
        assertThat(taggedSeats()).hasSize(1);

        seats.removeAll();

        assertThat(taggedSeats()).isEmpty();
    }

    @Test
    void crawlingRecordsACrawlSessionHoldsThePlayerAndReportsThePosePlaceholder() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);

        startCrawl.start(who, feetOf(player));

        // The crawl holds the player where they stand and records a CRAWL session; no seat entity is spawned.
        assertThat(crawlView.held).containsExactly(feetOf(player));
        assertThat(taggedSeats()).isEmpty();
        assertThat(sessions.current(who).orElseThrow().type()).isEqualTo(PoseType.CRAWL);
        // The pose placeholders read the crawl: posing yes, pose "crawl", and the plain-sit placeholder stays false.
        assertThat(placeholders.posing(who)).isTrue();
        assertThat(placeholders.pose(who)).isEqualTo("crawl");
        assertThat(placeholders.sitting(who)).isFalse();
    }

    @Test
    void movingRestatesTheHoldAtTheNewPosition() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        startCrawl.start(who, feetOf(player));

        Location from = Objects.requireNonNull(player.getLocation(), "location");
        Location to = new Location(world, 1.5, 64, 0.5);
        crawlMoveListener.onMove(new PlayerMoveEvent(player, from, to));

        // The ceiling travels with the crawler: the hold is re-stated wherever they walk to.
        assertThat(crawlView.held).containsExactly(feetOf(player), BukkitRefs.toPosition(to));
        assertThat(crawlView.released).isEmpty();
    }

    @Test
    void aLookOnlyMoveDoesNotDisturbTheCrawl() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        startCrawl.start(who, feetOf(player));
        crawlView.held.clear();

        Location from = Objects.requireNonNull(player.getLocation(), "location");
        Location to = from.clone();
        to.setYaw(45f);
        to.setPitch(10f);
        crawlMoveListener.onMove(new PlayerMoveEvent(player, from, to));

        // The player has not moved an inch, so the hot path skips the follow work entirely.
        assertThat(crawlView.held).isEmpty();
    }

    @Test
    void takingFlightEndsTheCrawlRatherThanFollowingIt() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        startCrawl.start(who, feetOf(player));
        player.setAllowFlight(true);
        player.setFlying(true); // the flight branch of the same environment guard

        Location from = Objects.requireNonNull(player.getLocation(), "location");
        crawlMoveListener.onMove(new PlayerMoveEvent(player, from, new Location(world, 1.5, 64, 0.5)));

        assertThat(sessions.isPosing(who)).isFalse();
        assertThat(crawlView.released).containsExactly(who);
    }

    @Test
    void aSecondCrawlReleasesThePlayerAndClearsTheSession() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        startCrawl.start(who, feetOf(player));

        // A second /crawl toggles off through StopPose (the command's toggle-off path).
        stopPose.stop(who);

        assertThat(crawlView.released).containsExactly(who);
        assertThat(sessions.isPosing(who)).isFalse();
    }

    @Test
    void quittingWhileCrawlingReleasesThePlayer() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        startCrawl.start(who, feetOf(player));

        cancelListener.onQuit(
                new PlayerQuitEvent(player, Component.text("bye"), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertThat(crawlView.released).containsExactly(who);
        assertThat(sessions.isPosing(who)).isFalse();
    }

    @Test
    void teleportingWhileCrawlingReleasesThePlayerAndClearsTheSession() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        startCrawl.start(who, feetOf(player));

        cancelListener.onTeleport(
                new PlayerTeleportEvent(player, new Location(world, 0.5, 64, 0.5), new Location(world, 40, 70, 40)));

        assertThat(crawlView.released).containsExactly(who);
        assertThat(sessions.isPosing(who)).isFalse();
    }

    @Test
    void takingDamageWhileCrawlingDoesNotEndIt() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerMock attacker = server.addPlayer("Attacker");
        PlayerRef who = BukkitRefs.toRef(player);
        startCrawl.start(who, feetOf(player));

        EntityDamageEvent damage = player.simulateDamage(1.0, attacker);
        cancelListener.onDamage(damage);

        assertThat(sessions.current(who).orElseThrow().type()).isEqualTo(PoseType.CRAWL);
        assertThat(crawlView.released).isEmpty();
    }

    private static Position feetOf(PlayerMock player) {
        return BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));
    }

    private PlayerMock playerAt(double x, double y, double z) {
        PlayerMock player = server.addPlayer("Steve");
        player.teleport(new Location(world, x, y, z));
        return player;
    }

    private Block stairAt(int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.OAK_STAIRS);
        return block;
    }

    private static PlayerInteractEvent rightClick(PlayerMock player, Block block) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP, EquipmentSlot.HAND);
    }

    private static PlayerInteractEntityEvent rightClickPlayer(PlayerMock clicker, PlayerMock target) {
        return new PlayerInteractEntityEvent(clicker, target, EquipmentSlot.HAND);
    }

    /** Every entity in the world that carries the {@code poses_seat} PDC tag, the ghost-prevention probe. */
    private List<Entity> taggedSeats() {
        return world.getEntities().stream()
                .filter(entity -> entity.getPersistentDataContainer().has(seatKey, PersistentDataType.STRING))
                .toList();
    }

    /** Runs every scheduled hop inline so the region-threaded seat work completes within the test. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }

        @Override
        public void laterGlobal(Duration delay, Runnable task) {
            task.run();
        }

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            // The spin and snore loops are driven deterministically by the tests (via posePort.tick() /
            // snores.tick()), so the repeating registration is a no-op that hands back a closeable to cancel.
            return () -> {};
        }
    }

    /** Dispatch a built command node as {@code player}, the way Paper's own dispatcher would. */
    private void runAsPlayer(LiteralCommandNode<CommandSourceStack> node, PlayerMock player) {
        player.addAttachment(plugin, "uxmessentials." + node.getName() + ".use", true);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
        try {
            dispatcher.execute(node.getName(), CommandSourceStackMock.from(player));
        } catch (CommandSyntaxException e) {
            throw new AssertionError("the " + node.getName() + " command did not dispatch", e);
        }
    }

    /**
     * Records where the crawl was asked to hold the player and every release. Routed through the port so the test
     * never touches the packet stack or {@code setPose}, neither of which MockBukkit implements; the recorded
     * hold/release calls are the probe that no exit leaves a player held down.
     */
    private static final class RecordingCrawlView implements CrawlView {
        private final List<Position> held = new ArrayList<>();
        private final List<PlayerRef> released = new ArrayList<>();

        @Override
        public void hold(PlayerRef who, Position feet) {
            held.add(feet);
        }

        @Override
        public void release(PlayerRef who) {
            released.add(who);
        }
    }

    /** Resolves each key to its own id, enough for the command-feedback paths the tests do not assert on. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
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
