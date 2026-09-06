package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.PlayerDataPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.meta.PlayerMeta;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
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
 * End-to-end golden of a menu's own {@code placeholders {}} block through the real {@link Menus} open path. The
 * greet menu defines {@code greeting = "Hi %player%"}, {@code doubled = "{math: %data_number_coins% * 2}"} and,
 * deliberately, {@code player = "OVERRIDDEN"}. With local-first resolution the {@code %greeting%} name expands its
 * inner {@code %player%} against the LOCAL override (not the built-in viewer name), so the name reads
 * {@code "Hi OVERRIDDEN"}; the {@code %doubled%} lore substitutes {@code %data_number_coins%} (coins=50) and the
 * renderer's outer math pass evaluates it to {@code "100"}; and a direct {@code %player%} lore line resolves to the
 * same {@code "OVERRIDDEN"}. The unambiguous demonstration that a local token overrides a built-in for this menu
 * alone. A second menu that declares no {@code placeholders {}} block still renders {@code %player%} as the real
 * built-in viewer name, proving no regression.
 */
class LocalPlaceholderGoldenTest {

    private static final String GREET = """
            rows = 1
            placeholders {
              greeting = "Hi %player%"
              doubled = "{math: %data_number_coins% * 2}"
              player = "OVERRIDDEN"
            }
            items {
              panel {
                slots = ["0"]
                material = "PAPER"
                name = "%greeting%"
                lore = ["%doubled%", "%player%"]
              }
            }
            """;

    private static final String PLAIN = """
            rows = 1
            items {
              panel {
                slots = ["0"]
                material = "PAPER"
                name = "%player%"
              }
            }
            """;

    private ServerMock server;
    private PlayerMock viewer;
    private Menus menus;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = server.addPlayer("Viewer");

        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        menus = engine.menus();
        MenuVocabulary.registerPlaceholders(engine.bindings());
        FakePlayerDataStore playerData = new FakePlayerDataStore();
        playerData.set(viewer.getUniqueId(), "coins", "50");
        PlayerDataPlaceholders.register(engine.bindings(), playerData, new PlayerMeta(MockBukkit.createMockPlugin()));

        MenuSpecLoader loader = new MenuSpecLoader();
        menus.registerSpec("greet", loader.parse(GREET));
        menus.registerSpec("plain", loader.parse(PLAIN));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aLocalTokenOverridesTheBuiltinAndNestsLocalFirst() {
        open("greet");
        ItemStack item = topItem();

        assertThat(plainName(item))
                .as("%greeting% expands its inner %player% against the local override, not the built-in viewer name")
                .isEqualTo("Hi OVERRIDDEN");
        assertThat(plainLore(item))
                .as("%doubled% substitutes %data_number_coins%; the outer render pass evaluates the math to 100, and "
                        + "a direct %player% resolves local-first to the override")
                .containsExactly("100", "OVERRIDDEN");
    }

    @Test
    void aMenuWithoutALocalBlockStillRendersTheBuiltin() {
        open("plain");

        assertThat(plainName(topItem()))
                .as("a menu declaring no placeholders {} block resolves %player% through the built-in registry")
                .isEqualTo("Viewer");
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
}
