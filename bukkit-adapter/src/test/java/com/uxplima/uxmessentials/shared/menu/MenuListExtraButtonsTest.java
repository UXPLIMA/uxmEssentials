package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.EntityListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
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
 * Coverage of the engine list's {@code extraButtons} completion: an {@link EntityListSpec} may carry fixed pre-built buttons
 * beyond its single create/action pair, so a caller that needs three or more non-entity buttons (the shared player
 * picker's offline-name button plus its two optional footer buttons) is not capped at two. The test opens a list with
 * two extra buttons through {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus#openList}, asserts
 * each renders at its slot with its baked icon, and that a click on one through the one {@link
 * com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener} runs that button's handler with the
 * live viewer: proving both the paint and the click-routing seam the renderer records onto the list state.
 */
class MenuListExtraButtonsTest {

    private static final int FIRST_EXTRA_SLOT = 47;
    private static final int SECOND_EXTRA_SLOT = 51;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private TestMenuEngine engine;
    private final List<String> clicks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        engine.installListener(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void extraButtonsRenderAtTheirSlotsWithTheirIcons() {
        engine.menus().openList(viewer, spec());

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(FIRST_EXTRA_SLOT).getType()).isEqualTo(Material.NAME_TAG);
        assertThat(inv.getItem(SECOND_EXTRA_SLOT).getType()).isEqualTo(Material.BOOK);
    }

    @Test
    void clickingAnExtraButtonRunsItsHandlerWithTheViewer() {
        engine.menus().openList(viewer, spec());

        fireClick(SECOND_EXTRA_SLOT);

        assertThat(clicks).containsExactly("second");
    }

    @Test
    void theWindowIsMenuBacked() {
        engine.menus().openList(viewer, spec());
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    private EntityListSpec spec() {
        return EntityListSpec.builder()
                .title(Component.text("list"))
                .rows(6)
                .contentSlots(List.of(0, 1, 2))
                .navigation(45, 53, Material.ARROW)
                .navNames(Component.text("prev"), Component.text("next"))
                .filler(Material.GRAY_STAINED_GLASS_PANE)
                .entities(List::of)
                .iconRenderer((v, entity) -> new ItemStack(Material.PLAYER_HEAD))
                .onSelect((p, entity) -> {})
                .extraButtons(List.of(
                        new EntityListSpec.ExtraButton(
                                FIRST_EXTRA_SLOT, named(Material.NAME_TAG), p -> clicks.add("first")),
                        new EntityListSpec.ExtraButton(
                                SECOND_EXTRA_SLOT, named(Material.BOOK), p -> clicks.add("second"))))
                .build();
    }

    private static ItemStack named(Material material) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(Component.text(material.name())));
        return item;
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
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
    }
}
