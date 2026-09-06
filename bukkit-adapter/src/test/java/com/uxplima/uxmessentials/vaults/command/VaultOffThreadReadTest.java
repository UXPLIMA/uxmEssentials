package com.uxplima.uxmessentials.vaults.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
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
import com.uxplima.uxmessentials.vaults.application.VaultSummary;
import com.uxplima.uxmessentials.vaults.application.port.VaultAudit;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Pins that {@code /vault} resolves the vault store <em>off</em> the command (tick/region) thread. The no-arg
 * {@code /vault} reads the owner's vault index to decide which window to open, and {@code /vault <n>} reads the
 * vault's contents; neither read may run on the thread Brigadier dispatched the command on. Both belong in a
 * {@link Scheduler#async} task whose continuation bridges the GUI/list/feedback back to the viewer's region
 * thread, the same shape {@code /home} uses.
 *
 * <p>The scheduler is a <em>deferring</em> double: {@code async} captures the task without running it, and
 * {@code onEntity} runs inline (the region bridge). So after dispatch the repository has seen zero reads
 * proving the lookup did not run on the command thread, and only once the captured task is drained does the
 * read happen.
 */
class VaultOffThreadReadTest {

    private ServerMock server;
    private PlayerMock player;
    private CountingRepository repository;
    private DeferringScheduler scheduler;
    private VaultServices services;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        player.setOp(true); // the /vault node gates on a permission; op satisfies it without a permission wiring
        repository = new CountingRepository();
        scheduler = new DeferringScheduler();
        services = services();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void noArgVaultReadsTheOwnedIndexOffTheCommandThread() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand();

        execute(dispatcher, "vault");

        // The command returned without scanning the database: the index read was handed to scheduler.async.
        assertThat(repository.reads).isZero();
        assertThat(scheduler.deferred).isNotEmpty();

        scheduler.drain();

        // Draining the captured task is what scanned the index and resolved the vault to open.
        assertThat(repository.reads).isPositive();
    }

    @Test
    void numberedVaultReadsContentsOffTheCommandThread() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommand();

        execute(dispatcher, "vault 2");

        assertThat(repository.reads).isZero();
        assertThat(scheduler.deferred).isNotEmpty();

        scheduler.drain();

        assertThat(repository.reads).isPositive();
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

    private VaultServices services() {
        KernelPorts kernel = kernel();
        VaultAmountQuota amount = new VaultAmountQuota(kernel.permissions(), 3);
        VaultSizeQuota size = new VaultSizeQuota(kernel.permissions(), 6);
        VaultNotifier notifier = new VaultNotifier(kernel.messages(), kernel.messageSink());
        VaultChargeSettings chargeSettings = VaultChargeSettings.allFree();
        VaultCharge charge = new VaultCharge(kernel.permissions(), Optional.empty(), chargeSettings);
        SaveVault saveVault = new SaveVault(repository, new NoEvents(), Clock.systemUTC());
        var view = VaultViews.view(kernel, saveVault);
        VaultAudit audit = new NoAudit();
        OpenVault openVault = new OpenVault(repository, amount, size, charge, Clock.systemUTC());
        ListVaults listVaults = new ListVaults(repository);
        var selector = VaultViews.selector(kernel, listVaults, amount, openVault, view, notifier, chargeSettings);
        return new VaultServices(
                openVault,
                listVaults,
                new OpenAdminVault(repository, size, audit, Clock.systemUTC()),
                new DeleteVault(repository, charge, audit, notifier),
                new RenameVault(repository, notifier),
                new SetVaultIcon(repository, notifier),
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
                scheduler,
                new AllowAllPermissions(),
                new NoCooldowns(),
                new NoWarmups(),
                new KeyMessages(),
                new RecordingSink(),
                new NoPlayerLookup(),
                new NoWorldLookup(),
                new NoPlayerLocator(),
                new NoEvents(),
                new NoopLogger());
    }

    /** Counts index/find reads and serves vaults from memory. */
    private static final class CountingRepository implements VaultRepository {
        private final TreeMap<Integer, Vault> byIndex = new TreeMap<>();
        private int reads;

        @Override
        public Optional<Vault> find(VaultId id) {
            reads++;
            return Optional.ofNullable(byIndex.get(id.index()));
        }

        @Override
        public List<Integer> ownedIndices(PlayerRef owner) {
            reads++;
            return List.copyOf(byIndex.keySet());
        }

        @Override
        public List<VaultSummary> summaries(PlayerRef owner) {
            reads++;
            List<VaultSummary> out = new ArrayList<>();
            for (Integer index : byIndex.keySet()) {
                out.add(new VaultSummary(index, null, null));
            }
            return out;
        }

        @Override
        public int count(PlayerRef owner) {
            reads++;
            return byIndex.size();
        }

        @Override
        public void save(Vault vault) {
            byIndex.put(vault.id().index(), vault);
        }

        @Override
        public void delete(VaultId id) {
            byIndex.remove(id.index());
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
