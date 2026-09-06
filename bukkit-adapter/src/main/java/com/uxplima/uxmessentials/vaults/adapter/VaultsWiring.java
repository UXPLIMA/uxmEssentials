package com.uxplima.uxmessentials.vaults.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Sound;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.vaults.CachedVaultRepository;
import com.uxplima.uxmessentials.persistence.vaults.VaultRepositories;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRegistryKeys;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.VaultSync;
import com.uxplima.uxmessentials.shared.adapter.outbound.log.Slf4jLogger;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.vaults.adapter.inbound.command.VaultCommands;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultSelectorMenu;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultView;
import com.uxplima.uxmessentials.vaults.adapter.outbound.LoggingVaultAudit;
import com.uxplima.uxmessentials.vaults.adapter.outbound.VaultCleanupSweep;
import com.uxplima.uxmessentials.vaults.application.DeleteVault;
import com.uxplima.uxmessentials.vaults.application.ListVaults;
import com.uxplima.uxmessentials.vaults.application.OpenAdminVault;
import com.uxplima.uxmessentials.vaults.application.OpenVault;
import com.uxplima.uxmessentials.vaults.application.PurgeInactiveVaults;
import com.uxplima.uxmessentials.vaults.application.RenameVault;
import com.uxplima.uxmessentials.vaults.application.SaveVault;
import com.uxplima.uxmessentials.vaults.application.SetVaultIcon;
import com.uxplima.uxmessentials.vaults.application.VaultAmountQuota;
import com.uxplima.uxmessentials.vaults.application.VaultCharge;
import com.uxplima.uxmessentials.vaults.application.VaultChargeSettings;
import com.uxplima.uxmessentials.vaults.application.VaultNotifier;
import com.uxplima.uxmessentials.vaults.application.VaultSizeQuota;
import com.uxplima.uxmessentials.vaults.application.port.VaultAudit;
import com.uxplima.uxmessentials.vaults.application.port.VaultEconomy;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

/**
 * Constructs the vaults context's adapters and use cases over the injected kernel ports and the persistence
 * DSL, and produces everything the plugin must register: the Brigadier {@code /vault} command, with the vault
 * windows themselves handled by uxmLib's {@code StorageGui}. This is the one place the vaults context is wired
 *: nothing else news up its classes.
 *
 * <p>The repository is the cached jOOQ adapter over {@code persistence.dsl()} (write-through at the database,
 * invalidate in the Caffeine cache); the two numbered-quota families resolve through the shared
 * {@code Permissions} reducer with the {@code vaults.conf} defaults; the audit trail goes to the dedicated
 * {@code com.uxplima.uxmessentials.audit} channel (not the plugin log), so an operator routes it to a retained
 * file per docs/09-deployment. The GUI rides uxmLib's menu framework: the plugin bootstrap installs the one
 * shared menu listener and {@link VaultView} opens a {@code StorageGui} sized to the resolved quota that
 * writes the vault through on close.
 *
 * <p>Cross-server sync rides the {@link Bus} handle: the wiring registers a {@link VaultSync} listener that
 * invalidates exactly the {@code (owner, index)} a peer reports changed and wraps the cached repository so
 * every local vault save announces a {@code VaultChanged} to peers. With the bus disabled the publish is a
 * no-op and the listener is never invoked, so the single-server path is unchanged.
 */
@NullMarked
public final class VaultsWiring {

    private static final String AUDIT_CHANNEL = "com.uxplima.uxmessentials.audit";

    private VaultsWiring() {}

