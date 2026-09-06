package com.uxplima.uxmessentials.itemworld.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.command.ItemworldGroupACommands;
import com.uxplima.uxmessentials.itemworld.application.ItemworldConfig;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.application.port.ItemworldAudit;
import com.uxplima.uxmessentials.itemworld.domain.MobSpec;
import com.uxplima.uxmessentials.itemworld.domain.PurgeSelection;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLocator;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the itemworld command paths through the real Brigadier nodes against a real (mock)
 * server: {@code /give} validation (an over-cap amount is refused before any stack materialises, a valid amount
 * delivers and an unknown item is rejected), {@code /disposal} opening a window, and, the headline
 * sub-feature-group disable. A command whose group is switched off in {@code itemworld.conf} answering
 * {@code COMMAND_DISABLED} and mutating nothing.
 *
 * <p>The commands' {@code build()} nodes are registered into a Brigadier {@link CommandDispatcher} and executed
 * against a {@link CommandSourceStackMock} for the player, so this drives the exact gating/validation a live
 * server would. The scheduler is a synchronous double so the entity-bound effects are observable without
 * ticking Folia; the message sink records which {@link MessageKey} each path delivered.
 */
class ItemworldCommandPathTest {

    private ServerMock server;
    private PlayerMock player;
    private RecordingSink sink;
    private MutableConfig config;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        player.setOp(true);
        sink = new RecordingSink();
        config = new MutableConfig();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void giveValidationRefusesAnOverCapAmountAndDeliversAValidOne() {
        config.put("give-cap", 64);
        CommandDispatcher<CommandSourceStack> dispatcher = registerGroupA();

        execute(dispatcher, "give Alice diamond 100"); // 100 > the give-cap of 64
        assertThat(sink.keys).contains(ItemworldMessageKey.AMOUNT_OUT_OF_RANGE);
        assertThat(player.getInventory().contains(Material.DIAMOND)).isFalse(); // nothing materialised over the cap

        sink.keys.clear();
        execute(dispatcher, "give Alice diamond 5");
        assertThat(sink.keys).contains(ItemworldMessageKey.GIVE_GIVEN);
        assertThat(countOf(Material.DIAMOND)).isEqualTo(5);
    }

    @Test
    void giveRejectsAnUnknownItemId() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerGroupA();

        execute(dispatcher, "give Alice not_a_real_item 1");

