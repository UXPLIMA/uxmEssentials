package com.uxplima.uxmessentials.vaults.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
import com.uxplima.uxmessentials.vaults.application.RenameVault;
import com.uxplima.uxmessentials.vaults.application.SaveVault;
import com.uxplima.uxmessentials.vaults.application.SetVaultIcon;
import com.uxplima.uxmessentials.vaults.application.VaultAmountQuota;
import com.uxplima.uxmessentials.vaults.application.VaultCharge;
import com.uxplima.uxmessentials.vaults.application.VaultChargeSettings;
import com.uxplima.uxmessentials.vaults.application.VaultNotifier;
import com.uxplima.uxmessentials.vaults.application.VaultSizeQuota;
import com.uxplima.uxmessentials.vaults.application.VaultsMessageKey;
import com.uxplima.uxmessentials.vaults.application.port.VaultAudit;
import com.uxplima.uxmessentials.vaults.application.port.VaultEconomy;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
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
 * MockBukkit coverage of {@code /vault rename} and {@code /vault icon} through the real Brigadier {@code /vault}
 * node, backed by the real cached jOOQ {@code VaultRepository} over embedded SQLite. Renaming sets or clears the
 * stored display name (refusing a name past the configured cap up front); the icon form sets the named material,
 * the held item when none is named, refuses an unknown material, and is gated off entirely when
 * {@code allow-custom-icon} is false. Both branches are permission-gated. The scheduler is synchronous so the
 * off-tick write runs inline and the persisted column can be asserted directly.
 */
class VaultRenameIconCommandTest {

