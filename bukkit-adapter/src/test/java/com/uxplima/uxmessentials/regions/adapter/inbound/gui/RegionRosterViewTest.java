package com.uxplima.uxmessentials.regions.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
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
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
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
 * MockBukkit coverage of {@link RegionRosterView} over a real menu engine, a fake {@link RegionService} and a fake
 * {@link PlayerLookup} (WorldGuard is not on the test classpath. The editor is exercised through the ports): it draws
 * one icon per roster entry (owners first, then members, then groups), clicking a uuid-backed member removes it
 * through {@link RegionService#applyMemberChange} and the reopened panel reflects the shrunken roster, and clicking a
 * read-only group entry sends the "can't remove here" line and touches the port not at all.
 */
class RegionRosterViewTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final RegionRef REGION = new RegionRef(WORLD, "spawn");
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock staff;
    private PlayerRef staffRef;
    private FakeRegionService service;
    private RecordingSink sink;
    private RegionRosterView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        staff = server.addPlayer("Staff");
        staffRef = new PlayerRef(staff.getUniqueId(), staff.getName());
        service = new FakeRegionService();
        service.addOwner(OWNER.toString());
        service.addMember(MEMBER.toString());
        service.addMember("g:staff");
        sink = new RecordingSink();

        Scheduler scheduler = new SyncScheduler();
        Messages messages = keyEcho();
        GuiText guiText = new GuiText(messages);
        TestMenuEngine engine = TestMenuEngine.create(messages, scheduler);
        engine.installListener(plugin);
        Menus menus = engine.menus();
        PlayerLookup lookup = new FakePlayerLookup();
        view = new RegionRosterView(
                menus,
                guiText,
                scheduler,
                messages,
                sink,
                service,
                lookup,
                EntityListLayout.paginatedDefault(Material.PLAYER_HEAD));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void drawsAnIconPerRosterEntryOwnersFirst() {
        view.open(staffRef, REGION);

        Inventory inv = staff.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.GOLDEN_HELMET);
        assertThat(inv.getItem(1).getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(inv.getItem(2).getType()).isEqualTo(Material.OAK_SIGN);
    }

    @Test
    void clickingAMemberRemovesItThroughThePortAndTheReopenedPanelReflectsIt() {
        view.open(staffRef, REGION);

        // Slot 1 is the member (owner sits at 0); clicking it removes that member through the port.
        fireClick(1);
        assertThat(service.lastChange())
                .isEqualTo(new RegionMemberChange(
                        REGION, MEMBER, RegionMemberChange.Role.MEMBER, RegionMemberChange.Action.REMOVE));

        // The reopened panel now shows only the owner and the group; the group slides up to slot 1 and the vacated
        // slot 2 falls back to the glass filler: the member's head is gone.
        Inventory reopened = staff.getOpenInventory().getTopInventory();
        assertThat(reopened.getItem(0).getType()).isEqualTo(Material.GOLDEN_HELMET);
        assertThat(reopened.getItem(1).getType()).isEqualTo(Material.OAK_SIGN);
        assertThat(reopened.getItem(2).getType()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Test
    void clickingAReadOnlyGroupSendsTheNotRemovableLineAndTouchesNoPort() {
        view.open(staffRef, REGION);

        // Slot 2 is the g: group entry: it has no uuid to key a removal.
        fireClick(2);

        assertThat(sink.last()).isEqualTo("regions.members.not-removable");
        assertThat(service.lastChange()).isNull();
    }

    private void fireClick(int slot) {
        InventoryView view = staff.getOpenInventory();
        server.getPluginManager()
                .callEvent(new org.bukkit.event.inventory.InventoryClickEvent(
                        view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    private static Messages keyEcho() {
        return (viewer, key, placeholders) -> key.key();
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

    /** An in-memory {@link RegionService} serving a region's roster and applying a removal by uuid. */
    private static final class FakeRegionService implements RegionService {
        private final List<String> owners = new ArrayList<>();
        private final List<String> members = new ArrayList<>();
        private @Nullable RegionMemberChange lastChange;

        void addOwner(String identifier) {
            owners.add(identifier);
        }

        void addMember(String identifier) {
            members.add(identifier);
        }

        @Nullable RegionMemberChange lastChange() {
            return lastChange;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public List<RegionRef> regionsIn(WorldRef world) {
            return List.of(REGION);
        }

        @Override
        public Optional<RegionRef> region(WorldRef world, String id) {
            return Optional.of(REGION);
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
            return List.copyOf(members);
        }

        @Override
        public List<String> owners(RegionRef region) {
            return List.copyOf(owners);
        }

        @Override
        public int priority(RegionRef region) {
            return 0;
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
            this.lastChange = change;
            List<String> roster = change.role() == RegionMemberChange.Role.OWNER ? owners : members;
            if (change.action() == RegionMemberChange.Action.REMOVE) {
                roster.remove(change.player().toString());
            } else {
                roster.add(change.player().toString());
            }
        }

        @Override
        public void setPriority(RegionRef region, int priority) {
            throw new UnsupportedOperationException();
        }
    }

    /** A {@link PlayerLookup} that resolves the two roster uuids to stable names, else empty. */
    private static final class FakePlayerLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            if (uuid.equals(OWNER)) {
                return Optional.of(new PlayerRef(OWNER, "Owner"));
            }
            if (uuid.equals(MEMBER)) {
                return Optional.of(new PlayerRef(MEMBER, "Member"));
            }
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
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
