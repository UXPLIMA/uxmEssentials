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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.StringConditions;
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
 * The proof that a valued string condition written as an {@code id:value} token in a config gates an item, and that
 * its operands are placeholder-expanded before the comparison, end to end through the real {@link MenuSpecLoader},
 * {@link MenuBindings#validate} and {@link MenuRenderer}. A test {@code %who%} placeholder resolves to
 * {@code SteveTheKing}, so a spec whose item is gated on {@code contains:%who% Steve} renders (the resolved name
 * contains the needle) while the same gate with the needle flipped to {@code Notch} hides it. The parser leaves the
 * whole token intact (it is registry-blind); the runtime's registry-aware split re-keys it to the head
 * {@code contains}, which is exactly what lets it both pass validation and evaluate at render time.
 */
class StringConditionReachabilityGoldenTest {

    private static final String VISIBLE_HOCON = """
            rows = 1
            items {
              gated { slot = 0, material = DIAMOND, name = "x", view = ["contains:%who% Steve"] }
            }
            """;

    private static final String HIDDEN_HOCON = """
            rows = 1
            items {
              gated { slot = 0, material = DIAMOND, name = "x", view = ["contains:%who% Notch"] }
            }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private MenuBindings bindings;
    private Menus menus;
    private MenuSpec visibleSpec;
    private MenuSpec hiddenSpec;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");

        Logger log = new NoopLogger();
        bindings = new MenuBindings();
        bindings.placeholder("who", ctx -> "SteveTheKing");
        StringConditions.register(bindings, log);

        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        menus = new Menus(renderer, scheduler, bindings.lists());
        MenuSpecLoader loader = new MenuSpecLoader();
        visibleSpec = loader.parse(VISIBLE_HOCON);
        hiddenSpec = loader.parse(HIDDEN_HOCON);
        menus.registerSpec("visible", visibleSpec);
        menus.registerSpec("hidden", hiddenSpec);
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
        // The registry-aware split makes contains:%who% Steve count as known because its head contains is registered.
        assertThat(bindings.validate(List.of(visibleSpec, hiddenSpec))).isEmpty();
    }

    @Test
    void theItemRendersWhenTheExpandedOperandContainsTheNeedle() {
        // %who% resolves to SteveTheKing, which contains Steve, so the view condition passes and the item is drawn.
        open("visible");

        assertThat(topInventory().getItem(0))
                .as("the gated item is visible when the expanded operand contains the needle")
                .isNotNull()
                .extracting(ItemStack::getType)
                .isEqualTo(Material.DIAMOND);
    }

    @Test
    void theItemIsHiddenWhenTheExpandedOperandLacksTheNeedle() {
        // SteveTheKing does not contain Notch, so the view condition fails and the item is hidden.
        open("hidden");

        assertThat(topInventory().getItem(0))
                .as("with the needle flipped the view condition fails, so the item is hidden")
                .isNull();
    }

    private void open(String menu) {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), menu, null);
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