    private ServerMock server;
    private PlayerMock player;
    private Persistence persistence;
    private VaultRepository repository;
    private RecordingSink sink;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        player.setOp(true);
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = VaultRepositories.cached(persistence);
        sink = new RecordingSink();
    }

    @AfterEach
    void tearDown() {
        persistence.close();
        MockBukkit.unmock();
    }

    @Test
    void renameSetsTheStoredDisplayName() {
        VaultServices services = services(32, true);
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(services);
        services.openVault().open(ref(), 1);

        execute(dispatcher, "vault rename 1 My Loot");

        assertThat(repository.find(VaultId.of(ref(), 1)).orElseThrow().displayName())
                .isEqualTo("My Loot");
        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_RENAMED);
    }

    @Test
    void renameWithoutANameClearsTheDisplayName() {
        VaultServices services = services(32, true);
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(services);
        services.openVault().open(ref(), 1);
        services.renameVault().rename(ref(), 1, "Old Name");

        execute(dispatcher, "vault rename 1");

        assertThat(repository.find(VaultId.of(ref(), 1)).orElseThrow().displayName())
                .isNull();
        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_NAME_CLEARED);
    }

    @Test
    void renameRefusesANameLongerThanTheConfiguredCap() {
        VaultServices services = services(4, true);
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(services);
        services.openVault().open(ref(), 1);

        execute(dispatcher, "vault rename 1 toolong");

        assertThat(repository.find(VaultId.of(ref(), 1)).orElseThrow().displayName())
                .isNull();
        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_NAME_TOO_LONG);
    }

    @Test
    void renameOfAnUnknownVaultNotifies() {
        VaultServices services = services(32, true);
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(services);

        execute(dispatcher, "vault rename 2 Whatever");

        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_RENAME_UNKNOWN);
    }

    @Test
    void renameIsHiddenWithoutTheRenameNode() {
        player.setOp(false);
        VaultServices services = services(32, true, new NodePermissions("uxmessentials.vault.use"));
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(services);

        assertThatThrownBy(() -> dispatcher.execute("vault rename 1 X", CommandSourceStackMock.from(player)))
                .isInstanceOf(CommandSyntaxException.class);
    }

    @Test
    void iconSetsTheNamedMaterial() {
        VaultServices services = services(32, true);
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(services);
        services.openVault().open(ref(), 1);

        execute(dispatcher, "vault icon 1 DIAMOND");

        assertThat(repository.find(VaultId.of(ref(), 1)).orElseThrow().iconMaterial())
                .isEqualTo("DIAMOND");
        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_ICON_SET);
    }

    @Test
    void iconWithoutAMaterialUsesTheHeldItem() {
        VaultServices services = services(32, true);
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(services);
        services.openVault().open(ref(), 1);
        player.getInventory().setItemInMainHand(new ItemStack(Material.EMERALD));

        execute(dispatcher, "vault icon 1");

        assertThat(repository.find(VaultId.of(ref(), 1)).orElseThrow().iconMaterial())
                .isEqualTo("EMERALD");
        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_ICON_SET);
    }

    @Test
    void iconWithoutAMaterialAndAnEmptyHandIsRefused() {
        VaultServices services = services(32, true);
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(services);
        services.openVault().open(ref(), 1);
        player.getInventory().setItemInMainHand(null);

        execute(dispatcher, "vault icon 1");

        assertThat(repository.find(VaultId.of(ref(), 1)).orElseThrow().iconMaterial())
                .isNull();
        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_ICON_NO_HELD_ITEM);
    }

    @Test
    void iconRefusesAnUnknownMaterial() {
        VaultServices services = services(32, true);
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(services);
        services.openVault().open(ref(), 1);

        execute(dispatcher, "vault icon 1 NOT_A_REAL_MATERIAL");

        assertThat(repository.find(VaultId.of(ref(), 1)).orElseThrow().iconMaterial())
                .isNull();
        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_UNKNOWN_MATERIAL);
    }

    @Test
    void iconIsRefusedWhenCustomIconsAreTurnedOff() {
        VaultServices services = services(32, false);
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(services);
        services.openVault().open(ref(), 1);

        execute(dispatcher, "vault icon 1 DIAMOND");

        assertThat(repository.find(VaultId.of(ref(), 1)).orElseThrow().iconMaterial())
                .isNull();
        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_ICON_NOT_ALLOWED);
    }

    @Test
    void iconIsHiddenWithoutTheIconNode() {
        player.setOp(false);
        VaultServices services = services(32, true, new NodePermissions("uxmessentials.vault.use"));
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(services);

        assertThatThrownBy(() -> dispatcher.execute("vault icon 1 DIAMOND", CommandSourceStackMock.from(player)))
                .isInstanceOf(CommandSyntaxException.class);
    }

    private CommandDispatcher<CommandSourceStack> registerCommand(VaultServices services) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(new VaultCommand(services).build());
        return dispatcher;
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private PlayerRef ref() {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private VaultServices services(int maxNameLength, boolean allowCustomIcon) {
        return services(maxNameLength, allowCustomIcon, new AllowAllPermissions());
    }

    private VaultServices services(int maxNameLength, boolean allowCustomIcon, Permissions perms) {
        KernelPorts kernel = kernel(perms);
        VaultAmountQuota amount = new VaultAmountQuota(kernel.permissions(), 5);
        VaultSizeQuota size = new VaultSizeQuota(kernel.permissions(), 6);
        VaultNotifier notifier = new VaultNotifier(kernel.messages(), kernel.messageSink());
        VaultChargeSettings settings = VaultChargeSettings.allFree();
        VaultCharge charge = new VaultCharge(kernel.permissions(), Optional.<VaultEconomy>empty(), settings);
        SaveVault saveVault = new SaveVault(repository, new NoEvents(), Clock.systemUTC());
        VaultView view = VaultViews.view(kernel, saveVault);
        OpenVault openVault = new OpenVault(repository, amount, size, charge, Clock.systemUTC());
        ListVaults listVaults = new ListVaults(repository);
        VaultSelectorMenu selector =
                VaultViews.selector(kernel, listVaults, amount, openVault, view, notifier, settings);
        return new VaultServices(
                openVault,
                listVaults,
                new OpenAdminVault(repository, size, new NoAudit(), Clock.systemUTC()),
                new DeleteVault(repository, charge, new NoAudit(), notifier),
                new RenameVault(repository, notifier),
                new SetVaultIcon(repository, notifier),
                saveVault,
                amount,
                size,
                notifier,
                view,
                selector,
                true,
                maxNameLength,
                allowCustomIcon,
                settings,
                kernel);
    }

    private KernelPorts kernel(Permissions perms) {
        return new KernelPorts(
                new SyncScheduler(),
                perms,
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

    // ----- fakes mirroring the vaults command-test harness (the real port shapes) -----

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

    private final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            sink.keys.add(key);
            return key.key();
        }
    }

    private static final class RecordingSink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // the key list (populated by KeyMessages) is what the tests assert on
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

    /** Grants exactly one node: used to prove the rename/icon branches are hidden without their node. */
    private static final class NodePermissions implements Permissions {
        private final String granted;

        private NodePermissions(String granted) {
            this.granted = granted;
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return granted.equals(node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
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

    private static final class NoAudit implements VaultAudit {
        @Override
        public void adminOpened(PlayerRef actor, PlayerRef owner, java.util.UUID ownerUuid, int index) {}

        @Override
        public void adminDeleted(PlayerRef actor, PlayerRef owner, java.util.UUID ownerUuid, int index) {}

        @Override
        public void purged(int count) {}
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
        public Optional<PlayerRef> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(java.util.UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(java.util.UUID uuid) {
            return false;
        }
    }

    private static final class NoWorldLookup implements WorldLookup {
        @Override
        public Optional<WorldRef> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<WorldRef> findByUid(java.util.UUID uid) {
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
