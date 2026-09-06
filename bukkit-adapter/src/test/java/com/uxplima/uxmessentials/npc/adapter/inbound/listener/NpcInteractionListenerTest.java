package com.uxplima.uxmessentials.npc.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import com.uxplima.uxmessentials.npc.adapter.outbound.NpcRenderer;
import com.uxplima.uxmessentials.npc.adapter.outbound.NpcViewSpawner;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickActionRunner;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickCommandRunner;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import com.uxplima.uxmessentials.shared.domain.action.ClickActionType;
import com.uxplima.uxmessentials.shared.domain.action.ClickTrigger;
import com.uxplima.uxmlib.packet.npc.NamedColor;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers {@link NpcInteractionListener}: a right-click on an NPC's (server-unknown) fake entity runs its bound
 * command via the runner; the {@code [console]}/{@code [player]} prefixes route to the right dispatcher; a
 * {@code {player}} token is filled with the clicker's name; the per-player-per-NPC cooldown swallows a rapid
 * second click; an NPC with no command and an unknown entity id do nothing; a left-click (attack) and the off
 * hand are ignored.
 */
class NpcInteractionListenerTest {

    private static final int ENTITY_ID = 1;

    private ServerMock server;
    private FixedIdPackets packets;
    private NpcRenderer renderer;
    private FakeRepository repository;
    private RecordingRunner runner;
    private RecordingActionRunner actionRunner;
    private final AtomicLong now = new AtomicLong(0);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        packets = new FixedIdPackets();
        InlineScheduler rendererScheduler = new InlineScheduler();
        NpcViewSpawner spawner = new NpcViewSpawner(packets, new NoopLogger());
        renderer = new NpcRenderer(packets, spawner, rendererScheduler, 48.0, 16.0);
        repository = new FakeRepository();
        runner = new RecordingRunner();
        actionRunner = new RecordingActionRunner();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private NpcInteractionListener listener(Duration cooldown) {
        return new NpcInteractionListener(
                renderer, repository, runner, actionRunner, new InlineScheduler(), cooldown, now::get);
    }

    @Test
    void runsTheBoundCommandAsThePlayerByDefault() {
        PlayerMock player = renderNpcAndAddPlayer("guide", "warp spawn");
        NpcInteractionListener listener = listener(Duration.ofMillis(500));

        listener.onUseUnknownEntity(interact(player, EquipmentSlot.HAND));

        assertThat(runner.playerCommands).containsExactly("warp spawn");
        assertThat(runner.consoleCommands).isEmpty();
    }

    @Test
    void runsAConsolePrefixedCommandAsConsole() {
        PlayerMock player = renderNpcAndAddPlayer("guide", "[console] give {player} diamond 1");
        NpcInteractionListener listener = listener(Duration.ofMillis(500));

        listener.onUseUnknownEntity(interact(player, EquipmentSlot.HAND));

        assertThat(runner.consoleCommands).containsExactly("give " + player.getName() + " diamond 1");
        assertThat(runner.playerCommands).isEmpty();
    }

    @Test
    void substitutesThePlayerTokenForAPlayerCommand() {
        PlayerMock player = renderNpcAndAddPlayer("guide", "[player] msg {player} hi");
        NpcInteractionListener listener = listener(Duration.ofMillis(500));

        listener.onUseUnknownEntity(interact(player, EquipmentSlot.HAND));

        assertThat(runner.playerCommands).containsExactly("msg " + player.getName() + " hi");
    }

    @Test
    void cooldownBlocksARapidSecondClick() {
        PlayerMock player = renderNpcAndAddPlayer("guide", "warp spawn");
        NpcInteractionListener listener = listener(Duration.ofMillis(500));

        listener.onUseUnknownEntity(interact(player, EquipmentSlot.HAND));
        now.set(200); // still within the 500 ms window
        listener.onUseUnknownEntity(interact(player, EquipmentSlot.HAND));

        assertThat(runner.playerCommands).containsExactly("warp spawn");
    }

    @Test
    void aClickAfterTheCooldownRunsAgain() {
        PlayerMock player = renderNpcAndAddPlayer("guide", "warp spawn");
        NpcInteractionListener listener = listener(Duration.ofMillis(500));

        listener.onUseUnknownEntity(interact(player, EquipmentSlot.HAND));
        now.set(600); // past the 500 ms window
        listener.onUseUnknownEntity(interact(player, EquipmentSlot.HAND));

        assertThat(runner.playerCommands).containsExactly("warp spawn", "warp spawn");
    }

