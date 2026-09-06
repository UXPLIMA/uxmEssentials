package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultSelectorMenu;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultSelectorMenu.VaultSelectorSettings;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultView;
import com.uxplima.uxmessentials.vaults.application.ListVaults;
import com.uxplima.uxmessentials.vaults.application.OpenVault;
import com.uxplima.uxmessentials.vaults.application.SaveVault;
import com.uxplima.uxmessentials.vaults.application.VaultAmountQuota;
import com.uxplima.uxmessentials.vaults.application.VaultCharge;
import com.uxplima.uxmessentials.vaults.application.VaultChargeSettings;
import com.uxplima.uxmessentials.vaults.application.VaultNotifier;
import com.uxplima.uxmessentials.vaults.application.VaultSizeQuota;
import com.uxplima.uxmessentials.vaults.application.VaultSummary;
import com.uxplima.uxmessentials.vaults.application.VaultsMessageKey;
import com.uxplima.uxmessentials.vaults.application.port.VaultEconomy;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultItemPolicy;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.StorageGui;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The vaults golden test: the engine-rendered {@code /vault} selector must draw the exact grid the original
 * {@code VaultSelectorView} drew. A player owns vaults 1 and 3 under an amount cap of 5 with locked indices shown,
 * so the picker draws two owned chests (content slots 0 and 2), three locked panes (slots 1, 3, 4), and the two
 * nav arrows (slots 21 and 23). The engine's window is snapshotted as {@code (slot -> material, plain name)} and
 * asserted equal, slot for slot, to the baseline the old view produced. Captured once while both rendered the
 * same fixture, then frozen here as the contract so the old class could be deleted. Then a click on the owned
 * vault-1 icon through the engine's own {@link MenuListener} proves the migrated path opens that vault through the
 * same {@link OpenVault}/{@link VaultView} the command drives, and a click on a locked pane sends the nudge and
 * opens nothing, so the move is faithful in both appearance and behaviour.
 */
class VaultSelectorGoldenTest {

    private static final int CAP = 5;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private FakeRepository repository;
    private RecordingSink sink;
    private Messages messages;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        player = server.addPlayer("Owner");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        repository = new FakeRepository();
        repository.allocate(1);
        repository.allocate(3);
        sink = new RecordingSink();
        messages = new KeyMessages();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameOwnedLockedGridAndNavAsTheOldView() {
        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void clickingAnOwnedSlotThroughTheEngineOpensThatVault() {
        openEngine();
        // Content slot 0 is the first owned icon, vault 1; clicking it must open that vault as a storage window.
        fireClick(0);

        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(StorageGui.class);
        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_OPENED);
        assertThat(repository.opened).contains(1);
    }

