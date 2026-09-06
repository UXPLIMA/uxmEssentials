package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
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
 * Proves a DB-backed list source is resolved off the viewer's region thread and then cached on the holder, so the
 * region thread never blocks on a query and a page flip never re-queries. A {@link PhaseScheduler} runs every hop
 * inline (so the test stays single-threaded) but tags which phase, the off-tick {@code async} or the entity
 * render, is live while a task runs; the list source records the phase it saw and counts its calls. Opening must
 * resolve the source exactly once during the async phase, and a real pagination click through the live
 * {@link MenuListener} must redraw the next page from the cache without touching the source again.
 */
class MenuAsyncListTest {

    private static final String HOCON = """
            rows = 1
            items {
              things {
                slots = ["0-2"],
                list { source = "t:list", template { material = STONE, name = "%v%" } }
              }
              next { slot = 8, type = NEXT, material = ARROW, name = "Next" }
            }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private Menus menus;
    private PhaseScheduler scheduler;
    private AtomicInteger sourceCalls;
    private AtomicReference<Phase> sourcePhase;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        scheduler = new PhaseScheduler();
        sourceCalls = new AtomicInteger();
        sourcePhase = new AtomicReference<>();

        GuiText guiText = new GuiText(new KeyMessages());
        PlaceholderRegistry placeholders = new PlaceholderRegistry();
        placeholders.register("v", ctx -> ctx.entry(String.class));
        ItemRenderer itemRenderer = new ItemRenderer(guiText, placeholders);
        ListSourceRegistry lists = new ListSourceRegistry();
        lists.register("t:list", ctx -> {
            sourceCalls.incrementAndGet();
            sourcePhase.set(scheduler.phase());
            return List.of("a", "b", "c", "d", "e", "f");
        });
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        MenuListener listener =
                new MenuListener(renderer, new ActionRegistry(), new ConditionRegistry(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        menus = new Menus(renderer, scheduler, lists);
        MenuSpec spec = new MenuSpecLoader().parse(HOCON);
        menus.registerSpec("test", spec);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void listSourceResolvesOffThreadOnce() {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), "test", null);

        assertThat(sourceCalls.get()).isEqualTo(1);
        assertThat(sourcePhase.get()).isEqualTo(Phase.ASYNC);
    }

    @Test
    void paginationReusesCachedListWithoutRequery() {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), "test", null);
        assertThat(sourceCalls.get()).isEqualTo(1);

        InventoryView view = player.getOpenInventory();
        InventoryClickEvent click = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 8, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);

        // The page flip redrew from the holder's cached list, so the source was not queried a second time.
        assertThat(sourceCalls.get()).isEqualTo(1);
    }

    private enum Phase {
        ASYNC,
        ENTITY
    }

    /**
     * A synchronous scheduler that runs every task inline but exposes which phase is currently live, so a list
     * source can record whether it ran during {@code async} (off-tick) or during an entity-thread render.
     */
    private static final class PhaseScheduler implements Scheduler {
        private Phase current = Phase.ENTITY;

        Phase phase() {
            return current;
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
            runIn(Phase.ENTITY, task);
        }

        @Override
        public void async(Runnable task) {
            runIn(Phase.ASYNC, task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            runIn(Phase.ASYNC, task);
        }

        private void runIn(Phase phase, Runnable task) {
            Phase previous = current;
            current = phase;
            try {
                task.run();
            } finally {
                current = previous;
            }
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