    @Test
    void theCooldownStampEvictsItselfOnceTheCooldownElapses() {
        PlayerMock player = renderNpcAndAddPlayer("guide", "warp spawn");
        NpcInteractionListener listener = listener(Duration.ofMillis(500));

        listener.onUseUnknownEntity(interact(player, EquipmentSlot.HAND));
        // A fresh click leaves exactly one tracked stamp.
        assertThat(listener.trackedClicks()).isEqualTo(1);

        now.set(600); // past the 500 ms window
        // The expired stamp drops out on its own: the map does not retain the (now idle) player/NPC pair.
        assertThat(listener.trackedClicks()).isZero();

        // And a click after expiry runs again (cooldown elapsed == evicted entry, same semantics as before).
        listener.onUseUnknownEntity(interact(player, EquipmentSlot.HAND));
        assertThat(runner.playerCommands).containsExactly("warp spawn", "warp spawn");
        assertThat(listener.trackedClicks()).isEqualTo(1);
    }

    @Test
    void aPerNpcCooldownOverridesTheGlobalDefault() {
        // The NPC carries a 1000 ms per-NPC override; the global default is 200 ms. A click at 300 ms (past the
        // global, inside the override) must still be blocked, proving the per-NPC value wins.
        PlayerMock player = server.addPlayer();
        Position at = com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs.toPosition(
                java.util.Objects.requireNonNull(player.getLocation(), "loc"));
        Npc npc = Npc.create(NpcName.of("guide"), at, null, Instant.ofEpochMilli(1_000))
                .withClickCommand("warp spawn")
                .withInteractionCooldownMillis(1_000);
        repository.save(npc);
        renderer.render(npc);
        NpcInteractionListener listener = listener(Duration.ofMillis(200));

        listener.onUseUnknownEntity(interact(player, EquipmentSlot.HAND));
        now.set(300); // past the 200 ms global, but inside the 1000 ms per-NPC override
        listener.onUseUnknownEntity(interact(player, EquipmentSlot.HAND));
        assertThat(runner.playerCommands).containsExactly("warp spawn");

        // Past the per-NPC window it runs again.
        now.set(1_100);
        listener.onUseUnknownEntity(interact(player, EquipmentSlot.HAND));
        assertThat(runner.playerCommands).containsExactly("warp spawn", "warp spawn");
    }

    @Test
    void doesNothingForAnNpcWithNoCommand() {
        PlayerMock player = renderNpcAndAddPlayer("guide", null);
        NpcInteractionListener listener = listener(Duration.ofMillis(500));

        listener.onUseUnknownEntity(interact(player, EquipmentSlot.HAND));

        assertThat(runner.playerCommands).isEmpty();
        assertThat(runner.consoleCommands).isEmpty();
    }

    @Test
    void ignoresAnUnknownEntityId() {
        PlayerMock player = renderNpcAndAddPlayer("guide", "warp spawn");
        NpcInteractionListener listener = listener(Duration.ofMillis(500));

        listener.onUseUnknownEntity(
                new PlayerUseUnknownEntityEvent(player, 999, false, EquipmentSlot.HAND, new Vector()));

        assertThat(runner.playerCommands).isEmpty();
    }

    @Test
    void doesNotRunTheClickCommandOnAttackAndIgnoresTheOffHand() {
        PlayerMock player = renderNpcAndAddPlayer("guide", "warp spawn");
        NpcInteractionListener listener = listener(Duration.ofMillis(500));

        // The single click command is right-click only, so an attack runs no command.
        listener.onUseUnknownEntity(
                new PlayerUseUnknownEntityEvent(player, ENTITY_ID, true, EquipmentSlot.HAND, new Vector()));
        // The off-hand fires its own duplicate event, which is ignored regardless of attack/interact.
        now.set(600);
        listener.onUseUnknownEntity(interact(player, EquipmentSlot.OFF_HAND));

        assertThat(runner.playerCommands).isEmpty();
        assertThat(runner.consoleCommands).isEmpty();
    }

