package com.uxplima.uxmessentials.vaults.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.vaults.VaultRepositories;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLocator;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.vaults.adapter.VaultServices;
import com.uxplima.uxmessentials.vaults.adapter.inbound.command.VaultCommand;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultSelectorMenu;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultView;
import com.uxplima.uxmessentials.vaults.application.DeleteVault;
import com.uxplima.uxmessentials.vaults.application.ListVaults;
import com.uxplima.uxmessentials.vaults.application.OpenAdminVault;
import com.uxplima.uxmessentials.vaults.application.OpenVault;
import com.uxplima.uxmessentials.vaults.application.SaveVault;
import com.uxplima.uxmessentials.vaults.application.VaultAmountQuota;
import com.uxplima.uxmessentials.vaults.application.VaultCharge;
import com.uxplima.uxmessentials.vaults.application.VaultChargeSettings;
import com.uxplima.uxmessentials.vaults.application.VaultNotifier;
import com.uxplima.uxmessentials.vaults.application.VaultSizeQuota;
import com.uxplima.uxmessentials.vaults.application.port.VaultAudit;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.StorageGui;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the vault GUI open → store → close → save round-trip through the real Brigadier
 * {@code /vault} node and uxmLib's {@code StorageGui}, backed by the real cached jOOQ {@code VaultRepository}
 * (from {@link VaultRepositories}) over an embedded SQLite database. {@code /vault} opens a {@code StorageGui}
 * sized to the resolved quota; an item placed in it and the window closed is serialized and written through to
 * the DB; re-opening the same vault re-reads the stored item. Proving vaults are DB-persisted and survive past
 * the live GUI, never PDC.
 *
 * <p>The scheduler is a synchronous double so the entity-bound open and the async save run inline. uxmLib's
 * menu listener is installed via {@link Guis#install} against a mock plugin, and the close is dispatched as a
 * real {@link InventoryCloseEvent} through the plugin manager, exactly the path a live close takes, so the
 * GUI's own close handler writes the vault through.
 */
class VaultGuiPathTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private Persistence persistence;
    private VaultRepository repository;
    private VaultServices services;
    private RecordingSink sink;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        player.setOp(true);
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = VaultRepositories.cached(persistence);
        sink = new RecordingSink();
        services = services();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall(); // reset the static install state so the next test re-installs the menu listener
        persistence.close();
        MockBukkit.unmock();
    }

    @Test
    void openStoreClosePersistsTheVaultAndReopenReadsItBack() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand();

        execute(dispatcher, "vault 1");
        Inventory vault = player.getOpenInventory().getTopInventory();
        assertThat(vault.getHolder()).isInstanceOf(StorageGui.class);
        assertThat(vault.getSize()).isEqualTo(54); // the default 6-row size quota

        // Store an item and close the window: the StorageGui's close handler serializes and writes it through.
        vault.setItem(0, new ItemStack(Material.DIAMOND, 12));
        server.getPluginManager().callEvent(new InventoryCloseEvent(player.getOpenInventory()));

        // The stored item is durable in the DB, not just in the live GUI.
        assertThat(repository.find(VaultId.of(ref(), 1))).isPresent();
        player.closeInventory();

        execute(dispatcher, "vault 1");
        Inventory reopened = player.getOpenInventory().getTopInventory();
        ItemStack restored = reopened.getItem(0);
        assertThat(restored).isNotNull();
        assertThat(restored.getType()).isEqualTo(Material.DIAMOND);
        assertThat(restored.getAmount()).isEqualTo(12);
    }

    @Test
    void openWithNoIndexOpensTheDefaultVault() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand();

        execute(dispatcher, "vault");

        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(StorageGui.class);
        assertThat(sink.keys).contains(com.uxplima.uxmessentials.vaults.application.VaultsMessageKey.VAULT_OPENED);
    }

    @Test
    void openWithNoIndexOpensTheSelectorWhenSeveralVaultsAreOwned() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand();
        // Allocate two vaults so the no-arg path crosses the >1 selector threshold.
        execute(dispatcher, "vault 1");
        player.closeInventory();
        execute(dispatcher, "vault 2");
        player.closeInventory();

        execute(dispatcher, "vault");

        assertThat(player.getOpenInventory().getTopInventory().getHolder())
                .isInstanceOf(com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder.class);
    }

    private CommandDispatcher<CommandSourceStack> registerCommand() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(new VaultCommand(services).build());
        return dispatcher;
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private PlayerRef ref() {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private VaultServices services() {
        KernelPorts kernel = kernel();
        VaultAmountQuota amount = new VaultAmountQuota(kernel.permissions(), 3);
        VaultSizeQuota size = new VaultSizeQuota(kernel.permissions(), 6);
        VaultNotifier notifier = new VaultNotifier(kernel.messages(), kernel.messageSink());
        VaultChargeSettings chargeSettings = VaultChargeSettings.allFree();
        VaultCharge charge = new VaultCharge(kernel.permissions(), Optional.empty(), chargeSettings);
        SaveVault saveVault = new SaveVault(repository, new NoEvents(), Clock.systemUTC());
        VaultView view = VaultViews.view(kernel, saveVault);
        VaultAudit audit = new NoAudit();
        OpenVault openVault = new OpenVault(repository, amount, size, charge, Clock.systemUTC());
        ListVaults listVaults = new ListVaults(repository);
        VaultSelectorMenu selector =
                VaultViews.selector(kernel, listVaults, amount, openVault, view, notifier, chargeSettings);
        return new VaultServices(
                openVault,
                listVaults,
                new OpenAdminVault(repository, size, audit, Clock.systemUTC()),
                new DeleteVault(repository, charge, audit, notifier),
                new com.uxplima.uxmessentials.vaults.application.RenameVault(repository, notifier),
                new com.uxplima.uxmessentials.vaults.application.SetVaultIcon(repository, notifier),
                saveVault,
                amount,
                size,
                notifier,
                view,
                selector,
                true,
                32,
                true,
                chargeSettings,
                kernel);
    }

    private KernelPorts kernel() {
        return new KernelPorts(
                new SyncScheduler(),
                new AllowAllPermissions(),
                new NoCooldowns(),
                new NoWarmups(),
                new KeyMessages(),
                sink,
                new NoPlayerLookup(),
                new NoWorldLookup(),
                new NoPlayerLocator(),
                new NoEvents(),
                new NoopLogger());
    }

    private record SqliteConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    /** Records each delivered key so a path's outcome is asserted by the message it produced. */
    private static final class RecordingSink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // renderedText is the key() string (see KeyMessages); the key list is what tests assert on
        }
    }

    private final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            sink.keys.add(key);
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

    private static final class AllowAllPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return true;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class NoCooldowns implements Cooldowns {
        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<com.uxplima.uxmessentials.shared.domain.Unit, Duration>
                check(PlayerRef who, CooldownKind kind) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<com.uxplima.uxmessentials.shared.domain.Unit, Duration>
                checkLabel(PlayerRef who, String label) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class NoWarmups implements Warmups {
        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            onComplete.run();
            return new Warmups.CompletedWarmup(who);
        }
    }

    private static final class NoPlayerLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    private static final class NoWorldLookup implements WorldLookup {
        @Override
        public Optional<WorldRef> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<WorldRef> findByUid(UUID uid) {
            return Optional.empty();
        }
    }

    private static final class NoPlayerLocator implements PlayerLocator {
        @Override
        public Optional<Position> locate(PlayerRef who) {
            return Optional.empty();
        }
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class NoAudit implements VaultAudit {
        @Override
        public void adminOpened(PlayerRef actor, PlayerRef owner, UUID ownerUuid, int index) {}

        @Override
        public void adminDeleted(PlayerRef actor, PlayerRef owner, UUID ownerUuid, int index) {}

        @Override
        public void purged(int count) {}
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
