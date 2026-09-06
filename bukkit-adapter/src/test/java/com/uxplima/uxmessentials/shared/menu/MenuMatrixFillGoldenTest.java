package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
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
 * End-to-end proof that the two loader-level conveniences render as ordinary items through a live open. A real
 * {@link MenuSpecLoader} folds a {@code layout} grid and a {@code fill-item} into plain slotted items, and a real
 * {@link Menus} paints them, so the assertions land on the live top inventory: a fill icon in every empty slot, the
 * real items untouched, and, with a grid, the border character exactly where the drawing placed it.
 */
class MenuMatrixFillGoldenTest {

    private ServerMock server;
    private PlayerMock player;
    private Menus menus;
    private MenuSpecLoader loader;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Viewer");

        MenuBindings bindings = new MenuBindings();
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        menus = new Menus(renderer, new InlineScheduler(), bindings.lists());
        loader = new MenuSpecLoader();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aFillItemPaintsEveryEmptySlotWithoutOverwritingTheRealItem() {
        Inventory inv = open("filled", """
                rows = 3
                items { real { slot = 13, material = DIAMOND } }
                fill-item { material = BLACK_STAINED_GLASS_PANE, name = " " }
                """);

        assertThat(type(inv, 13)).as("the real item keeps its slot").isEqualTo(Material.DIAMOND);
        assertThat(type(inv, 0))
                .as("an empty slot is painted with the fill icon")
                .isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
        assertThat(type(inv, 26)).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Test
    void aLayoutBorderAndAFillTogetherPaintEverySlot() {
        Inventory inv = open("bordered", """
                layout = [
                  "GGGGGGGGG"
                  "G.......G"
                  "GGGGGGGGG"
                ]
                items { G { material = GRAY_STAINED_GLASS_PANE, name = " " } }
                fill-item { material = BLACK_STAINED_GLASS_PANE, name = " " }
                """);

        assertThat(type(inv, 0)).as("the grid character paints the border").isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
        assertThat(type(inv, 9)).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
        assertThat(type(inv, 13))
                .as("the fill paints the interior the border left empty")
                .isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 27; slot++) {
            assertThat(inv.getItem(slot))
                    .as("no cell is left empty at slot " + slot)
                    .isNotNull();
        }
    }

    private Inventory open(String id, String hocon) {
        menus.registerSpec(id, loader.parse(hocon));
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), id, null);
        return player.getOpenInventory().getTopInventory();
    }

    private static Material type(Inventory inv, int slot) {
        return Objects.requireNonNull(inv.getItem(slot), "an item renders at slot " + slot)
                .getType();
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Runs every scheduled hop inline so the open completes on the test thread. */
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
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}
