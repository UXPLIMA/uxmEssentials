package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.RequirementConditions;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.Currencies;
import com.uxplima.uxmessentials.shared.adapter.outbound.meta.PlayerMeta;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
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
 * The proof that a valued requirement written as an {@code id:value} token in a config gates an item: the coupled
 * reachability fix and the requirement pack, end to end. The parser leaves {@code has-empty-slots:1} whole (it is
 * registry-blind), so before the shared {@code Ref.resolve} fix this menu would be rejected at startup validation and,
 * even if it loaded, the whole token would miss the condition registry at render time. Here the real
 * {@link MenuSpecLoader}, {@link MenuBindings#validate} and {@link MenuRenderer} run against the live registry, so the
 * token both passes validation and gates the item's visibility on the viewer's real inventory.
 */
class RequirementConditionReachabilityGoldenTest {

    private static final String GATED_HOCON = """
            rows = 1
            items {
              gated { slot = 0, material = DIAMOND, name = "x", view = ["has-empty-slots:1"] }
            }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private MenuBindings bindings;
    private Menus menus;
    private MenuSpec spec;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");

        Logger log = new NoopLogger();
        bindings = new MenuBindings();
        Currencies currencies = new Currencies(() -> null, log, "vault");
        RequirementConditions.register(bindings, currencies, new PlayerMeta(plugin), log);

        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        menus = new Menus(renderer, scheduler, bindings.lists());
        spec = new MenuSpecLoader().parse(GATED_HOCON);
        menus.registerSpec("gated", spec);
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theValuedViewConditionPassesStartupValidation() {
        // The registry-aware split makes has-empty-slots:1 count as known because its head has-empty-slots is
        // registered: without the fix, validate would report the whole token as missing and the menu would be skipped.
        assertThat(bindings.validate(List.of(spec))).isEmpty();
    }

    @Test
    void theItemRendersWhenTheViewerHasAnEmptySlot() {
        // A fresh viewer's storage is all empty, so has-empty-slots:1 passes and the item is drawn.
        open();

        assertThat(topInventory().getItem(0))
                .as("the gated item is visible when the view condition passes")
                .isNotNull()
                .extracting(ItemStack::getType)
                .isEqualTo(Material.DIAMOND);
    }

    @Test
    void theItemIsHiddenWhenTheInventoryIsFull() {
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.STONE));
        }

        open();

        assertThat(topInventory().getItem(0))
                .as("with no empty slot the view condition fails, so the item is hidden")
                .isNull();
    }

    private void open() {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), "gated", null);
    }

    private Inventory topInventory() {
        return player.getOpenInventory().getTopInventory();
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
