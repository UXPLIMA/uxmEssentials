package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

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
 * The proof that a menu spec's {@code inventory-type} opens the window in a non-chest shape, end to end: a real
 * {@link MenuSpecLoader} parses the spec and a real {@link Menus} opens it, and the live top inventory is asserted to
 * carry the requested Bukkit {@link InventoryType} and size. It also pins the two robustness guarantees the feature
 * rests on. A spec slot that overflows a small window (slot 8 in a five-slot hopper) is skipped rather than crashing
 * the open, and an unknown type falls back to the default {@code rows}-based chest. Plus the unchanged chest default
 * for a spec that names no type, so every existing menu opens exactly as before.
 */
class InventoryTypeMenuGoldenTest {

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
        menus = new Menus(renderer, new SyncScheduler(), bindings.lists());
        loader = new MenuSpecLoader();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aHopperTypeOpensAFiveSlotHopperAndSkipsAnOverflowingSlot() {
        // Items sit at slots 0..4 (the hopper's five slots) plus one at slot 8, which no hopper can hold; the open
        // must survive that overflow by skipping the stray slot rather than throwing an out-of-bounds error.
        Inventory inv = open("hop", """
                inventory-type = "hopper"
                items {
                  row { slots = ["0-4"], material = DIAMOND, name = "x" }
                  stray { slot = 8, material = EMERALD, name = "y" }
                }
                """);

        assertThat(inv.getType()).isEqualTo(InventoryType.HOPPER);
        assertThat(inv.getSize()).isEqualTo(5);
        assertThat(item(inv, 4)).extracting(ItemStack::getType).isEqualTo(Material.DIAMOND);
        assertThat(item(inv, 0)).extracting(ItemStack::getType).isEqualTo(Material.DIAMOND);
    }

    @Test
    void aDispenserTypeOpensANineSlotDispenser() {
        Inventory inv = open("dis", typed("dispenser"));

        assertThat(inv.getType()).isEqualTo(InventoryType.DISPENSER);
        assertThat(inv.getSize()).isEqualTo(9);
        assertThat(item(inv, 0)).extracting(ItemStack::getType).isEqualTo(Material.DIAMOND);
    }

    @Test
    void aDropperTypeOpensANineSlotDropper() {
        Inventory inv = open("dro", typed("dropper"));

        assertThat(inv.getType()).isEqualTo(InventoryType.DROPPER);
        assertThat(inv.getSize()).isEqualTo(9);
    }

    @Test
    void aShulkerTypeOpensATwentySevenSlotShulkerBox() {
        Inventory inv = open("shu", typed("shulker"));

        assertThat(inv.getType()).isEqualTo(InventoryType.SHULKER_BOX);
        assertThat(inv.getSize()).isEqualTo(27);
        assertThat(item(inv, 0)).extracting(ItemStack::getType).isEqualTo(Material.DIAMOND);
    }

    @Test
    void aFurnaceTypeOpensAFurnace() {
        Inventory inv = open("fur", typed("furnace"));

        assertThat(inv.getType()).isEqualTo(InventoryType.FURNACE);
        assertThat(item(inv, 0)).extracting(ItemStack::getType).isEqualTo(Material.DIAMOND);
    }

    @Test
    void aSpecWithNoInventoryTypeOpensARowsBasedChestAsBefore() {
        Inventory inv = open("chest", """
                rows = 3
                items { a { slot = 0, material = DIAMOND, name = "x" } }
                """);

        assertThat(inv.getType()).isEqualTo(InventoryType.CHEST);
        assertThat(inv.getSize()).isEqualTo(27);
        assertThat(item(inv, 0)).extracting(ItemStack::getType).isEqualTo(Material.DIAMOND);
    }

    @Test
    void anUnknownInventoryTypeFallsBackToAChestWithoutThrowing() {
        Inventory inv = open("bad", """
                inventory-type = "banana"
                rows = 1
                items { a { slot = 0, material = DIAMOND, name = "x" } }
                """);

        assertThat(inv.getType())
                .as("an unrecognised type is a soft miss: the menu opens as the rows-based chest")
                .isEqualTo(InventoryType.CHEST);
        assertThat(inv.getSize()).isEqualTo(9);
        assertThat(item(inv, 0)).extracting(ItemStack::getType).isEqualTo(Material.DIAMOND);
    }

    /** A minimal one-item spec of the given type, its single item at slot 0 (present in every non-chest window). */
    private static String typed(String type) {
        return "inventory-type = \"" + type + "\"\nitems { a { slot = 0, material = DIAMOND, name = \"x\" } }";
    }

    private Inventory open(String id, String hocon) {
        menus.registerSpec(id, loader.parse(hocon));
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), id, null);
        return player.getOpenInventory().getTopInventory();
    }

    private static ItemStack item(Inventory inv, int slot) {
        return inv.getItem(slot);
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
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

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}