    /**
     * Build the vaults adapters and use cases from {@code ctx}, the {@code persistence} DSL, and the bus, with
     * no economy bridge: a configured vault cost is recorded but not charged.
     */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            Bus bus,
            Menus menus,
            MenuBindings menuBindings) {
        return wire(plugin, ctx, persistence, bus, Optional.empty(), menus, menuBindings);
    }

    /**
     * Build the vaults context, charging a configured per-action cost (and paying the delete refund) through
     * {@code vaultEconomy} when present. The economy context lands before vaults in the registry, so its
     * {@link VaultEconomy} bridge is captured during economy wiring and handed in here; when it is empty
     * (economy disabled, or {@code economy.enabled = false} in vaults config), a configured cost is recorded
     * but not charged and no refund is paid.
     *
     * <p>The {@code menus}/{@code menuBindings} pair is the data-driven menu engine: vaults registers the
     * selector's slot source, placeholders, and open action through the bindings and opens it through the façade.
     */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            Bus bus,
            Optional<VaultEconomy> vaultEconomy,
            Menus menus,
            MenuBindings menuBindings) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(vaultEconomy, "vaultEconomy");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        KernelPorts kernel = ctx.kernel();
        Clock clock = Clock.systemUTC();
        VaultSettings settings = new VaultSettings(ctx.config());
        // The cached repository is the read accelerator; the bus listener invalidates exactly the vault a peer
        // reports changed, and the broadcasting decorator announces this backend's own saves to peers.
        CachedVaultRepository cached = VaultRepositories.cachedConcrete(persistence);
        bus.registry().register(VaultSync.listener(cached));
        VaultRepository repository = VaultSync.repository(cached, bus.publisher());
        VaultAudit audit = new LoggingVaultAudit(auditLogger());
        // The two quota reducers are built once here so the placeholder seam reads the same resolved
        // vault.amount / vault.size the use cases enforce, rather than constructing a second pair.
        VaultAmountQuota amountQuota = new VaultAmountQuota(kernel.permissions(), settings.defaultAmount());
        VaultSizeQuota sizeQuota = new VaultSizeQuota(kernel.permissions(), settings.defaultSize());
        VaultServices services = assemble(
                plugin,
                kernel,
                settings,
                repository,
                clock,
                vaultEconomy,
                audit,
                amountQuota,
                sizeQuota,
                menus,
                menuBindings);
        // The cleanup sweep is opt-in: when cleanup.enabled is false it is never built, so a disabled module
        // arms zero background work. The running flag is flipped on stop so the self-rescheduling loop exits.
        AtomicBoolean running = new AtomicBoolean(true);
        Optional<VaultCleanupSweep> sweep = cleanupSweep(kernel, settings, repository, audit, running, clock);
        return new Wired(
                VaultCommands.all(services),
                List.of(),
                services.view(),
                services.selector(),
                repository,
                amountQuota,
                sizeQuota,
                services,
                sweep,
                running);
    }

    private static Optional<VaultCleanupSweep> cleanupSweep(
            KernelPorts kernel,
            VaultSettings settings,
            VaultRepository repository,
            VaultAudit audit,
            AtomicBoolean running,
            Clock clock) {
        if (!settings.cleanupEnabled()) {
            return Optional.empty();
        }
        return Optional.of(new VaultCleanupSweep(
                kernel.scheduler(),
                new PurgeInactiveVaults(repository),
                audit,
                settings.cleanupInterval(),
                settings.cleanupInactive(),
                running::get,
                kernel.log(),
                clock));
    }

    private static VaultServices assemble(
            Plugin plugin,
            KernelPorts kernel,
            VaultSettings settings,
            VaultRepository repository,
            Clock clock,
            Optional<VaultEconomy> vaultEconomy,
            VaultAudit audit,
            VaultAmountQuota amountQuota,
            VaultSizeQuota sizeQuota,
            Menus menus,
            MenuBindings menuBindings) {
        VaultNotifier notifier = new VaultNotifier(kernel.messages(), kernel.messageSink());
        VaultCharge charge = buildCharge(kernel, settings, vaultEconomy);
        SaveVault saveVault = new SaveVault(repository, kernel.events(), clock);
        @Nullable Sound openSound = BukkitRegistryKeys.resolveSound(settings.openSound());
        VaultView view = new VaultView(
                kernel.messages(),
                kernel.messageSink(),
                saveVault,
                kernel.scheduler(),
                kernel.permissions(),
                settings.itemPolicy(),
                openSound);
        OpenVault openVault = new OpenVault(repository, amountQuota, sizeQuota, charge, clock);
        ListVaults listVaults = new ListVaults(repository);
        VaultSelectorMenu selector = new VaultSelectorMenu(
                menus,
                kernel.messages(),
                kernel.messageSink(),
                kernel.scheduler(),
                listVaults,
                amountQuota,
                openVault,
                view,
                notifier,
                settings.chargeSettings(),
                settings.selectorSettings());
        selector.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        return new VaultServices(
                openVault,
                listVaults,
                new OpenAdminVault(repository, sizeQuota, audit, clock),
                new DeleteVault(repository, charge, audit, notifier),
                new RenameVault(repository, notifier),
                new SetVaultIcon(repository, notifier),
                saveVault,
                amountQuota,
                sizeQuota,
                notifier,
                view,
                selector,
                settings.selectorEnabled(),
                settings.maxNameLength(),
                settings.allowCustomIcon(),
                settings.chargeSettings(),
                kernel);
    }

    private static VaultCharge buildCharge(
            KernelPorts kernel, VaultSettings settings, Optional<VaultEconomy> vaultEconomy) {
        if (!settings.economyEnabled() || vaultEconomy.isEmpty()) {
            // Economy disabled in config or no provider wired: every vault action is free, no refund is paid.
            return new VaultCharge(kernel.permissions(), Optional.empty(), VaultChargeSettings.allFree());
        }
        return new VaultCharge(kernel.permissions(), vaultEconomy, settings.chargeSettings());
    }

    private static Logger auditLogger() {
        return new Slf4jLogger(LoggerFactory.getLogger(AUDIT_CHANNEL));
    }

    /**
     * Everything the vaults module contributes once wired: the Brigadier {@code /vault} command and the
     * {@link VaultView} held so {@code stop()} flushes every still-open vault before the pool closes (the
     * {@code open-guis=N} the doctor line reports), plus the optional self-rescheduling inactive-vault cleanup
     * sweep (present only when {@code cleanup.enabled}). The menu close events are routed by uxmLib's own menu
     * listener (installed once in the plugin bootstrap), so this module registers no inventory listener of its
     * own.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the Bukkit listeners to register (none here; the menu listener is uxmLib's)
     * @param view the GUI, held for the stop-time flush
     * @param selector the engine-rendered vault selector the {@code /vault} command and the management hub both open
     * @param repository the vault store the {@code vaults_count} placeholder reads
     * @param amountQuota the vault-count reducer the {@code vaults_max}/{@code vaults_left} placeholders read
     * @param sizeQuota the per-vault size reducer the {@code vaults_size} placeholder reads
     * @param services the assembled use cases, handed on so the published vault actions run the same ones
     * @param cleanupSweep the inactive-vault cleanup sweep, armed by {@link #startBackgroundWork} (empty when off)
     * @param running the flag flipped false on stop so the sweep's self-rescheduling loop exits
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            VaultView view,
            VaultSelectorMenu selector,
            VaultRepository repository,
            VaultAmountQuota amountQuota,
            VaultSizeQuota sizeQuota,
            VaultServices services,
            Optional<VaultCleanupSweep> cleanupSweep,
            AtomicBoolean running) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(view, "view");
            Objects.requireNonNull(selector, "selector");
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(amountQuota, "amountQuota");
            Objects.requireNonNull(sizeQuota, "sizeQuota");
            Objects.requireNonNull(services, "services");
            Objects.requireNonNull(cleanupSweep, "cleanupSweep");
            Objects.requireNonNull(running, "running");
        }

        /** Arm the inactive-vault cleanup sweep (a no-op when cleanup is disabled). Called on module enable. */
        public void startBackgroundWork() {
            cleanupSweep.ifPresent(VaultCleanupSweep::start);
        }

        /** Stop the cleanup sweep and save every still-open vault window before the pool closes. On module stop. */
        public void stop() {
            running.set(false);
            view.flushAll();
        }
    }
}
