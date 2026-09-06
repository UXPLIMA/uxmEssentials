package com.uxplima.uxmessentials.economy.adapter;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.adapter.inbound.command.EconomyCommands;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.BaltopMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.PayConfirmPanelMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.TransactionsHistoryMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.WalletPanelMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.listener.BanknoteListener;
import com.uxplima.uxmessentials.economy.adapter.inbound.listener.CommandCostListener;
import com.uxplima.uxmessentials.economy.adapter.outbound.BaltopSnapshots;
import com.uxplima.uxmessentials.economy.adapter.outbound.BukkitInventoryCalculations;
import com.uxplima.uxmessentials.economy.adapter.outbound.EconomyProviderRegistrar;
import com.uxplima.uxmessentials.economy.adapter.outbound.LoanRepaymentTask;
import com.uxplima.uxmessentials.economy.adapter.outbound.LoggingEconomyAudit;
import com.uxplima.uxmessentials.economy.adapter.outbound.PermissionBaltopExemption;
import com.uxplima.uxmessentials.economy.adapter.outbound.PermissionTaxExemption;
import com.uxplima.uxmessentials.economy.adapter.outbound.PhysicalWalletRepositoryDecorator;
import com.uxplima.uxmessentials.economy.adapter.outbound.ProviderKitEconomy;
import com.uxplima.uxmessentials.economy.adapter.outbound.ProviderNpcEconomy;
import com.uxplima.uxmessentials.economy.adapter.outbound.ProviderTaxSink;
import com.uxplima.uxmessentials.economy.adapter.outbound.ProviderWarpEconomy;
import com.uxplima.uxmessentials.economy.adapter.outbound.SalaryTask;
import com.uxplima.uxmessentials.economy.adapter.outbound.SchedulerPendingPayRegistry;
import com.uxplima.uxmessentials.economy.adapter.outbound.ShopWorthSource;
import com.uxplima.uxmessentials.economy.adapter.outbound.SnapshotBaltopProvider;
import com.uxplima.uxmessentials.economy.adapter.outbound.backend.CurrencyBackends;
import com.uxplima.uxmessentials.economy.application.AmountFormat;
import com.uxplima.uxmessentials.economy.application.BalTop;
import com.uxplima.uxmessentials.economy.application.Balance;
import com.uxplima.uxmessentials.economy.application.CombiningWorthSource;
import com.uxplima.uxmessentials.economy.application.EcoAdmin;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.ExchangeService;
import com.uxplima.uxmessentials.economy.application.LookupWorth;
import com.uxplima.uxmessentials.economy.application.NativeCurrencyBackend;
import com.uxplima.uxmessentials.economy.application.Pay;
import com.uxplima.uxmessentials.economy.application.PayAll;
import com.uxplima.uxmessentials.economy.application.PayTaxation;
import com.uxplima.uxmessentials.economy.application.PayToggle;
import com.uxplima.uxmessentials.economy.application.RoutingEconomyProvider;
import com.uxplima.uxmessentials.economy.application.SellAll;
import com.uxplima.uxmessentials.economy.application.SellItem;
import com.uxplima.uxmessentials.economy.application.SetWorth;
import com.uxplima.uxmessentials.economy.application.WorthSource;
import com.uxplima.uxmessentials.economy.application.WorthTable;
import com.uxplima.uxmessentials.economy.application.port.BaltopExemption;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.application.port.EconomyAudit;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.PayPreferences;
import com.uxplima.uxmessentials.economy.application.port.PendingPayRegistry;
import com.uxplima.uxmessentials.economy.application.port.PendingTransactionRepository;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.application.port.WorthOverrideStore;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.homes.adapter.outbound.ProviderHomeEconomy;
import com.uxplima.uxmessentials.homes.application.port.HomeEconomy;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.persistence.economy.CachedWalletRepository;
import com.uxplima.uxmessentials.persistence.economy.WalletLedger;
import com.uxplima.uxmessentials.persistence.economy.WalletRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.WalletSync;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.Hooks;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.ClickActionEconomy;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.vaults.adapter.outbound.ProviderVaultEconomy;
import com.uxplima.uxmessentials.vaults.application.port.VaultEconomy;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the economy context, the plugin's canonical worked DDD example, over the injected kernel ports,
 * the persistence DSL, and the Bukkit {@code ServicesManager}. This is the one place the economy context is
 * wired: it builds the native ledger (the cached jOOQ repository plus the debounced settle writer and the
 * batched transaction telemetry), wraps it in the native {@code EconomyProvider}, then runs register-or-defer
 *. Registering the native provider unless a foreign economy is already present, in which case it consumes the
 * incumbent (Treasury before Vault, {@code docs/11-economy-integration.md} §2, §4).
 *
 * <p>Every command reads the resolved provider through the per-currency baltop snapshot decorator, so the same
 * code serves the native ledger, a Treasury economy, or a legacy Vault economy without knowing which. The
 * {@code WarpEconomy} bridge produced here lets the warps context charge a per-warp cost through the resolved
 * provider. The {@link Wired} handle carries the lifecycle: {@code start} arms the settle/telemetry/baltop
 * loops and {@code stop} drains them and drops this plugin's registration.
 */
