package com.uxplima.uxmessentials.vaults.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
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
 * Pins that clicking an owned cell in the engine-rendered {@code /vault} selector resolves the vault's contents
 * <em>off</em> the click (region) thread. The engine dispatches the click handler on the viewer's entity thread;
 * the vault-contents read it triggers must not run there. It belongs in a {@link Scheduler#async} task whose
 * continuation bridges the window open back to the viewer's region thread, the same shape {@code /vault <n>} uses.
 *
 * <p>The scheduler is a <em>deferring</em> double: {@code async} captures the task without running it, while
 * {@code onEntity} runs inline (the region bridge). The menu is opened first with the captured slot-source load
 * drained (so the picker is on screen), the read counter is then reset, and a click is fired. After the click the
 * repository has seen zero further reads (proving the contents read did not run on the click thread) and only
 * once the captured task is drained does the read happen and the vault window open.
 */
class VaultSelectorOffThreadReadTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private RecordingSink sink;
    private CountingRepository repository;
    private DeferringScheduler scheduler;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        sink = new RecordingSink();
        repository = new CountingRepository();
        scheduler = new DeferringScheduler();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void clickingAnOwnedIconReadsTheContentsOffTheClickThread() {
        repository.allocate(1);
        repository.allocate(2);
        openSelector(4);
        scheduler.drain(); // the deferred slot-source load builds and opens the picker on the entity bridge
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);

        repository.reads = 0; // ignore the index/summary reads that built the menu; measure only the click

        fireClick(1); // content slot 1 -> the second owned icon, vault 2

        // The click handler returned without reading the vault's contents: that read was handed to async.
        assertThat(repository.reads).isZero();
        assertThat(scheduler.deferred).isNotEmpty();
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);

        scheduler.drain();

        // Draining the captured task is what read the contents and opened the vault window on the bridge-back.
        assertThat(repository.reads).isPositive();
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(StorageGui.class);
    }

    /** Left-click the given content slot of the open menu through the installed listener. */
    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** Build the engine over the deferring scheduler, register the vault bindings + spec, and open the picker. */
    private void openSelector(int cap) {
        Messages messages = new KeyMessages();
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

    /** Counts find/index reads and serves vaults from memory. */
    private final class CountingRepository implements VaultRepository {
        private final Map<Integer, Vault> vaults = new HashMap<>();
        private int reads;

        void allocate(int index) {
            vaults.put(index, Vault.allocate(VaultId.of(ref(), index), VaultSize.ofClamped(6), Instant.now()));
        }

        @Override
        public Optional<Vault> find(VaultId id) {
            reads++;
            return Optional.ofNullable(vaults.get(id.index()));
        }

        @Override
        public List<Integer> ownedIndices(PlayerRef owner) {
            reads++;
            List<Integer> indices = new ArrayList<>(vaults.keySet());
            indices.sort(Integer::compareTo);
            return indices;
        }

        @Override
        public List<VaultSummary> summaries(PlayerRef owner) {
            reads++;
            List<VaultSummary> out = new ArrayList<>();
            for (int index : ownedIndices(owner)) {
                Vault vault = vaults.get(index);
                out.add(new VaultSummary(index, vault.displayName(), vault.iconMaterial()));
            }
            return out;
        }

        @Override
        public int count(PlayerRef owner) {
            reads++;
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
            return 0;
        }
    }

    /** Captures async tasks without running them; runs the region bridge inline. */
    private static final class DeferringScheduler implements Scheduler {
        private final List<Runnable> deferred = new ArrayList<>();

        void drain() {
            List<Runnable> snapshot = List.copyOf(deferred);
            deferred.clear();
            snapshot.forEach(Runnable::run);
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
            deferred.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            deferred.add(task);
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class RecordingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
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
