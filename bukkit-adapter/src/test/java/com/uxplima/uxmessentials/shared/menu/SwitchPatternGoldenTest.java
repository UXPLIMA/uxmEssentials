package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.DataActions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.PlayerDataPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.StringConditions;
import com.uxplima.uxmessentials.shared.adapter.outbound.meta.PlayerMeta;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerDataStore;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The proof that a state-switch button (one slot whose icon and actions change with a player-data flag) needs no
 * dedicated engine construct: it falls out of the view conditions and same-slot priority layering the engine already
 * has. Two items share slot 4 with opposite view conditions on {@code %data_value_mode%}. Priority layering drops the
 * item whose view fails and renders the visible one, and a click routes to <em>that</em> rendered item's own actions,
 * so the pair reads as a switch. Clicking the visible item flips the flag through the {@code data-set} action, and the
 * next render shows the other item. Everything here, the {@code equals-ignorecase} view condition, the {@code data-set}
 * action, the {@code %data_value_mode%} placeholder: is a shipped building block; nothing switch-specific was added.
 */
class SwitchPatternGoldenTest {

    private static final String HOCON = """
            rows = 1
            items {
              on {
                slots = ["4"], material = LIME_DYE, name = "On",
                view = ["equals-ignorecase:%data_value_mode% on"],
                click { left = ["data-set:mode off"] }
              }
              off {
                slots = ["4"], material = GRAY_DYE, name = "Off",
                view = ["equals-ignorecase:%data_value_mode% off"],
                click { left = ["data-set:mode on"] }
              }
            }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private Menus menus;
    private FakePlayerDataStore playerData;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");

        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        playerData = new FakePlayerDataStore();
        PlayerMeta playerMeta = new PlayerMeta(MockBukkit.createMockPlugin());
        Logger log = new NoopLogger();
        DataActions.register(engine.bindings(), playerData, playerMeta, log);
        PlayerDataPlaceholders.register(engine.bindings(), playerData, playerMeta);
        StringConditions.register(engine.bindings(), log);
        menus = engine.menus();
        menus.registerSpec("switch", new MenuSpecLoader().parse(HOCON));
        engine.installListener(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theOnItemShowsWhenTheFlagIsOnAndAClickFlipsItSoTheOffItemShowsNext() {
        playerData.set(player.getUniqueId(), "mode", "on");

        open();
        assertThat(materialAt(4))
                .as("with mode=on the on-item wins slot 4 (the off-item's view fails and it is dropped)")
                .isEqualTo(Material.LIME_DYE);

        click(4);
        assertThat(playerData.get(player.getUniqueId(), "mode"))
                .as("clicking the visible on-item runs its own action, flipping the flag to off")
                .contains("off");

        open();
        assertThat(materialAt(4))
                .as(
                        "re-rendered with the flipped flag, the same slot now shows the off-item, a switch, no new construct")
                .isEqualTo(Material.GRAY_DYE);
    }

    @Test
    void theOffItemShowsWhenTheFlagIsOff() {
        playerData.set(player.getUniqueId(), "mode", "off");

        open();

        assertThat(materialAt(4))
                .as("with mode=off only the off-item's view passes, so it is the one rendered and clickable")
                .isEqualTo(Material.GRAY_DYE);
    }

    private void open() {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), "switch", null);
    }

    private Material materialAt(int slot) {
        Inventory top = player.getOpenInventory().getTopInventory();
        return Objects.requireNonNull(top.getItem(slot), "item at slot " + slot).getType();
    }

    private void click(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** A minimal in-memory player-data store: the {@code data-set} action writes here and the placeholder reads it. */
    private static final class FakePlayerDataStore implements PlayerDataStore {

        private final Map<UUID, Map<String, String>> data = new ConcurrentHashMap<>();

        @Override
        public Optional<String> get(UUID player, String key) {
            return Optional.ofNullable(data.getOrDefault(player, Map.of()).get(key));
        }

        @Override
        public double number(UUID player, String key, double fallback) {
            return get(player, key).map(Double::parseDouble).orElse(fallback);
        }

        @Override
        public void set(UUID player, String key, String value) {
            data.computeIfAbsent(player, k -> new HashMap<>()).put(key, value);
        }

        @Override
        public double apply(UUID player, String key, NumericOp op, double operand) {
            throw new UnsupportedOperationException("not used by the switch pattern");
        }

        @Override
        public void remove(UUID player, String key) {
            Map<String, String> keys = data.get(player);
            if (keys != null) {
                keys.remove(key);
            }
        }

        @Override
        public Map<String, String> all(UUID player) {
            return Map.copyOf(data.getOrDefault(player, Map.of()));
        }
    }

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

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}
