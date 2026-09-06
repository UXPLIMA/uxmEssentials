package com.uxplima.uxmessentials.presence.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link OnlinePlayerListView}: the read-only online-roster head grid. Given a
 * pre-snapshotted roster it draws one player head per entry into the paginated content slots through the engine
 * list runtime ({@link MenuHolder}); an empty roster opens the same engine list under its empty-state title, a
 * filler-and-nav panel, still a {@link MenuHolder}, never a bespoke uxmLib window. The scheduler is a synchronous
 * double so the entity-bound build runs inline, and the engine's menu listener is installed against a mock plugin.
 */
class OnlinePlayerListViewTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private OnlinePlayerListView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        engine.installListener(plugin);
        view = new OnlinePlayerListView(
                engine.menus(),
                new GuiText(new KeyMessages()),
                new SyncScheduler(),
                EntityListLayout.paginatedDefault(Material.PLAYER_HEAD));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rendersOneHeadPerRosterEntry() {
        List<OnlinePlayerListView.Entry> roster = List.of(
                new OnlinePlayerListView.Entry(UUID.randomUUID(), "Alice", "world", "Online"),
                new OnlinePlayerListView.Entry(UUID.randomUUID(), "Bob", "world", "Online"));

        view.open(player, viewer, roster);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(inv.getItem(1).getType()).isEqualTo(Material.PLAYER_HEAD);
        // The third content slot has no entry, so the filler shows through rather than a head.
        assertThat(inv.getItem(2).getType()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Test
    void anEmptyRosterOpensTheEngineListEmptyState() {
        view.open(player, viewer, List.of());

        Inventory inv = player.getOpenInventory().getTopInventory();
        // The empty roster opens the engine list (a MenuHolder), not a bespoke uxmLib window.
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        // No roster entry, so every content slot is filler: there is no player head anywhere.
        for (int slot = 0; slot < inv.getSize(); slot++) {
            assertThat(inv.getItem(slot).getType()).isNotEqualTo(Material.PLAYER_HEAD);
        }
    }

    /** Resolves a key to its own string so the view's rendering never needs a real catalog. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Runs every scheduler hop inline, as the production schedulers would after their marshal. */
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
