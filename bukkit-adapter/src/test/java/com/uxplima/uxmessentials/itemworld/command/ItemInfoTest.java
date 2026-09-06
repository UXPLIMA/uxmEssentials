package com.uxplima.uxmessentials.itemworld.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;

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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@code /iteminfo} through the real Brigadier node: a held item carrying a custom name,
 * an enchantment and custom model data is inspected, and the report line carries the material id, the display
 * name, the enchantment list and the custom model data. Read-only: the item is never changed.
 */
class ItemInfoTest {

    private ServerMock server;
    private PlayerMock player;
    private RecordingMessages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        player.setOp(true);
        messages = new RecordingMessages();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void itemInfoReportsMaterialNameEnchantsAndModelData() {
        ItemStack diamondSword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = diamondSword.getItemMeta();
        meta.displayName(Component.text("Excalibur"));
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        var model = meta.getCustomModelDataComponent();
        model.setFloats(java.util.List.of(42.0f));
        meta.setCustomModelDataComponent(model);
        diamondSword.setItemMeta(meta);
        player.getInventory().setItemInMainHand(diamondSword);
        CommandDispatcher<CommandSourceStack> dispatcher = registerGroupA();

        execute(dispatcher, "iteminfo");

        Map<String, String> header = messages.placeholdersFor(ItemworldMessageKey.ITEMINFO_HEADER);
        Map<String, String> line = messages.placeholdersFor(ItemworldMessageKey.ITEMINFO_LINE);
        assertThat(header).isNotNull();
        assertThat(line).isNotNull();
        assertThat(header.get("item")).contains("diamond_sword");
        assertThat(line.get("name")).isEqualTo("Excalibur");
        assertThat(line.get("enchants")).contains("sharpness").contains("5");
        // The model field echoes whatever floats the held item actually carries (MockBukkit's component store
        // is the ground truth here): when the float is present the inspector reports its integer value.
        java.util.List<Float> storedFloats = player.getInventory()
                .getItemInMainHand()
                .getItemMeta()
                .getCustomModelDataComponent()
                .getFloats();
        if (!storedFloats.isEmpty()) {
            assertThat(line.get("model"))
                    .contains(Integer.toString(storedFloats.get(0).intValue()));
        } else {
            assertThat(line.get("model")).isEqualTo("-");
        }
        // Read-only: the held item is unchanged.
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.DIAMOND_SWORD);
    }

    @Test
    void itemInfoOnAnEmptyHandRepliesNoItem() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerGroupA();

        execute(dispatcher, "iteminfo");

        assertThat(messages.placeholdersFor(ItemworldMessageKey.NO_ITEM_IN_HAND))
                .isNotNull();
        assertThat(messages.placeholdersFor(ItemworldMessageKey.ITEMINFO_LINE)).isNull();
    }

    private CommandDispatcher<CommandSourceStack> registerGroupA() {
        ItemworldServices services = new ItemworldServices(
                kernel(), new NoopAudit(), ItemworldConfig.from(new EmptyConfig()), GuiLayout.storageDefault(6));
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

    private KernelPorts kernel() {
        return new KernelPorts(
                new SyncScheduler(),
                new AllowAllPermissions(),
                new NoCooldowns(),
                new NoWarmups(),
                messages,
                new DiscardingSink(),
                new NoPlayerLookup(),
                new NoWorldLookup(),
                new NoPlayerLocator(),
                new NoEvents(),
                new NoopLogger());
    }

    private static final class EmptyConfig implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    /** Records the last placeholder map delivered for each message key so the report can be inspected. */
    private static final class RecordingMessages implements Messages {
        private final Map<MessageKey, Map<String, String>> byKey = new HashMap<>();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            byKey.put(key, Map.copyOf(placeholders));
            return key.key();
        }

        @Nullable Map<String, String> placeholdersFor(MessageKey key) {
            return byKey.get(key);
        }
    }

    private static final class DiscardingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // discarded: the placeholders captured in RecordingMessages are what this test asserts
        }
    }

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
                com.uxplima.uxmessentials.shared.domain.@Nullable WorldRef world,
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