    @Test
    void runsTheClickCommandThenTheMatchingActionChainOnRightClick() {
        ClickAction action = new ClickAction(ClickTrigger.RIGHT_CLICK, ClickActionType.MESSAGE, "hi");
        PlayerMock player = renderNpcWithActions("guide", "warp spawn", action);
        NpcInteractionListener listener = listener(Duration.ofMillis(500));

        listener.onUseUnknownEntity(interact(player, EquipmentSlot.HAND));

        assertThat(runner.playerCommands).containsExactly("warp spawn");
        assertThat(actionRunner.calls).containsExactly(new RunCall(List.of(action), false));
    }

    @Test
    void runsTheActionChainOnAttackWithTheAttackFlag() {
        ClickAction action = new ClickAction(ClickTrigger.LEFT_CLICK, ClickActionType.MESSAGE, "ouch");
        PlayerMock player = renderNpcWithActions("guide", null, action);
        NpcInteractionListener listener = listener(Duration.ofMillis(500));

        listener.onUseUnknownEntity(
                new PlayerUseUnknownEntityEvent(player, ENTITY_ID, true, EquipmentSlot.HAND, new Vector()));

        assertThat(runner.playerCommands).isEmpty();
        assertThat(actionRunner.calls).containsExactly(new RunCall(List.of(action), true));
    }

    @Test
    void doesNotInvokeTheActionRunnerWhenTheNpcHasNoActions() {
        PlayerMock player = renderNpcAndAddPlayer("guide", "warp spawn");
        NpcInteractionListener listener = listener(Duration.ofMillis(500));

        listener.onUseUnknownEntity(interact(player, EquipmentSlot.HAND));

        assertThat(actionRunner.calls).isEmpty();
    }

    private PlayerMock renderNpcAndAddPlayer(String name, @Nullable String command) {
        return renderNpcWithActions(name, command);
    }

    private PlayerMock renderNpcWithActions(String name, @Nullable String command, ClickAction... actions) {
        PlayerMock player = server.addPlayer();
        Position at = com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs.toPosition(
                java.util.Objects.requireNonNull(player.getLocation(), "loc"));
        Npc npc = Npc.create(NpcName.of(name), at, null, Instant.ofEpochMilli(1_000))
                .withClickCommand(command);
        for (ClickAction action : actions) {
            npc = npc.withActionAdded(action);
        }
        repository.save(npc);
        renderer.render(npc); // populates the entityId -> name map under ENTITY_ID
        return player;
    }

    private PlayerUseUnknownEntityEvent interact(Player player, EquipmentSlot hand) {
        return new PlayerUseUnknownEntityEvent(player, ENTITY_ID, false, hand, new Vector());
    }

    /** Fake packets handing out a single fixed entity id so the test knows which id the click carries. */
    private static final class FixedIdPackets implements com.uxplima.uxmlib.packet.npc.NpcPackets {
        @Override
        public int allocateEntityId() {
            return ENTITY_ID;
        }

        @Override
        public Object tabAdd(UUID profileId, String name, @Nullable TabSkin skin) {
            return new Object();
        }

        @Override
        public Object tabAdd(UUID profileId, String name, @Nullable TabSkin skin, boolean listed) {
            return new Object();
        }

        @Override
        public Object tabRemove(UUID profileId) {
            return new Object();
        }

        @Override
        public Object spawnPlayer(int entityId, UUID profileId, double x, double y, double z, float yaw, float pitch) {
            return new Object();
        }

        @Override
        public Object spawnEntity(
                int entityId,
                UUID entityUuid,
                String entityTypeKey,
                double x,
                double y,
                double z,
                float yaw,
                float pitch) {
            return new Object();
        }

        @Override
        public Object headLook(int entityId, float yaw) {
            return new Object();
        }

        @Override
        public Object bodyLook(int entityId, float yaw, float pitch) {
            return new Object();
        }

        @Override
        public Object teleport(int entityId, double x, double y, double z, float yaw, float pitch) {
            return new Object();
        }

        @Override
        public Object remove(int entityId) {
            return new Object();
        }

        @Override
        public Object equipment(int entityId, Map<com.uxplima.uxmlib.packet.npc.EquipmentSlot, ItemStack> items) {
            return new Object();
        }

