package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
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
 * MockBukkit coverage of {@link Menus#confirm}, the engine's two-button confirm window that replaces uxmLib's
 * {@code ConfirmMenu}. It opens as a {@link MenuHolder}-backed window the one {@link MenuListener} routes, so the
 * yes/no buttons run their supplied runnables exactly once and the window closes on the first click. A close
 * without a click runs neither handler, and the whole flow is leak-free: no refresh task survives the close.
 */
class MenuConfirmTest {

    private static final int YES_SLOT = 11;
    private static final int NO_SLOT = 15;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private Scheduler scheduler;
    private Menus menus;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler = new SyncScheduler();
        ItemRenderer itemRenderer = new ItemRenderer(
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText(new KeyMessages()),
                new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        menus = new Menus(renderer, scheduler, new ListSourceRegistry());
        MenuListener listener =
                new MenuListener(renderer, new ActionRegistry(), new ConditionRegistry(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void confirmOpensAMenuHolderWindowWithYesAndNoButtonsAtTheUxmLibSlots() {
        menus.confirm(viewer, Component.text("delete?"), () -> {}, () -> {});

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getItem(YES_SLOT).getType()).isEqualTo(Material.LIME_WOOL);
        assertThat(inv.getItem(NO_SLOT).getType()).isEqualTo(Material.RED_WOOL);
    }

    @Test
    void clickingYesRunsOnYesOnceNotOnNoAndClosesTheWindow() {
        AtomicInteger yes = new AtomicInteger();
        AtomicInteger no = new AtomicInteger();
        menus.confirm(viewer, Component.text("delete?"), yes::incrementAndGet, no::incrementAndGet);

        fireClick(YES_SLOT);

        assertThat(yes.get()).isEqualTo(1);
        assertThat(no.get()).isZero();
        assertWindowClosed();
    }

    @Test
    void clickingNoRunsOnNoOnceNotOnYesAndClosesTheWindow() {
        AtomicInteger yes = new AtomicInteger();
        AtomicInteger no = new AtomicInteger();
        menus.confirm(viewer, Component.text("delete?"), yes::incrementAndGet, no::incrementAndGet);

        fireClick(NO_SLOT);

        assertThat(no.get()).isEqualTo(1);
        assertThat(yes.get()).isZero();
        assertWindowClosed();
    }

    @Test
    void closingTheWindowWithoutAClickRunsNeitherHandler() {
        AtomicInteger yes = new AtomicInteger();
        AtomicInteger no = new AtomicInteger();
        menus.confirm(viewer, Component.text("delete?"), yes::incrementAndGet, no::incrementAndGet);

        player.closeInventory();

        assertThat(yes.get()).isZero();
        assertThat(no.get()).isZero();
    }

    @Test
    void aSecondClickAfterYesDoesNotReRunTheHandler() {
        AtomicInteger yes = new AtomicInteger();
        menus.confirm(viewer, Component.text("delete?"), yes::incrementAndGet, () -> {});

        fireClick(YES_SLOT);
        // The window closed on the first click; a stray second click on the same slot must not re-fire.
        fireClick(YES_SLOT);

        assertThat(yes.get()).isEqualTo(1);
    }

    @Test
    void openThenYesThenCloseLeavesNoLiveRefreshTaskAndDoubleCloseIsANoOp() {
        var recording = new RecordingScheduler();
        ItemRenderer itemRenderer = new ItemRenderer(
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText(new KeyMessages()),
                new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        Menus leakMenus = new Menus(renderer, recording, new ListSourceRegistry());
        MenuListener leakListener =
                new MenuListener(renderer, new ActionRegistry(), new ConditionRegistry(), recording, plugin);
        server.getPluginManager().registerEvents(leakListener, plugin);

        leakMenus.confirm(viewer, Component.text("delete?"), () -> {}, () -> {});
        fireClick(YES_SLOT);
        player.closeInventory();
        player.closeInventory(); // a double-close is a harmless no-op

        // A confirm window arms no refresh timer, so start and cancel both stay at zero: perfectly balanced.
        assertThat(recording.scheduled).isZero();
        assertThat(recording.cancelled).isZero();
    }

    private void assertWindowClosed() {
        assertThat(openMenuHolder()).isNull();
    }

    private void fireClick(int slot) {
        if (openMenuHolder() == null) {
            return;
        }
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The {@link MenuHolder} behind the player's open top inventory, or null when no engine menu is open. */
    private MenuHolder openMenuHolder() {
        InventoryView view = player.getOpenInventory();
        if (view == null) {
            return null;
        }
        Inventory top = view.getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder holder ? holder : null;
    }

    /** A no-op message source; the confirm window's title is a pre-resolved Component, so no key is read. */
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

    /** A scheduler that records every repeating-task start and cancel, for the leak-balance assertion. */
    private static final class RecordingScheduler implements Scheduler {
        int scheduled = 0;
        int cancelled = 0;

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            scheduled++;
            return () -> cancelled++;
        }

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
