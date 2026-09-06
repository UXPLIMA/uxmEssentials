package com.uxplima.uxmessentials.vaults.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
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
import com.uxplima.uxmessentials.shared.menu.TileText;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultSelectorMenu;
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
 * MockBukkit coverage of the {@code /vault} selector rendered through the menu engine. A player owning vaults 1 and
 * 2 under an amount cap of 4 opens a picker with four cells: two owned (clickable chests) and two locked panes.
 * Clicking an owned cell opens that vault as a {@link StorageGui} through the same {@link OpenVault} path the
 * command drives; clicking a locked cell opens nothing and only sends the {@code VAULT_SELECTOR_LOCKED_CLICK}
 * nudge. A one-owned player still opens the menu without throwing, and the per-vault custom icon and name (escaped)
 * render onto their cells.
 *
 * <p>The slot source loads owned indices off-thread and the menu builds on the entity thread; both run inline
 * through a synchronous scheduler double. The engine's own {@link MenuListener} is installed against a mock plugin,
 * and a click is dispatched as a real {@link InventoryClickEvent} through the plugin manager, the production click
 * path.
 */
class VaultSelectorTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private RecordingSink sink;
    private FakeRepository repository;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        sink = new RecordingSink();
        repository = new FakeRepository();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void theSelectorOpensForAMultiVaultOwner() {
        repository.allocate(1);
        repository.allocate(2);
        openSelector(4);

        Inventory menu = player.getOpenInventory().getTopInventory();
        assertThat(menu.getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void clickingAnOwnedIconOpensThatVault() {
        repository.allocate(1);
        repository.allocate(2);
        openSelector(4);

        fireClick(1); // content slot 1 -> the second owned icon, vault 2

        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(StorageGui.class);
        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_OPENED);
    }

    @Test
    void clickingALockedIconOpensNothingAndNudges() {
        repository.allocate(1);
        repository.allocate(2);
        openSelector(4);

        fireClick(2); // content slot 2 -> the first locked index (vault 3)

        // Still on the selector, no storage vault opened, and only the locked nudge was sent.
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isNotInstanceOf(StorageGui.class);
        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_SELECTOR_LOCKED_CLICK);
        assertThat(sink.keys).doesNotContain(VaultsMessageKey.VAULT_OPENED);
    }

    @Test
    void aSingleOwnedPlayerStillOpensTheMenuWithoutThrowing() {
        repository.allocate(1);
        assertThatCode(() -> openSelector(4)).doesNotThrowAnyException();
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void anOwnedVaultRendersItsCustomIconWhenTheSummaryCarriesOne() {
        repository.allocate(1);
        repository.allocate(2);
        repository.setIcon(2, "DIAMOND");
        openSelector(4);

        Inventory menu = player.getOpenInventory().getTopInventory();
        // Content slots 0 and 1 hold the two owned vaults (vault 1, then vault 2 with the custom icon).
        assertThat(menu.getItem(0)).isNotNull();
        assertThat(menu.getItem(0).getType()).isEqualTo(org.bukkit.Material.CHEST);
        assertThat(menu.getItem(1)).isNotNull();
        assertThat(menu.getItem(1).getType()).isEqualTo(org.bukkit.Material.DIAMOND);
    }

    @Test
    void anUnknownCustomIconFallsBackToTheConfiguredOwnedIcon() {
        repository.allocate(1);
        repository.setIcon(1, "NOT_A_REAL_MATERIAL");
        openSelector(4);

        Inventory menu = player.getOpenInventory().getTopInventory();
        assertThat(menu.getItem(0)).isNotNull();
        assertThat(menu.getItem(0).getType()).isEqualTo(org.bukkit.Material.CHEST);
    }

    @Test
    void anOwnedVaultRendersItsCustomNameWhenTheSummaryCarriesOne() {
        repository.allocate(1);
        repository.setName(1, "Treasure");
        openSelector(4);

        Inventory menu = player.getOpenInventory().getTopInventory();
        String rendered = TileText.title(Objects.requireNonNull(menu.getItem(0), "vault tile"));
        assertThat(rendered).contains("Treasure");
    }

    @Test
    void aCustomNameWithMiniMessageTagsIsEscapedNotInterpreted() {
        repository.allocate(1);
        repository.setName(1, "<red>hax</red>");
        openSelector(4);

        Inventory menu = player.getOpenInventory().getTopInventory();
        // The literal tag text survives (escaped) rather than being parsed into a colour, so no injection.
        String rendered = TileText.title(Objects.requireNonNull(menu.getItem(0), "vault tile"));
        assertThat(rendered).contains("<red>").contains("hax");
    }

    /** Left-click the given content slot of the open menu through the installed listener. */
    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** Build the engine, register the vault bindings + spec, and open the picker whose amount cap is {@code cap}. */
    private void openSelector(int cap) {
        Messages messages = new KeyMessages();
        Scheduler scheduler = new SyncScheduler();
        GuiText guiText = new GuiText(messages);
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());

        Permissions permissions = new CapPermissions(cap);
        VaultAmountQuota amount = new VaultAmountQuota(permissions, cap);
        VaultSizeQuota size = new VaultSizeQuota(permissions, 6);
        VaultNotifier notifier = new VaultNotifier(messages, sink);
        VaultChargeSettings chargeSettings = VaultChargeSettings.allFree();
        VaultCharge charge = new VaultCharge(permissions, Optional.<VaultEconomy>empty(), chargeSettings);
        SaveVault saveVault = new SaveVault(repository, new NoEvents(), Clock.systemUTC());
        VaultView view =
                new VaultView(messages, sink, saveVault, scheduler, permissions, VaultItemPolicy.allowAll(), null);
        OpenVault openVault = new OpenVault(repository, amount, size, charge, Clock.systemUTC());
        ListVaults listVaults = new ListVaults(repository);
        VaultSelectorMenu menu = new VaultSelectorMenu(
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
                VaultViews.selectorSettings());
        menu.register(bindings, dataFolder, new NoopLogger());
        menu.open(ref());
    }

    private PlayerRef ref() {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    /** An in-memory vault store the test seeds with owned indices. */
    private final class FakeRepository implements VaultRepository {
        private final Map<Integer, Vault> vaults = new HashMap<>();

        void allocate(int index) {
            vaults.put(index, Vault.allocate(VaultId.of(ref(), index), VaultSize.ofClamped(6), Instant.now()));
        }

        void setName(int index, String name) {
            vaults.computeIfPresent(index, (idx, vault) -> vault.renamedTo(name));
        }

        void setIcon(int index, String materialName) {
            vaults.computeIfPresent(index, (idx, vault) -> vault.iconSet(materialName));
        }

        @Override
        public Optional<Vault> find(VaultId id) {
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

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            // The named-entry button label renders the stored name as the {name} value; surface that value so a
            // test can assert the custom name reaches the icon. Every other key returns its bare key as before.
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