@NullMarked
public final class EconomyWiring {

    private EconomyWiring() {}

    /**
     * Build the economy context, threading the shared {@code textInput} seam into every menu that captures a typed
     * line. The bare-{@code /eco} admin GUI's Give / Take / Set / bulk amount entry and the bank, loan, and
     * exchange dashboards. A {@code null} {@code textInput} disables the admin GUI (bare {@code /eco} then falls
     * through to usage); the bank/loan/exchange views always receive the seam, and the raw subcommands are
     * unaffected either way.
     *
     * <p>The {@code menus} façade, the shared {@code menuBindings}, and the {@code dataFolder} carry the menu
     * engine into the economy context: the baltop leaderboard and the transaction-history list both render through
     * a registered engine spec rather than a bespoke view, so each registers its spec and bindings here once.
     */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            Bus bus,
            Hooks hooks,
            com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.@org.jspecify.annotations.Nullable TextInput
                    textInput,
            com.uxplima.uxmessentials.shared.adapter.inbound.gui.@org.jspecify.annotations.Nullable PlayerPickerView
                    picker,
            Menus menus,
            MenuBindings menuBindings,
            Path dataFolder) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(hooks, "hooks");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        KernelPorts kernel = ctx.kernel();
        EconomyConfig settings = new EconomyConfig(ctx.config());
        CurrencyRegistry currencies = settings.currencies();
        Clock clock = Clock.systemUTC();

        // The cached repository is the offline-read accelerator and the cache the bus invalidates on a remote
        // change; the broadcasting decorator wraps it so this backend's balance writes notify peers.
        CachedWalletRepository cached = WalletRepositories.cachedConcrete(persistence, currencies, clock);
        bus.registry().register(WalletSync.listener(cached));

        // Decorator chain: JooqWalletRepository -> CachedWalletRepository -> general-bus WalletSync broadcaster.
        // Cross-server invalidation rides the general bus's WalletSync frame (network.transport selects the
        // carrier). Money safety is the jOOQ guarded UPDATE alone, a single `UPDATE … WHERE amount >= ?` the
        // database serialises, where an over-draw updates zero rows; correct even across servers sharing one DB,
        // which a per-JVM lock could never be.
        WalletRepository repository = WalletSync.repository(cached, bus.publisher());

        WalletLedger ledger = WalletRepositories.ledgerOver(
                repository,
                persistence,
                kernel.scheduler(),
                kernel.log(),
                settings.writeDebounce(),
                settings.batchFlush());

        PendingTransactionRepository pendingRepo = WalletRepositories.pendingTransactionRepository(persistence);
        BukkitInventoryCalculations calculations = new BukkitInventoryCalculations();
        WalletRepository decoratedRepository = new PhysicalWalletRepositoryDecorator(
                ledger.repository(),
                pendingRepo,
                calculations,
                currencies,
                kernel.scheduler(),
                kernel.playerLookup(),
                kernel.log());

        ResolvedEconomy resolved =
                resolveProvider(plugin, ctx, kernel, hooks, settings, currencies, decoratedRepository, clock);
        return assemble(
                plugin,
                ctx,
                persistence,
                settings,
                currencies,
                ledger,
                resolved.provider(),
                resolved.backends(),
                decoratedRepository,
                pendingRepo,
                textInput,
                picker,
                menus,
                menuBindings,
                dataFolder);
    }

    /**
     * Build the closed backend set the currencies we own live on, register the routing provider over it, then
     * run register-or-defer. The routing provider is what the plugin registers with the {@code ServicesManager};
     * a Treasury or Vault economy already present still wins the whole economy, and the backends serve the
     * currencies we own. The registry is carried back out so the menu-side {@code Currencies} seam reads the same
     * backend set the provider routes through.
     */
    private static ResolvedEconomy resolveProvider(
            Plugin plugin,
            ModuleContext ctx,
            KernelPorts kernel,
            Hooks hooks,
            EconomyConfig settings,
            CurrencyRegistry currencies,
            WalletRepository repository,
            Clock clock) {
        CurrencyBackendRegistry backends = CurrencyBackends.discover(
                plugin.getServer(), hooks, kernel.log(), kernel.scheduler(), repository, ctx.config());
        EconomyProvider nativeProvider = new RoutingEconomyProvider(backends, currencies, clock, kernel.log());
        Optional<EconomyProvider> foreign = ForeignEconomyProviders.discover(plugin, currencies, kernel.log());
        if (foreign.isPresent()) {
            kernel.log().info("event=economy_provider_deferred (consuming foreign economy)");
            return new ResolvedEconomy(foreign.get(), backends);
        }
        logBackendsInUse(currencies, kernel.log());
        if (!settings.registerProvider()) {
            kernel.log().info("native economy provider registration disabled by config; using it locally");
            return new ResolvedEconomy(nativeProvider, backends);
        }
        EconomyProviderRegistrar registrar = new EconomyProviderRegistrar(
                plugin.getServer().getServicesManager(), plugin, kernel.log(), settings.registerPriority());
        EconomyProvider resolved = registrar.registerOrDefer(nativeProvider);
        if (resolved == nativeProvider) {
            // A third-party plugin asks Vault for an economy, not this plugin for its own port. Registering
            // only the port left every one of them finding nothing and paying nothing, however much this
            // ledger was holding.
            ForeignEconomyProviders.publishNative(
                    plugin, resolved, currencies, settings.registerPriority(), kernel.log());
        }
        return new ResolvedEconomy(resolved, backends);
    }

    /**
     * Say which backends the configured currencies actually live on, which is not what the available list says.
     * A server with an empty {@code backends} block still sees three backends discovered, and without this line
     * there is nothing in the log to tell an operator that every currency is on the plugin's own ledger. Only
     * reached when the routing provider is the one in play: a foreign economy bypasses these backends entirely
     * and says so on its own line.
     */
    private static void logBackendsInUse(CurrencyRegistry currencies, Logger log) {
        List<Currency> foreign = currencies.all().stream()
                .filter(currency -> !"native".equals(currency.backendId()))
                .sorted(Comparator.comparing(currency -> currency.id().value()))
                .toList();
        if (foreign.isEmpty()) {
            log.info(
                    "event=currency_backends_in_use backends=native currencies={}",
                    currencies.all().size());
            return;
        }
        foreign.forEach(currency -> log.info(
                "event=currency_backend_in_use currency={} backend={}",
                currency.id().value(),
                currency.backendId()));
    }

    /** The provider the plugin talks through and the backend set it routes over, resolved together at start. */
    private record ResolvedEconomy(EconomyProvider provider, CurrencyBackendRegistry backends) {}

    private static Wired assemble(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            EconomyConfig settings,
            CurrencyRegistry currencies,
            WalletLedger ledger,
            EconomyProvider resolved,
            CurrencyBackendRegistry backends,
            WalletRepository repository,
            PendingTransactionRepository pendingRepo,
            com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.@org.jspecify.annotations.Nullable TextInput
                    textInput,
            com.uxplima.uxmessentials.shared.adapter.inbound.gui.@org.jspecify.annotations.Nullable PlayerPickerView
                    picker,
            Menus menus,
            MenuBindings menuBindings,
            Path dataFolder) {
        KernelPorts kernel = ctx.kernel();
        BaltopExemption exemption = new PermissionBaltopExemption(kernel.permissions(), settings.baltopExemptNode());
        BaltopSnapshots snapshots = new BaltopSnapshots(
                resolved,
                exemption,
                kernel.scheduler(),
                settings.baltopCacheTtl(),
                settings.baltopCapacity(),
                settings.baltopExcludeBanned(),
                settings.baltopMinBalance(),
                new com.uxplima.uxmessentials.economy.adapter.outbound.BukkitBannedLookup());
        // One currency picker for the whole module: the loan request flow and both eco-admin screens open it, and
        // its spec and binding ids can only be claimed once, so it is built and registered here and handed to both.
        com.uxplima.uxmessentials.economy.adapter.inbound.gui.CurrencyPickerMenu currencyPicker =
                new com.uxplima.uxmessentials.economy.adapter.inbound.gui.CurrencyPickerMenu(
                        menus, kernel.messages(), kernel.scheduler());
        currencyPicker.register(menuBindings, dataFolder, kernel.log());
        EconomyServices services = useCases(
                plugin,
                persistence,
                kernel,
                settings,
                currencies,
                repository,
                resolved,
                snapshots,
                ledger.telemetry(),
                textInput,
                currencyPicker,
                menus,
                menuBindings,
                dataFolder);
        // The bare-/eco admin GUI (item 19): a hub over picker → per-player Give/Take/Set/Reset and a server-wide
        // bulk screen, reusing the shared input seam for amount entry. Built only when a seam is supplied (the
        // module is wired with GUIs available); GuiRootBinding then opens it on bare /eco when the catalog gui flag
        // is on, and the .requires(economy.admin) gate is carried by the rebind.
        com.uxplima.uxmessentials.economy.adapter.inbound.gui.EconomyAdminGuiViews adminGui = null;
        if (textInput != null && picker != null) {
            com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText guiText =
                    new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText(kernel.messages());
            adminGui = com.uxplima.uxmessentials.economy.adapter.inbound.gui.EconomyAdminGuiViews.create(
                    menus,
                    menuBindings,
                    dataFolder,
                    kernel.log(),
                    guiText,
                    kernel.messages(),
                    kernel.scheduler(),
                    plugin.getServer(),
                    picker,
                    currencyPicker,
                    textInput,
                    kernel.playerLookup(),
                    resolved,
                    services.ecoAdmin(),
                    currencies,
                    services.notifier(),
                    services.historyView());
        }
        List<CommandRegistration> commands =
                EconomyCommands.all(plugin, settings, services, kernel.messages(), adminGui);
        // Every seam below charges as a side effect of something else the player asked for, so each one reports
        // what it took: a silent debit is indistinguishable from money going missing.
        Optional<com.uxplima.uxmessentials.shared.adapter.outbound.ChargeReceipts> receipts =
                Optional.of(new com.uxplima.uxmessentials.shared.adapter.outbound.ChargeReceipts(
                        kernel.messages(), kernel.messageSink()));
        WarpEconomy warpEconomy = new ProviderWarpEconomy(resolved, currencies.defaultCurrency(), receipts);
        KitEconomy kitEconomy = new ProviderKitEconomy(resolved, currencies.defaultCurrency(), receipts);
        HomeEconomy homeEconomy = new ProviderHomeEconomy(resolved, currencies.defaultCurrency(), receipts);
        VaultEconomy vaultEconomy = new ProviderVaultEconomy(resolved, currencies.defaultCurrency(), receipts);
        ClickActionEconomy npcEconomy = new ProviderNpcEconomy(resolved, currencies.defaultCurrency(), receipts);

        java.util.List<org.bukkit.event.Listener> listenersList = new java.util.ArrayList<>();
        listenersList.add(new com.uxplima.uxmessentials.economy.adapter.inbound.listener.PendingTransactionListener(
                resolved, pendingRepo, services.notifier(), kernel.scheduler()));

        if (settings.commandCostsEnabled()) {
            listenersList.add(new CommandCostListener(
                    resolved, settings.commandCosts(), currencies.defaultCurrency(), services.notifier()));
        }
        if (settings.banknotesEnabled()) {
            listenersList.add(new BanknoteListener(services.banknoteRedeemer()));
        }
        if (settings.dailyRewardEnabled()) {
            listenersList.add(new com.uxplima.uxmessentials.economy.adapter.inbound.listener.DailyRewardListener(
                    plugin,
                    resolved,
                    services.notifier(),
                    kernel.scheduler(),
                    settings.dailyRewardCurrency(currencies),
                    true,
                    settings.dailyRewardAmount(),
                    settings.dailyRewardCooldown()));
        }
        List<org.bukkit.event.Listener> listeners = List.copyOf(listenersList);

        com.uxplima.uxmessentials.economy.adapter.outbound.SalaryTask salaryTask =
                new com.uxplima.uxmessentials.economy.adapter.outbound.SalaryTask(
                        plugin,
                        kernel.scheduler(),
                        resolved,
                        kernel.permissions(),
                        services.notifier(),
                        currencies.defaultCurrency(),
                        settings.salaryEnabled(),
                        settings.salaryInterval(),
                        settings.salaryDefaultAmount());

        com.uxplima.uxmessentials.economy.adapter.outbound.LoanRepaymentTask loanRepaymentTask =
                new com.uxplima.uxmessentials.economy.adapter.outbound.LoanRepaymentTask(
                        kernel.scheduler(),
                        services.loanService(),
                        kernel.log(),
                        settings.loansEnabled(),
                        settings.loanSweepInterval());

        com.uxplima.uxmessentials.economy.adapter.outbound.EconomyMaintenanceTask maintenanceTask =
                new com.uxplima.uxmessentials.economy.adapter.outbound.EconomyMaintenanceTask(
                        WalletRepositories.maintenance(persistence),
                        kernel.scheduler(),
                        kernel.log(),
                        Clock.systemUTC(),
                        new com.uxplima.uxmessentials.economy.adapter.outbound.BukkitPlayerLastSeen(),
                        settings.maintenanceEnabled(),
                        settings.maintenanceInterval(),
                        settings.maintenancePurgeInactiveDays(),
                        settings.maintenancePruneTransactionDays(),
                        settings.maintenanceDryRun());

        com.uxplima.uxmessentials.economy.adapter.outbound.BankInterestTask bankInterestTask =
                new com.uxplima.uxmessentials.economy.adapter.outbound.BankInterestTask(
                        com.uxplima.uxmessentials.persistence.economy.WalletRepositories.bankRepository(
                                persistence, currencies, Clock.systemUTC()),
                        settings.bankInterestPolicy(),
                        kernel.scheduler(),
                        kernel.log());

        return new Wired(
                commands,
                listeners,
                warpEconomy,
                kitEconomy,
                homeEconomy,
                vaultEconomy,
                npcEconomy,
                ledger,
                snapshots,
                resolved,
                backends,
                currencies,
                plugin,
                settings.registerProvider(),
                currencies.defaultCurrency(),
                settings.amountFormat(),
                salaryTask,
                loanRepaymentTask,
                maintenanceTask,
                bankInterestTask,
                services.walletView(),
                services.ecoAdmin());
    }

    /**
     * Whether this plugin's own wallet ledger is the authoritative store for every configured currency. Exchange,
     * banks and loans read and write {@code wallet_balances} directly instead of going through
     * {@link EconomyProvider}, so they are only correct while that holds. Two things break it: a foreign economy
     * plugin consuming us outright, or a single currency naming a foreign {@code backend}, its real balance then
     * lives in PlayerPoints or CoinsEngine, and a row in our table would invent money that does not exist.
     *
     * <p>One foreign currency therefore disables the three features for all currencies. A per-currency gate is the
     * better answer, but it means reworking {@code ExchangeService}, {@code BankService} and {@code LoanService},
     * and inventing money is the worse failure to risk in the meantime.
     */
    private static boolean walletLedgerIsAuthoritative(
            EconomyProvider resolved, CurrencyRegistry currencies, KernelPorts kernel) {
        if (!(resolved instanceof RoutingEconomyProvider)) {
            return false;
        }
        List<String> foreign = foreignBackedCurrencies(currencies);
        if (foreign.isEmpty()) {
            return true;
        }
        kernel.log()
                .warn(
                        "event=wallet_ledger_not_authoritative foreign_currencies={} disabled=exchange,bank,loan",
                        foreign);
        return false;
    }

    /**
     * The EconomyShopGUI-backed bottom layer of the worth chain, or empty when it should not be bound: the
     * operator turned the fallback off, or the plugin is simply not installed. Reading the shop catalogue is
     * deferred to the first {@code /worth} or {@code /sell}, so binding this costs nothing on enable.
     */
    private static Optional<WorthSource> shopWorthFallback(
            EconomyConfig settings, Server server, Logger log, CurrencyRegistry currencies) {
        if (!settings.shopWorthFallbackEnabled() || !ShopWorthSource.present(server)) {
            return Optional.empty();
        }
        log.info("event=worth_fallback_bound provider=economyshopgui");
        return Optional.of(new ShopWorthSource(
                server, log, currencies.defaultCurrency().id().value()));
    }

    /** The configured currencies whose balances live outside our ledger, as {@code <id>=<backend>} pairs. */
    static List<String> foreignBackedCurrencies(CurrencyRegistry currencies) {
        Objects.requireNonNull(currencies, "currencies");
        return currencies.all().stream()
                .filter(currency -> !NativeCurrencyBackend.ID.equals(currency.backendId()))
                .map(currency -> currency.id().value() + "=" + currency.backendId())
                .sorted()
                .toList();
    }

    private static EconomyServices useCases(
            Plugin plugin,
            Persistence persistence,
            KernelPorts kernel,
            EconomyConfig settings,
            CurrencyRegistry currencies,
            WalletRepository repository,
            EconomyProvider resolved,
            BaltopSnapshots snapshots,
            com.uxplima.uxmessentials.economy.application.port.TransactionHistory history,
            com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.@org.jspecify.annotations.Nullable TextInput
                    textInput,
            com.uxplima.uxmessentials.economy.adapter.inbound.gui.CurrencyPickerMenu currencyPicker,
            Menus menus,
            MenuBindings menuBindings,
            Path dataFolder) {
        // The bank, loan, and exchange dashboards all capture a typed amount through the shared input seam, so a
        // non-null TextInput is required to build them; bootstrap always supplies it.
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput input =
                Objects.requireNonNull(textInput, "textInput");
        EconomyNotifier notifier =
                new EconomyNotifier(kernel.messages(), kernel.messageSink(), settings.amountFormat());

        EconomyAudit baseAudit = new LoggingEconomyAudit(kernel.log());
        EconomyAudit audit = new com.uxplima.uxmessentials.economy.adapter.outbound.FraudDetector(
                baseAudit,
                kernel.log(),
                settings.fraudEnabled(),
                settings.fraudWebhookUrl(),
                settings.fraudSingleLimit(),
                settings.fraudVelocitySeconds(),
                settings.fraudVelocityLimit());
        audit = new com.uxplima.uxmessentials.economy.adapter.outbound.FileEconomyAudit(
                audit,
                kernel.scheduler(),
                kernel.log(),
                plugin.getDataFolder().toPath().resolve("economy").resolve("operations.log"),
                settings.operationLogEnabled());

        PayPreferences preferences = WalletRepositories.payPreferences(persistence, settings.payToggleDefault());
        PendingPayRegistry pending =
                new SchedulerPendingPayRegistry(kernel.scheduler(), kernel.log(), settings.confirmTimeout());
        Clock clock = Clock.systemUTC();
        EconomyProvider baltopProvider = new SnapshotBaltopProvider(resolved, snapshots);
        WorthTable configWorth = settings.worthTable(currencies, kernel.log());
        WorthOverrideStore worthOverrides = WalletRepositories.worthOverrideStore(persistence);
        // The bottom worth layer: EconomyShopGUI's own prices, so a shop that has already priced the server's
        // items answers for anything the operator has not priced themselves. Bound only when that plugin is
        // installed and the operator left the fallback on, so the usual server keeps the plain two-layer chain.
        WorthSource worth = shopWorthFallback(settings, plugin.getServer(), kernel.log(), currencies)
                .map(shop -> (WorthSource) new CombiningWorthSource(worthOverrides, configWorth, shop))
                .orElseGet(() -> new CombiningWorthSource(worthOverrides, configWorth));
        Currency defaultCurrency = currencies.defaultCurrency();
        PayTaxation taxation = new PayTaxation(
                settings.taxPolicy(),
                new ProviderTaxSink(resolved, settings.taxSinkAccount(), kernel.log()),
                new PermissionTaxExemption(kernel.permissions(), settings.taxBypassNode()));
        Pay pay = new Pay(resolved, preferences, pending, notifier, taxation, clock);

        boolean nativeLedger = walletLedgerIsAuthoritative(resolved, currencies, kernel);
        ExchangeService exchangeService = new ExchangeService(repository, settings.exchangeRegistry(), nativeLedger);

        // The read-only transaction-history list and the baltop leaderboard both render through the menu engine:
        // each registers its spec and bindings here once, then opens through the Menus façade. The history read
        // runs off the tick thread at the open site and hands the rows in as the menu subject; the baltop snapshot
        // is a lock-free Bukkit-free read the engine resolves off-thread directly.
        TransactionsHistoryMenu historyView =
                new TransactionsHistoryMenu(menus, history, kernel.scheduler(), settings.historyTimeZone());
        historyView.register(menuBindings, dataFolder, kernel.log());
        PayConfirmPanelMenu payConfirmationView = new PayConfirmPanelMenu(menus, pay, kernel.scheduler());
        payConfirmationView.register(menuBindings, dataFolder, kernel.log());
        BaltopMenu baltopGuiView = new BaltopMenu(menus, snapshots, notifier);
        baltopGuiView.register(menuBindings, dataFolder, kernel.log());
        WalletPanelMenu walletGuiView =
                new WalletPanelMenu(plugin, menus, resolved, kernel.scheduler(), notifier, historyView);
        walletGuiView.register(menuBindings, dataFolder, kernel.log());
        // The exchange dashboard is an engine-rendered panel now: it registers its spec and bindings here once, then
        // opens through the Menus façade. Its source/target icons open the shared currency picker to switch each side.
        com.uxplima.uxmessentials.economy.adapter.inbound.gui.EconomyExchangeMenu exchangeView =
                new com.uxplima.uxmessentials.economy.adapter.inbound.gui.EconomyExchangeMenu(
                        menus,
                        resolved,
                        exchangeService,
                        kernel.scheduler(),
                        notifier,
                        kernel.messages(),
                        input,
                        currencyPicker);
        exchangeView.register(menuBindings, dataFolder, kernel.log());

        com.uxplima.uxmessentials.economy.application.port.BanknoteStore banknoteStore =
                com.uxplima.uxmessentials.persistence.economy.WalletRepositories.banknoteStore(persistence);
        com.uxplima.uxmessentials.economy.adapter.inbound.listener.BanknoteRedeemer banknoteRedeemer =
                new com.uxplima.uxmessentials.economy.adapter.inbound.listener.BanknoteRedeemer(
                        plugin, resolved, notifier, banknoteStore, kernel.log());
        com.uxplima.uxmessentials.economy.application.port.BankRepository bankRepo =
                com.uxplima.uxmessentials.persistence.economy.WalletRepositories.bankRepository(
                        persistence, currencies, clock);
        com.uxplima.uxmessentials.economy.application.port.LoanRepository loanRepo =
                com.uxplima.uxmessentials.persistence.economy.WalletRepositories.loanRepository(
                        persistence, currencies, clock);
        com.uxplima.uxmessentials.persistence.economy.EconomyBackupManager backupManager =
                com.uxplima.uxmessentials.persistence.economy.WalletRepositories.backupManager(
                        persistence, repository, currencies, plugin.getDataFolder());

        // Loans and banks are native-ledger features like exchange (gated above): their atomic wallet legs run
        // through the wallet repository this context owns, so they are available only when the resolved provider
        // is the native ledger (a foreign economy holds balances we cannot move atomically here).
        com.uxplima.uxmessentials.economy.application.BankService bankService =
                new com.uxplima.uxmessentials.economy.application.BankService(
                        bankRepo, history, kernel.events(), clock, new java.security.SecureRandom(), nativeLedger);
        com.uxplima.uxmessentials.economy.application.LoanService loanService =
                new com.uxplima.uxmessentials.economy.application.LoanService(
                        loanRepo, settings.loanPolicy(), kernel.events(), clock, nativeLedger);

        // The bank menus open each other, so they cannot all be constructed before the navigation router that links
        // them. A holder supplies the router once it is built; each view holds the supplier as a final field and
        // dereferences it only on a click, keeping the cross-links constructor-injected and non-null (no
        // post-construction setters). The bank list and the members list are now engine-rendered menus that register
        // their spec and bindings here once and open through the Menus façade; the actions hub stays bespoke.
        java.util.concurrent.atomic.AtomicReference<
                        com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankNavigation>
                navigationHolder = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.function.Supplier<com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankNavigation> navigation =
                () -> Objects.requireNonNull(navigationHolder.get(), "bank navigation not yet wired");

        com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankListMenu bankListMenu =
                new com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankListMenu(
                        menus,
                        bankService,
                        currencies,
                        input,
                        currencyPicker,
                        kernel.scheduler(),
                        kernel.messages(),
                        navigation);
        bankListMenu.register(menuBindings, dataFolder, kernel.log());
        com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankMembersMenu bankMembersMenu =
                new com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankMembersMenu(
                        menus,
                        bankService,
                        input,
                        kernel.scheduler(),
                        kernel.playerLookup(),
                        kernel.messages(),
                        navigation);
        bankMembersMenu.register(menuBindings, dataFolder, kernel.log());
        com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankActionsMenu bankActionsView =
                new com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankActionsMenu(
                        menus, bankService, input, kernel.scheduler(), kernel.messages(), historyView, navigation);
        bankActionsView.register(menuBindings, dataFolder, kernel.log());
        navigationHolder.set(new com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankNavigation(
                bankListMenu, bankActionsView, bankMembersMenu));
        // The loan dashboard is an engine-rendered panel now: it registers its spec and bindings here once, then
        // opens through the Menus façade. Its active-loan strip is the economy:loan-list source; the request button
        // hands off to the shared currency picker and the input seam.
        com.uxplima.uxmessentials.economy.adapter.inbound.gui.LoanDashboardMenu loanGuiView =
                new com.uxplima.uxmessentials.economy.adapter.inbound.gui.LoanDashboardMenu(
                        menus,
                        loanService,
                        currencies,
                        input,
                        kernel.scheduler(),
                        kernel.messages(),
                        notifier,
                        currencyPicker);
        loanGuiView.register(menuBindings, dataFolder, kernel.log());

        return new EconomyServices(
                new Balance(resolved, notifier),
                pay,
                new PayAll(pay, notifier),
                new PayToggle(preferences, notifier),
                new BalTop(baltopProvider, notifier, settings.baltopPageSize()),
                new LookupWorth(worth, notifier, defaultCurrency, currencies.all()),
                new SellItem(resolved, worth, notifier, defaultCurrency, currencies.all()),
                new SellAll(resolved, worth, notifier, defaultCurrency, currencies.all()),
                new SetWorth(worthOverrides, notifier, audit, defaultCurrency, currencies.all()),
                new EcoAdmin(resolved, repository, audit, notifier, history, clock),
                exchangeService,
                currencies,
                snapshots,
                kernel.scheduler(),
                kernel.playerLookup(),
                notifier,
                history,
                historyView,
                payConfirmationView,
                baltopGuiView,
                walletGuiView,
                exchangeView,
                resolved,
                banknoteStore,
                banknoteRedeemer,
                bankService,
                loanService,
                backupManager,
                bankListMenu,
                loanGuiView);
    }

    /**
     * Everything the economy module contributes once wired: the Brigadier commands, the {@code WarpEconomy},
     * {@code KitEconomy}, {@code HomeEconomy}, and {@code VaultEconomy} bridges the warps, kits, homes, and
     * vaults contexts charge through, and the lifecycle for the settle/telemetry/baltop loops and the
     * {@code ServicesManager} registration.
     *
     * @param commands the Brigadier command registrations to publish
     * @param warpEconomy the bridge warps charges a per-warp cost through
     * @param kitEconomy the bridge kits charges a per-kit cost through
     * @param homeEconomy the bridge homes charges a per-action cost through
     * @param vaultEconomy the bridge vaults charges a per-action cost (and pays the delete refund) through
     * @param npcEconomy the bridge an NPC {@code COST} click action charges the clicking viewer through
     * @param ledger the native-ledger persistence handle whose loops are armed/drained
     * @param snapshots the per-currency baltop snapshots whose refresh loop is armed/stopped
     * @param provider the resolved provider this plugin uses (registered or deferred)
     * @param backends the closed backend set the routing provider resolves each currency against
     * @param currencies the closed currency set the menu-currency façade resolves menu specs against
     * @param plugin the owning plugin, for the registration drop on stop
     * @param registered whether this plugin registered the native provider (so stop only unregisters then)
     * @param defaultCurrency the default currency the {@code balance}/{@code baltop_position} placeholders read
     * @param amountFormat the operator-selected amount format the {@code balance_formatted} placeholder uses
     * @param walletView the wallet dashboard the {@code /wallet} command and the management hub both open
     * @param admin the eco-admin use case, which is also what a published API write runs
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<org.bukkit.event.Listener> listeners,
            WarpEconomy warpEconomy,
            KitEconomy kitEconomy,
            HomeEconomy homeEconomy,
            VaultEconomy vaultEconomy,
            ClickActionEconomy npcEconomy,
            WalletLedger ledger,
            BaltopSnapshots snapshots,
            EconomyProvider provider,
            CurrencyBackendRegistry backends,
            CurrencyRegistry currencies,
            Plugin plugin,
            boolean registered,
            Currency defaultCurrency,
            AmountFormat amountFormat,
            @org.jspecify.annotations.Nullable SalaryTask salaryTask,
            @org.jspecify.annotations.Nullable LoanRepaymentTask loanRepaymentTask,
            com.uxplima.uxmessentials.economy.adapter.outbound.EconomyMaintenanceTask maintenanceTask,
            com.uxplima.uxmessentials.economy.adapter.outbound.BankInterestTask bankInterestTask,
            WalletPanelMenu walletView,
            EcoAdmin admin) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(warpEconomy, "warpEconomy");
            Objects.requireNonNull(kitEconomy, "kitEconomy");
            Objects.requireNonNull(homeEconomy, "homeEconomy");
            Objects.requireNonNull(vaultEconomy, "vaultEconomy");
            Objects.requireNonNull(npcEconomy, "npcEconomy");
            Objects.requireNonNull(ledger, "ledger");
            Objects.requireNonNull(snapshots, "snapshots");
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(backends, "backends");
            Objects.requireNonNull(currencies, "currencies");
            Objects.requireNonNull(plugin, "plugin");
            Objects.requireNonNull(defaultCurrency, "defaultCurrency");
            Objects.requireNonNull(amountFormat, "amountFormat");
            Objects.requireNonNull(maintenanceTask, "maintenanceTask");
            Objects.requireNonNull(bankInterestTask, "bankInterestTask");
            Objects.requireNonNull(admin, "admin");
            Objects.requireNonNull(walletView, "walletView");
        }

        /** Arm the settle, telemetry, and baltop-refresh loops. Called once after the module starts. */
        public void start() {
            ledger.start();
            snapshots.start();
            if (salaryTask != null) {
                salaryTask.start();
            }
            if (loanRepaymentTask != null) {
                loanRepaymentTask.start();
            }
            maintenanceTask.start();
            bankInterestTask.start();
        }

        /** Drain the queues, stop the loops, and drop this plugin's provider registration. */
        public void stop() {
            bankInterestTask.stop();
            maintenanceTask.stop();
            if (loanRepaymentTask != null) {
                loanRepaymentTask.stop();
            }
            if (salaryTask != null) {
                salaryTask.stop();
            }
            snapshots.stop();
            ledger.stop();
            if (registered) {
                new EconomyProviderRegistrar(
                                plugin.getServer().getServicesManager(),
                                plugin,
                                new com.uxplima.uxmessentials.shared.adapter.outbound.log.Slf4jLogger(
                                        plugin.getSLF4JLogger()),
                                org.bukkit.plugin.ServicePriority.Normal)
                        .unregister(provider);
                ForeignEconomyProviders.withdrawNative(plugin);
            }
        }
    }
}