        @Override
        public Object glow(int entityId, boolean glowing) {
            return new Object();
        }

        @Override
        public Object sharedFlags(int entityId, boolean onFire, boolean glowing, boolean invisible) {
            return new Object();
        }

        @Override
        public Object silent(int entityId, boolean silent) {
            return new Object();
        }

        @Override
        public Object team(
                String teamName,
                String memberName,
                @Nullable NamedColor color,
                boolean collidable,
                boolean hideNametag) {
            return new Object();
        }

        @Override
        public Object glowColor(String teamName, String memberName, @Nullable NamedColor color) {
            return new Object();
        }

        @Override
        public Object pose(int entityId, com.uxplima.uxmlib.packet.npc.NpcPose pose) {
            return new Object();
        }

        @Override
        public Object scale(int entityId, double scale) {
            return new Object();
        }

        @Override
        public Object baby(int entityId, boolean baby) {
            return new Object();
        }

        @Override
        public Object zombieBaby(int entityId, boolean baby) {
            return new Object();
        }

        @Override
        public Object piglinBaby(int entityId, boolean baby) {
            return new Object();
        }

        @Override
        public Object zoglinBaby(int entityId, boolean baby) {
            return new Object();
        }

        @Override
        public Object villagerData(int entityId, String type, String profession, int level) {
            return new Object();
        }

        @Override
        public Object slimeSize(int entityId, int size) {
            return new Object();
        }

        @Override
        public Object charged(int entityId, boolean charged) {
            return new Object();
        }

        @Override
        public Object horseVariant(int entityId, int color, int markings) {
            return new Object();
        }

        @Override
        public Object llamaVariant(int entityId, int variant) {
            return new Object();
        }

        @Override
        public Object sheepWool(int entityId, int color, boolean sheared) {
            return new Object();
        }

        @Override
        public Object wolfCollar(int entityId, int color) {
            return new Object();
        }

        @Override
        public Object shulkerColor(int entityId, int color) {
            return new Object();
        }

        @Override
        public Object shulkerPeek(int entityId, int peek) {
            return new Object();
        }

        @Override
        public Object shulkerAttachFace(int entityId, String face) {
            return new Object();
        }

        @Override
        public Object spinAttack(int entityId, boolean spinning) {
            return new Object();
        }

        @Override
        public Object sleepingPosition(int entityId, int x, int y, int z) {
            return new Object();
        }

        @Override
        public Object noSleepingPosition(int entityId) {
            return new Object();
        }

        @Override
        public Object pandaGene(int entityId, int gene) {
            return new Object();
        }

        @Override
        public Object goatScreaming(int entityId, boolean screaming) {
            return new Object();
        }

        @Override
        public Object allayDancing(int entityId, boolean dancing) {
            return new Object();
        }

        @Override
        public Object piglinDancing(int entityId, boolean dancing) {
            return new Object();
        }

        @Override
        public Object camelDash(int entityId, boolean dashing) {
            return new Object();
        }

        @Override
        public Object beeFlags(int entityId, boolean nectar, boolean rolling, boolean stung) {
            return new Object();
        }

        @Override
        public Object vexCharging(int entityId, boolean charging) {
            return new Object();
        }

        @Override
        public Object tropicalFishVariant(int entityId, int variantIndex) {
            return new Object();
        }

        @Override
        public Object armorStandFlags(
                int entityId, boolean small, boolean showArms, boolean noBasePlate, boolean marker) {
            return new Object();
        }

        @Override
        public Object armorStandPose(
                int entityId, com.uxplima.uxmlib.packet.npc.ArmorStandPart part, float x, float y, float z) {
            return new Object();
        }

        @Override
        public Object interactionSize(int entityId, float width, float height) {
            return new Object();
        }

        @Override
        public Object blockDisplayState(int entityId, org.bukkit.block.data.BlockData blockData) {
            return new Object();
        }

        @Override
        public Object itemDisplayItem(int entityId, org.bukkit.inventory.ItemStack item) {
            return new Object();
        }

        @Override
        public Object textDisplayText(int entityId, net.kyori.adventure.text.Component text) {
            return new Object();
        }

        @Override
        public Object displayScale(int entityId, float scale) {
            return new Object();
        }

