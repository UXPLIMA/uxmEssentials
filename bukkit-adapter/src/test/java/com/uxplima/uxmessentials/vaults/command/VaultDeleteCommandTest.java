package com.uxplima.uxmessentials.vaults.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
import com.uxplima.uxmessentials.vaults.application.SaveVault;
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
 * MockBukkit coverage of {@code /vault delete} through the real Brigadier {@code /vault} node, backed by the
 * real cached jOOQ {@code VaultRepository} over embedded SQLite. The own form deletes the caller's vault row
 * (freeing the quota slot) and refunds the configured amount when economy is on; the admin form
 * ({@code /vault delete <player> <n>}) is gated by {@code uxmessentials.vault.admin.delete}, deletes an offline
 * owner's vault and audits the override with no refund. The charge wiring is exercised on open: a new vault
 * costs the create fee when economy is enabled, is free when it is disabled, and the bypass node skips it.
 *
 * <p>The scheduler is synchronous so the off-tick delete runs inline; the audit and economy ports are
 * recording fakes so the override line and the debit/credit are asserted directly.
 */
class VaultDeleteCommandTest {

    private ServerMock server;
    private PlayerMock player;
    private Persistence persistence;
    private VaultRepository repository;
    private RecordingSink sink;
    private RecordingAudit audit;
    private RecordingEconomy economy;

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
        audit = new RecordingAudit();
        economy = new RecordingEconomy();
    }

    @AfterEach
    void tearDown() {
        persistence.close();
        MockBukkit.unmock();
    }

    @Test
    void deleteOwnRemovesTheRowAndNotifies() {
        VaultServices services = services(VaultEconomy.NONE, VaultChargeSettings.allFree(), new AllowAllPermissions());
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(services);
        services.openVault().open(ref(), 1); // allocate vault 1 so there is a row to delete
        assertThat(repository.find(VaultId.of(ref(), 1))).isPresent();

        execute(dispatcher, "vault delete 1");

        assertThat(repository.find(VaultId.of(ref(), 1))).isEmpty();
        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_DELETED);
    }

    @Test
    void deleteOwnRefundsWhenEconomyIsOn() {
        VaultChargeSettings settings = new VaultChargeSettings(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5"));
        VaultServices services = services(economy, settings, new AllowExceptFree());
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(services);
        services.openVault().open(ref(), 1);

        execute(dispatcher, "vault delete 1");

        assertThat(economy.deposited).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void deleteUnknownVaultNotifies() {
        VaultServices services = services(VaultEconomy.NONE, VaultChargeSettings.allFree(), new AllowAllPermissions());
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(services);

        execute(dispatcher, "vault delete 2");

        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_DELETE_UNKNOWN);
    }

    @Test
    void deleteOtherDeletesAndAuditsWithNoRefund() {
        VaultChargeSettings settings = new VaultChargeSettings(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5"));
        PlayerRef bob = new PlayerRef(UUID.randomUUID(), "Bob");
        VaultServices services = services(economy, settings, new AllowAllPermissions(), bob);
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(services);
        // Allocate Bob's vault 1 so the staff override has a row to delete.
        services.openVault().open(bob, 1);
        assertThat(repository.find(VaultId.of(bob, 1))).isPresent();

        execute(dispatcher, "vault delete Bob 1");

        assertThat(repository.find(VaultId.of(bob, 1))).isEmpty();
        assertThat(audit.deletedOwners).containsExactly(bob.uuid());
        assertThat(economy.deposited).isNull(); // the admin override pays no refund
    }

    @Test
    void deleteOtherIsHiddenWithoutTheAdminNode() {
        // requires(...) gates the admin branch; an unprivileged sender cannot parse /vault delete <player> <n>.
        player.setOp(false);
        VaultServices services = services(
                VaultEconomy.NONE, VaultChargeSettings.allFree(), new NodePermissions("uxmessentials.vault.use"));
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand(services);

        assertThatThrownBy(() -> dispatcher.execute("vault delete Bob 1", CommandSourceStackMock.from(player)))
                .isInstanceOf(CommandSyntaxException.class);
    }

    @Test
    void openChargesCreateFeeWhenEconomyEnabled() {
        VaultChargeSettings settings = new VaultChargeSettings(new BigDecimal("7"), BigDecimal.ZERO, BigDecimal.ZERO);
        VaultServices services = services(economy, settings, new AllowExceptFree());

        services.openVault().open(ref(), 1);

        assertThat(economy.withdrawn).isEqualByComparingTo(new BigDecimal("7"));
    }

    @Test
    void openIsFreeWhenEconomyDisabled() {
        VaultChargeSettings settings = new VaultChargeSettings(new BigDecimal("7"), BigDecimal.ZERO, BigDecimal.ZERO);
        // Economy NONE: the configured cost is recorded but never charged.
        VaultServices services = services(VaultEconomy.NONE, settings, new AllowAllPermissions());

        services.openVault().open(ref(), 1);

        assertThat(economy.withdrawn).isNull();
    }

    @Test
    void openIsFreeWhenBypassNodeHeld() {
        VaultChargeSettings settings = new VaultChargeSettings(new BigDecimal("7"), BigDecimal.ZERO, BigDecimal.ZERO);
        // Economy present, but the player holds uxmessentials.vault.free, so no fee is charged.
        VaultServices services = services(economy, settings, new NodePermissions("uxmessentials.vault.free"));

        services.openVault().open(ref(), 1);

        assertThat(economy.withdrawn).isNull();
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

    private VaultServices services(VaultEconomy vaultEconomy, VaultChargeSettings settings, Permissions perms) {
        return services(vaultEconomy, settings, perms, null);
    }

    private VaultServices services(
            VaultEconomy vaultEconomy,
            VaultChargeSettings settings,
            Permissions perms,
            @Nullable PlayerRef offlineOwner) {
        KernelPorts kernel = kernel(perms, offlineOwner);
        VaultAmountQuota amount = new VaultAmountQuota(kernel.permissions(), 5);
        VaultSizeQuota size = new VaultSizeQuota(kernel.permissions(), 6);
        VaultNotifier notifier = new VaultNotifier(kernel.messages(), kernel.messageSink());
        Optional<VaultEconomy> economyPort =
                vaultEconomy == VaultEconomy.NONE ? Optional.empty() : Optional.of(vaultEconomy);
        VaultCharge charge = new VaultCharge(kernel.permissions(), economyPort, settings);
        SaveVault saveVault = new SaveVault(repository, new NoEvents(), Clock.systemUTC());
        VaultView view = VaultViews.view(kernel, saveVault);
        OpenVault openVault = new OpenVault(repository, amount, size, charge, Clock.systemUTC());
        ListVaults listVaults = new ListVaults(repository);
        VaultSelectorMenu selector =
                VaultViews.selector(kernel, listVaults, amount, openVault, view, notifier, settings);
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
                settings,
                kernel);
    }

    private KernelPorts kernel(Permissions perms, @Nullable PlayerRef offlineOwner) {
        return new KernelPorts(
                new SyncScheduler(),
                perms,
                new NoCooldowns(),
                new NoWarmups(),
                new KeyMessages(),
                sink,
                new NamePlayerLookup(offlineOwner),
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
            // the key list (populated by KeyMessages) is what the tests assert on
        }
    }

    private final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            sink.keys.add(key);
            return key.key();
        }
    }

    private static final class RecordingAudit implements VaultAudit {
        private final List<UUID> deletedOwners = new ArrayList<>();

        @Override
        public void adminOpened(PlayerRef actor, PlayerRef owner, UUID ownerUuid, int index) {}

        @Override
        public void adminDeleted(PlayerRef actor, PlayerRef owner, UUID ownerUuid, int index) {
            deletedOwners.add(ownerUuid);
        }

        @Override
        public void purged(int count) {}
    }

    /** Records the most recent withdraw and deposit so the charge/refund wiring is asserted. */
    private static final class RecordingEconomy implements VaultEconomy {
        private @Nullable BigDecimal withdrawn;
        private @Nullable BigDecimal deposited;

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount) {
            return true;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount) {
            this.withdrawn = amount;
            return true;
        }

        @Override
        public boolean deposit(PlayerRef who, BigDecimal amount) {
            this.deposited = amount;
            return true;
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

    /** Grants every node except the cost-bypass {@code uxmessentials.vault.free}, so a fee actually fires. */
    private static final class AllowExceptFree implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return !"uxmessentials.vault.free".equals(node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    /** Grants exactly the given node and nothing else: for the bypass and unprivileged-branch tests. */
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

    /** Resolves a single offline owner by name (for the admin-delete path), online lookups empty. */
    private static final class NamePlayerLookup implements PlayerLookup {
        private final @Nullable PlayerRef owner;

        private NamePlayerLookup(@Nullable PlayerRef owner) {
            this.owner = owner;
        }

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByName(String name) {
            return owner != null && owner.name().equals(name) ? Optional.of(owner) : Optional.empty();
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