    @Test
    void clickingALockedSlotThroughTheEngineNudgesAndOpensNothing() {
        openEngine();
        // Content slot 1 is the first locked index, vault 2; clicking it stays on the selector and only nudges.
        fireClick(1);

        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isNotInstanceOf(StorageGui.class);
        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_SELECTOR_LOCKED_CLICK);
        assertThat(sink.keys).doesNotContain(VaultsMessageKey.VAULT_OPENED);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code VaultSelectorView} produced for this fixture
     * (vaults 1 and 3 owned, cap 5, show-locked on), captured once while both paths rendered it identically and
     * frozen here as the contract: two owned CHEST cells (content slots 0 and 2), three locked grey panes (slots
     * 1, 3, 4), and the two nav ARROWs (slots 21 and 23). The plain names are the bare catalog keys because the
     * test's {@code KeyMessages} returns each key verbatim, so a real rendering difference (a wrong key, a wrong
     * material, a misplaced cell) still shows up as a mismatch.
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.CHEST, "vaults.selector.entry.name"));
        baseline.put(1, new Snapshot(Material.GRAY_STAINED_GLASS_PANE, "vaults.selector.locked.name"));
        baseline.put(2, new Snapshot(Material.CHEST, "vaults.selector.entry.name"));
        baseline.put(3, new Snapshot(Material.GRAY_STAINED_GLASS_PANE, "vaults.selector.locked.name"));
        baseline.put(4, new Snapshot(Material.GRAY_STAINED_GLASS_PANE, "vaults.selector.locked.name"));
        baseline.put(21, new Snapshot(Material.ARROW, "vaults.selector.prev"));
        baseline.put(23, new Snapshot(Material.ARROW, "vaults.selector.next"));
        return baseline;
    }

    /** Render the engine menu for the player and snapshot its populated slots. */
    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        Inventory inv = player.getOpenInventory().getTopInventory();
        return snapshot(inv);
    }

    /** Build the engine, register the vault bindings + spec, and open the selector for the player. */
    private void openEngine() {
        GuiText guiText = new GuiText(messages);
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());

        vaultMenu(menus, scheduler).register(bindings, dataFolder, new NoopLogger());
        menus.open(viewer, VaultSelectorMenu.SPEC_ID, null);
    }

    /** A {@link VaultSelectorMenu} wired off the same collaborators as the old view, over the engine façade. */
    private VaultSelectorMenu vaultMenu(Menus menus, Scheduler scheduler) {
        Permissions permissions = new CapPermissions(CAP);
        VaultAmountQuota amount = new VaultAmountQuota(permissions, CAP);
        VaultSizeQuota size = new VaultSizeQuota(permissions, 6);
        VaultNotifier notifier = new VaultNotifier(messages, sink);
        VaultChargeSettings chargeSettings = VaultChargeSettings.allFree();
        VaultCharge charge = new VaultCharge(permissions, Optional.<VaultEconomy>empty(), chargeSettings);
        SaveVault saveVault = new SaveVault(repository, new NoEvents(), Clock.systemUTC());
        VaultView view =
                new VaultView(messages, sink, saveVault, scheduler, permissions, VaultItemPolicy.allowAll(), null);
        OpenVault openVault = new OpenVault(repository, amount, size, charge, Clock.systemUTC());
        ListVaults listVaults = new ListVaults(repository);
        return new VaultSelectorMenu(
                menus,
                messages,
                sink,
                scheduler,
                listVaults,
                amount,
                openVault,
                view,
                notifier,
                chargeSettings,
                engineSettings());
    }

    /** Left-click the given content slot of the open menu through the production click path. */
    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The slot -> (material, plain name) map for every non-empty, non-filler slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item)));
        }
        return out;
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    /** The engine menu's selector settings: three-row picker, CHEST owned, grey-pane locked, locked shown. */
    private static VaultSelectorSettings engineSettings() {
        GuiLayout layout = new GuiLayout(3, Material.CHEST, Material.ARROW, 21, 23, List.of());
        return new VaultSelectorSettings(layout, Material.CHEST, Material.GRAY_STAINED_GLASS_PANE, true);
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    /** An in-memory vault store the test seeds with owned indices; records every index opened through it. */
    private final class FakeRepository implements VaultRepository {
        private final Map<Integer, Vault> vaults = new HashMap<>();
        private final List<Integer> opened = new ArrayList<>();

        void allocate(int index) {
            vaults.put(index, Vault.allocate(VaultId.of(viewer, index), VaultSize.ofClamped(6), Instant.now()));
        }

        @Override
        public Optional<Vault> find(VaultId id) {
            opened.add(id.index());
            return Optional.ofNullable(vaults.get(id.index()));
        }

        @Override
        public List<Integer> ownedIndices(PlayerRef owner) {
            List<Integer> indices = new ArrayList<>(vaults.keySet());
            indices.sort(Integer::compareTo);
            return indices;
        }

        @Override
        public List<VaultSummary> summaries(PlayerRef owner) {
            List<VaultSummary> out = new ArrayList<>();
            for (int index : ownedIndices(owner)) {
                Vault vault = vaults.get(index);
                out.add(new VaultSummary(index, vault.displayName(), vault.iconMaterial()));
            }
            return out;
        }

        @Override
        public int count(PlayerRef owner) {
            return vaults.size();
        }

        @Override
        public void save(Vault vault) {
            vaults.put(vault.id().index(), vault);
        }

        @Override
        public void delete(VaultId id) {
            vaults.remove(id.index());
        }

        @Override
        public int deleteUntouchedBefore(Instant cutoff) {
            int before = vaults.size();
            vaults.values().removeIf(vault -> vault.lastTouched().isBefore(cutoff));
            return before - vaults.size();
        }
    }

    /** Resolves a key to its bare key string, except the named-entry which surfaces the stored name value. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key == VaultsMessageKey.VAULT_SELECTOR_NAMED_ENTRY) {
                return placeholders.getOrDefault("name", "");
            }
            return key.key();
        }
    }

    private static final class RecordingSink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            for (VaultsMessageKey key : VaultsMessageKey.values()) {
                if (key.key().equals(renderedText)) {
                    keys.add(key);
                }
            }
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

    /** Resolves the amount quota to a fixed cap; grants no other node. */
    private static final class CapPermissions implements Permissions {
        private final int cap;

        private CapPermissions(int cap) {
            this.cap = cap;
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            if (family.equals(VaultAmountQuota.FAMILY)) {
                return QuotaResult.limited(cap);
            }
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class NoEvents
            implements com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher {
        @Override
        public void publish(com.uxplima.uxmessentials.shared.domain.DomainEvent event) {}
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
}