        @Override
        public Object displayScale(int entityId, float x, float y, float z) {
            return new Object();
        }

        @Override
        public Object displayTranslation(int entityId, float x, float y, float z) {
            return new Object();
        }

        @Override
        public Object displayBillboard(int entityId, byte constraint) {
            return new Object();
        }

        @Override
        public Object textDisplayBackground(int entityId, int argb) {
            return new Object();
        }

        @Override
        public Object textDisplayLineWidth(int entityId, int lineWidth) {
            return new Object();
        }

        @Override
        public Object parrotVariant(int entityId, int variant) {
            return new Object();
        }

        @Override
        public Object axolotlVariant(int entityId, int variant) {
            return new Object();
        }

        @Override
        public Object foxType(int entityId, int type) {
            return new Object();
        }

        @Override
        public Object rabbitType(int entityId, int type) {
            return new Object();
        }

        @Override
        public Object catVariant(int entityId, String name) {
            return new Object();
        }

        @Override
        public Object frogVariant(int entityId, String name) {
            return new Object();
        }

        @Override
        public Object wolfVariant(int entityId, String name) {
            return new Object();
        }

        @Override
        public Object chickenVariant(int entityId, String name) {
            return new Object();
        }

        @Override
        public Object cowVariant(int entityId, String name) {
            return new Object();
        }

        @Override
        public Object pigVariant(int entityId, String name) {
            return new Object();
        }

        @Override
        public Object axolotlPlayingDead(int entityId, boolean playingDead) {
            return new Object();
        }

        @Override
        public Object raiderCelebrating(int entityId, boolean celebrating) {
            return new Object();
        }

        @Override
        public Object tameableSitting(int entityId, boolean sitting) {
            return new Object();
        }

        @Override
        public Object foxFlags(int entityId, boolean sitting, boolean sleeping, boolean crouching) {
            return new Object();
        }

        @Override
        public Object pandaEating(int entityId, boolean eating) {
            return new Object();
        }

        @Override
        public Object snifferState(int entityId, String state) {
            return new Object();
        }

        @Override
        public Object armadilloState(int entityId, String state) {
            return new Object();
        }

        @Override
        public Object usingItem(int entityId, boolean using, boolean offHand) {
            return new Object();
        }

        @Override
        public Object frozenTicks(int entityId, int ticks) {
            return new Object();
        }

        @Override
        public Object glowColorRemove(String teamName) {
            return new Object();
        }

        @Override
        public Object bundle(List<Object> built) {
            return new Object();
        }

        @Override
        public void send(Player viewer, Object packet) {
            // no-op: the interaction test does not assert on packets
        }
    }

    /** An in-memory repository keyed by NPC name. */
    private static final class FakeRepository implements NpcRepository {
        private final Map<String, Npc> byName = new LinkedHashMap<>();

        @Override
        public Optional<Npc> find(NpcName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<Npc> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(NpcName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(Npc npc) {
            byName.put(npc.name().value(), npc);
        }

        @Override
        public void delete(NpcName name) {
            byName.remove(name.value());
        }
    }

    /** Records which command went to console vs the player. */
    private static final class RecordingRunner implements ClickCommandRunner {
        private final List<String> consoleCommands = new ArrayList<>();
        private final List<String> playerCommands = new ArrayList<>();

        @Override
        public void runAsConsole(String command) {
            consoleCommands.add(command);
        }

        @Override
        public void runAsPlayer(Player player, String command) {
            playerCommands.add(command);
        }

        @Override
        public void runAsPlayerOp(Player player, String command) {
            playerCommands.add(command);
        }
    }

    /** One captured action-chain invocation: the actions passed and the attack flag they were run with. */
    private record RunCall(List<ClickAction> actions, boolean attack) {}

    /** Records each action-chain invocation so the listener's pass-through can be asserted. */
    private static final class RecordingActionRunner implements ClickActionRunner {
        private final List<RunCall> calls = new ArrayList<>();

        @Override
        public void run(Player viewer, List<ClickAction> actions, boolean attack) {
            calls.add(new RunCall(List.copyOf(actions), attack));
        }
    }

    /** Runs every scheduled hop inline so the dispatch completes within the test. */
    private static final class NoopLogger implements com.uxplima.uxmessentials.shared.application.port.Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

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
    }
}
