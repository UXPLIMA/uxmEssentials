package com.uxplima.uxmessentials.regions.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.regions.domain.FlagDescriptor;
import com.uxplima.uxmessentials.regions.domain.FlagValue;
import com.uxplima.uxmessentials.regions.domain.RegionMemberChange;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link RegionListView} over a real menu engine and a fake {@link RegionService} (WorldGuard
 * is not on the test classpath. The view is exercised through the port): the {@code /regions} list renders one
 * paper icon per region priority-first, clicking one hands that region to the injected selection callback, and a
 * world with no regions sends the "no regions" line without opening a window.
 */
class RegionListViewTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock staff;
    private PlayerRef staffRef;
    private FakeRegionService service;
    private RecordingSink sink;
    private List<String> selected;
    private RegionListView listView;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        staff = server.addPlayer("Staff");
        staffRef = new PlayerRef(staff.getUniqueId(), staff.getName());
        service = new FakeRegionService(true);
        sink = new RecordingSink();
        selected = new ArrayList<>();

        Scheduler scheduler = new SyncScheduler();
        Messages messages = idAwareEcho();
        GuiText guiText = new GuiText(messages);
        TestMenuEngine engine = TestMenuEngine.create(messages, scheduler);
        engine.installListener(plugin);
        Menus menus = engine.menus();
        listView = new RegionListView(
                menus,
                guiText,
                scheduler,
                messages,
                sink,
                service,
                EntityListLayout.paginatedDefault(Material.PAPER),
                (Player clicker, RegionRef region) -> selected.add(region.id()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void listsRegionsPriorityFirstAndAClickHandsTheRegionToTheCallback() {
        // Added out of priority order; the list must show highest priority first (shop 10, spawn 5, wild 1).
        service.add(new RegionRef(WORLD, "spawn"), 5, 2);
        service.add(new RegionRef(WORLD, "shop"), 10, 0);
        service.add(new RegionRef(WORLD, "wild"), 1, 1);

        listView.open(staffRef, WORLD);

        Inventory inv = staff.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.PAPER);
        assertThat(inv.getItem(1).getType()).isEqualTo(Material.PAPER);
        assertThat(inv.getItem(2).getType()).isEqualTo(Material.PAPER);

        // Clicking a slot routes to onSelect for the region drawn there, which hands that region to the callback.
        fireClick(0);
        assertThat(selected).containsExactly("shop");
        fireClick(2);
        assertThat(selected).containsExactly("shop", "wild");
    }

    @Test
    void anEmptyWorldSendsTheNoRegionsLineAndOpensNoWindow() {
        listView.open(staffRef, WORLD);

        assertThat(sink.last()).isEqualTo("regions.no-regions");
        assertThat(menuHolderOpen(staff)).isFalse();
    }

    /** Whether {@code player} currently has an engine menu open: null-safe (no window means a null top inventory). */
    private static boolean menuHolderOpen(PlayerMock player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder;
    }

    private void fireClick(int slot) {
        InventoryView view = staff.getOpenInventory();
        server.getPluginManager()
                .callEvent(new org.bukkit.event.inventory.InventoryClickEvent(
                        view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    /** Renders the {@code {id}} placeholder verbatim (so an icon/notice carries the region id), else echoes the key. */
    private static Messages idAwareEcho() {
        return (viewer, key, placeholders) -> placeholders.containsKey("id") ? placeholders.get("id") : key.key();
    }

    /** Captures the last message delivered so a test can assert what the view sent. */
    private static final class RecordingSink implements MessageSink {
        private final AtomicReference<String> last = new AtomicReference<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            last.set(renderedText);
        }

        @Nullable String last() {
            return last.get();
        }
    }

    /** An in-memory {@link RegionService} for one world; reads answer, mutations are out of Phase 1 scope. */
    private static final class FakeRegionService implements RegionService {
        private final boolean available;
        private final List<RegionRef> regions = new ArrayList<>();
        private final Map<String, Integer> priorities = new java.util.HashMap<>();
        private final Map<String, Integer> memberCounts = new java.util.HashMap<>();

        FakeRegionService(boolean available) {
            this.available = available;
        }

        void add(RegionRef region, int priority, int memberCount) {
            regions.add(region);
            priorities.put(region.id(), priority);
            memberCounts.put(region.id(), memberCount);
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public List<RegionRef> regionsIn(WorldRef world) {
            return regions.stream().filter(r -> r.world().equals(world)).toList();
        }

        @Override
        public Optional<RegionRef> region(WorldRef world, String id) {
            return regions.stream()
                    .filter(r -> r.world().equals(world) && r.id().equals(id))
                    .findFirst();
        }

        @Override
        public List<RegionRef> regionsAt(Position position) {
            return List.of();
        }

        @Override
        public List<FlagValue> flags(RegionRef region) {
            return List.of();
        }

        @Override
        public List<FlagDescriptor> flagDescriptors(RegionRef region) {
            return List.of();
        }

        @Override
        public List<String> members(RegionRef region) {
            int count = memberCounts.getOrDefault(region.id(), 0);
            List<String> names = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                names.add("member" + i);
            }
            return names;
        }

        @Override
        public List<String> owners(RegionRef region) {
            return List.of();
        }

        @Override
        public int priority(RegionRef region) {
            return priorities.getOrDefault(region.id(), 0);
        }

        @Override
        public RegionRef create(WorldRef world, String id, Position min, Position max) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setFlag(RegionRef region, FlagValue flag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void applyMemberChange(RegionMemberChange change) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPriority(RegionRef region, int priority) {
            throw new UnsupportedOperationException();
        }
    }

    /** Runs every scheduler hop inline. */
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