        assertThat(sink.keys).contains(ItemworldMessageKey.UNKNOWN_ITEM);
        assertThat(player.getInventory().getContents())
                .allSatisfy(stack -> assertThat(stack).isNull());
    }

    @Test
    void giveallDeliversToEveryOnlinePlayer() {
        config.put("give-cap", 64);
        PlayerMock bob = server.addPlayer("Bob");
        CommandDispatcher<CommandSourceStack> dispatcher = registerGroupA();

        execute(dispatcher, "giveall diamond 5");

        assertThat(sink.keys).contains(ItemworldMessageKey.GIVEALL_DONE);
        assertThat(countOfFor(player, Material.DIAMOND)).isEqualTo(5);
        assertThat(countOfFor(bob, Material.DIAMOND)).isEqualTo(5);
    }

    @Test
    void giveallRejectsAnUnknownItemId() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerGroupA();

        execute(dispatcher, "giveall not_a_real_item 1");

        assertThat(sink.keys).contains(ItemworldMessageKey.UNKNOWN_ITEM);
        assertThat(sink.keys).doesNotContain(ItemworldMessageKey.GIVEALL_DONE);
        assertThat(player.getInventory().getContents())
                .allSatisfy(stack -> assertThat(stack).isNull());
    }

    @Test
    void giveallRefusesAnOverCapAmount() {
        config.put("give-cap", 64);
        CommandDispatcher<CommandSourceStack> dispatcher = registerGroupA();

        execute(dispatcher, "giveall diamond 100"); // 100 > the give-cap of 64

        assertThat(sink.keys).contains(ItemworldMessageKey.AMOUNT_OUT_OF_RANGE);
        assertThat(sink.keys).doesNotContain(ItemworldMessageKey.GIVEALL_DONE);
        assertThat(player.getInventory().contains(Material.DIAMOND)).isFalse();
    }

    @Test
    void disposalOpensAThrowawayWindow() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerGroupA();

        execute(dispatcher, "disposal");

        assertThat(sink.keys).contains(ItemworldMessageKey.DISPOSAL_OPENED);
        assertThat(player.getOpenInventory().getTopInventory().getSize()).isEqualTo(54);
    }

    @Test
    void fireworkAddsAColouredBallEffectToTheHeldRocket() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.FIREWORK_ROCKET));
        CommandDispatcher<CommandSourceStack> dispatcher = registerGroupA();

        execute(dispatcher, "firework red");

        assertThat(sink.keys).contains(ItemworldMessageKey.FIREWORK_STYLED);
        org.bukkit.inventory.meta.FireworkMeta meta = heldFireworkMeta();
        assertThat(meta.getEffects()).isNotEmpty();
        assertThat(meta.getEffects().get(0).getColors()).contains(org.bukkit.DyeColor.RED.getFireworkColor());
    }

    @Test
    void fireworkPowerSetsTheRocketFlightPower() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.FIREWORK_ROCKET));
        CommandDispatcher<CommandSourceStack> dispatcher = registerGroupA();

        execute(dispatcher, "firework power 2");

        assertThat(sink.keys).contains(ItemworldMessageKey.FIREWORK_POWER_SET);
        assertThat(heldFireworkMeta().getPower()).isEqualTo(2);
    }

    @Test
    void fireworkClearRemovesEffectsFromTheRocket() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.FIREWORK_ROCKET));
        CommandDispatcher<CommandSourceStack> dispatcher = registerGroupA();

        execute(dispatcher, "firework red");
        assertThat(heldFireworkMeta().getEffects()).isNotEmpty();

        sink.keys.clear();
        execute(dispatcher, "firework clear");

        assertThat(sink.keys).contains(ItemworldMessageKey.FIREWORK_CLEARED);
        assertThat(heldFireworkMeta().getEffects()).isEmpty();
    }

    @Test
    void fireworkRefusesAHandThatIsNotARocket() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND));
        CommandDispatcher<CommandSourceStack> dispatcher = registerGroupA();

        execute(dispatcher, "firework red");

        assertThat(sink.keys).contains(ItemworldMessageKey.NOT_A_FIREWORK);
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.DIAMOND);
    }

    private org.bukkit.inventory.meta.FireworkMeta heldFireworkMeta() {
        return (org.bukkit.inventory.meta.FireworkMeta)
                player.getInventory().getItemInMainHand().getItemMeta();
    }

    @Test
    void disablingTheCleanupGroupMakesDisposalAnswerDisabledAndDoNothing() {
        config.put("groups.cleanup.enabled", false); // switch off the whole cleanup sub-feature group
        CommandDispatcher<CommandSourceStack> dispatcher = registerGroupA();

        execute(dispatcher, "disposal");

        // The only message is the disabled notice; the disposal window never opened (no DISPOSAL_OPENED).
        assertThat(sink.keys).containsExactly(ItemworldMessageKey.COMMAND_DISABLED);
        assertThat(sink.keys).doesNotContain(ItemworldMessageKey.DISPOSAL_OPENED);
    }

    @Test
    void disablingASingleCommandShadowsItWhileTheGroupStaysOn() {
        config.put("commands.disposal.disabled", true); // per-command disable, group still on
        CommandDispatcher<CommandSourceStack> dispatcher = registerGroupA();

        execute(dispatcher, "disposal");

        assertThat(sink.keys).containsExactly(ItemworldMessageKey.COMMAND_DISABLED);
    }

    private CommandDispatcher<CommandSourceStack> registerGroupA() {
        ItemworldServices services = new ItemworldServices(
                kernel(), new NoopAudit(), ItemworldConfig.from(config), GuiLayout.storageDefault(6));
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        for (CommandRegistration command : ItemworldGroupACommands.all(services, null, null)) {
            dispatcher.getRoot().addChild(command.build());
        }
        return dispatcher;
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private int countOf(Material material) {
        return countOfFor(player, material);
    }

    private int countOfFor(PlayerMock who, Material material) {
        int total = 0;
        for (ItemStack stack : who.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private KernelPorts kernel() {
        return new KernelPorts(
                new SyncScheduler(),
                new AllowAllPermissions(),
                new NoCooldowns(),
                new NoWarmups(),
                new KeyMessages(),
                sink,
                new NoPlayerLookup(),
                new NoWorldLookup(),
                new NoPlayerLocator(),
                new NoEvents(),
                new NoopLogger());
    }

    /** A map-backed {@link ConfigStore} scoped to {@code modules.itemworld} so tests flip group/command flags. */
    private static final class MutableConfig implements ConfigStore {
        private final Map<String, Object> values = new HashMap<>();

        void put(String path, Object value) {
            values.put(path, value);
        }

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }
    }

    /** Records each delivered key so a path's outcome is asserted by the message it produced. */
    private static final class RecordingSink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // renderedText is the key() string (see KeyMessages); the key list is what tests assert on
        }
    }

    /** Resolves a key to its own string and records it on the sink for assertions. */
    private final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            sink.keys.add(key);
            return key.key();
        }
    }

    /** Runs scheduled work inline so entity-bound effects are observable without ticking Folia. */
    private static final class SyncScheduler implements Scheduler {
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

    private static final class AllowAllPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return true;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who,
                QuotaFamily family,
                com.uxplima.uxmessentials.shared.domain.@org.jspecify.annotations.Nullable WorldRef world,
                long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class NoCooldowns implements Cooldowns {
        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<com.uxplima.uxmessentials.shared.domain.Unit, Duration>
                check(PlayerRef who, CooldownKind kind) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<com.uxplima.uxmessentials.shared.domain.Unit, Duration>
                checkLabel(PlayerRef who, String label) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class NoWarmups implements Warmups {
        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            onComplete.run();
            return new Warmups.CompletedWarmup(who);
        }
    }

    private static final class NoPlayerLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    private static final class NoWorldLookup implements WorldLookup {
        @Override
        public Optional<com.uxplima.uxmessentials.shared.domain.WorldRef> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<com.uxplima.uxmessentials.shared.domain.WorldRef> findByUid(UUID uid) {
            return Optional.empty();
        }
    }

    private static final class NoPlayerLocator implements PlayerLocator {
        @Override
        public Optional<Position> locate(PlayerRef who) {
            return Optional.empty();
        }
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class NoopAudit implements ItemworldAudit {
        @Override
        public void gave(PlayerRef actor, PlayerRef target, String itemKey, int amount) {}

        @Override
        public void spawnedMob(PlayerRef actor, MobSpec spec, int spawned) {}

        @Override
        public void retypedSpawner(PlayerRef actor, String mobType) {}

        @Override
        public void killed(PlayerRef actor, String target) {}

        @Override
        public void butchered(PlayerRef actor, PurgeSelection selection, int removed) {}

        @Override
        public void killedAll(PlayerRef actor, PurgeSelection selection, int removed) {}

        @Override
        public void removed(PlayerRef actor, PurgeSelection selection, int removed) {}

        @Override
        public void struckLightning(PlayerRef actor, Optional<PlayerRef> target) {}

        @Override
        public void launchedFireball(PlayerRef actor) {}

        @Override
        public void firedKittycannon(PlayerRef actor) {}

        @Override
        public void threwAntioch(PlayerRef actor) {}

        @Override
        public void firedBeezooka(PlayerRef actor) {}

        @Override
        public void brokeBlock(PlayerRef actor, String blockType) {}

        @Override
        public void grewTree(PlayerRef actor, String type) {}

        @Override
        public void nuked(PlayerRef actor, Optional<PlayerRef> target) {}
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
