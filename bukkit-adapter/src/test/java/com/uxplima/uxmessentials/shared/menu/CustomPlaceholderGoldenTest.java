package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.custommenus.adapter.CustomMenuLoader;
import com.uxplima.uxmessentials.custommenus.adapter.CustomPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.PlayerDataPlaceholders;
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
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * End-to-end golden of the global operator-defined {@code menus/placeholders.conf} through the real
 * {@link CustomMenuLoader} and {@link Menus} open path. A menu whose item names a custom {@code %welcome%} (defined as
 * {@code "Hi %player%"}) and whose lore names a custom {@code %doubled%} (defined as
 * {@code "{math: %data_number_coins% * 2}"}) renders against a seeded data store (coins=50): the name shows the inner
 * built-in resolved and the lore shows the evaluated product, proving the custom substitute resolves the inner token
 * while the renderer's outer pass owns the math. It also proves {@code placeholders.conf} is not swept in as a menu,
 * and that a {@code /menu reload}, a second {@link CustomMenuLoader#loadFrom}, picks up an edited definition.
 */
class CustomPlaceholderGoldenTest {

    private static final String PLACEHOLDERS = """
            placeholders {
              welcome = "Hi %player%"
              doubled = "{math: %data_number_coins% * 2}"
            }
            """;

    private static final String MENU = """
            rows = 1
            items {
              panel {
                slots = ["0"]
                material = "PAPER"
                name = "%welcome%"
                lore = ["%doubled%"]
              }
            }
            """;

    private ServerMock server;
    private PlayerMock viewer;
    private Menus menus;
    private CustomMenuLoader loader;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = server.addPlayer("Viewer");

        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        menus = engine.menus();
        // The built-in %player% and the data reader %data_number_coins% a custom template references, registered
        // before the custom fallback so the custom family is claimed last: the production ordering.
        MenuVocabulary.registerPlaceholders(engine.bindings());
        FakePlayerDataStore playerData = new FakePlayerDataStore();
        playerData.set(viewer.getUniqueId(), "coins", "50");
        PlayerDataPlaceholders.register(engine.bindings(), playerData, new PlayerMeta(MockBukkit.createMockPlugin()));

        CustomPlaceholders customPlaceholders = new CustomPlaceholders(engine.bindings());
        loader = new CustomMenuLoader(
                new MenuSpecLoader(), engine.bindings(), menus, new NoopLogger(), customPlaceholders);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aCustomPlaceholderRendersThroughTheOpenPathAndTheFileIsNotAMenu(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("placeholders.conf"), PLACEHOLDERS);
        Files.writeString(dir.resolve("greet.conf"), MENU);

        CustomMenuLoader.LoadResult result = loader.loadFrom(dir);
        assertThat(result.loadedNames())
                .as("placeholders.conf is a custom-placeholder file, not a menu, so only the real menu registers")
                .containsExactly("greet");

        open("greet");
        ItemStack item = topItem();
        assertThat(plainName(item))
                .as("the custom %welcome% resolves its inner %player%")
                .isEqualTo("Hi Viewer");
        assertThat(plainLore(item))
                .as("the custom %doubled% substitutes %data_number_coins%; the outer render pass evaluates the math")
                .containsExactly("100");
    }

    @Test
    void aMenuReloadPicksUpAnEditedPlaceholderValue(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("placeholders.conf"), PLACEHOLDERS);
        Files.writeString(dir.resolve("greet.conf"), MENU);

        loader.loadFrom(dir);
        open("greet");
        assertThat(plainName(topItem())).isEqualTo("Hi Viewer");

        Files.writeString(dir.resolve("placeholders.conf"), """
                placeholders {
                  welcome = "Hey %player%"
                  doubled = "{math: %data_number_coins% * 2}"
                }
                """);
        loader.loadFrom(dir);
        open("greet");
        assertThat(plainName(topItem()))
                .as("a second loadFrom (a /menu reload) re-reads the edited placeholders.conf")
                .isEqualTo("Hey Viewer");
    }

    private void open(String id) {
        menus.open(new PlayerRef(viewer.getUniqueId(), viewer.getName()), id, null);
    }

    private ItemStack topItem() {
        Inventory top = viewer.getOpenInventory().getTopInventory();
        return Objects.requireNonNull(top.getItem(0), "item at slot 0");
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    private static List<String> plainLore(ItemStack item) {
        // The body only: the title line the canon puts above it is asserted where the title is asserted.
        return TileText.body(item).stream()
                .map(line -> PlainTextComponentSerializer.plainText().serialize(line))
                .toList();
    }

    /** An in-memory {@link PlayerDataStore} whose reads mirror the real store; only {@code get}/{@code number} matter here. */
    private static final class FakePlayerDataStore implements PlayerDataStore {

        private final Map<UUID, Map<String, String>> data = new ConcurrentHashMap<>();

        @Override
        public Optional<String> get(UUID player, String key) {
            return Optional.ofNullable(data.getOrDefault(player, Map.of()).get(key));
        }

        @Override
        public double number(UUID player, String key, double fallback) {
            Optional<String> raw = get(player, key);
            if (raw.isEmpty()) {
                return fallback;
            }
            try {
                return Double.parseDouble(raw.get().trim());
            } catch (NumberFormatException notANumber) {
                return fallback;
            }
        }

        @Override
        public void set(UUID player, String key, String value) {
            data.computeIfAbsent(player, k -> new HashMap<>()).put(key, value);
        }

        @Override
        public double apply(UUID player, String key, NumericOp op, double operand) {
            throw new UnsupportedOperationException("reads only");
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

    /** A synchronous scheduler that runs every hop inline so the open path completes within the test call. */
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

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Swallows the loader's SLF4J-style lines; the golden asserts on the rendered inventory, not the log. */
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
