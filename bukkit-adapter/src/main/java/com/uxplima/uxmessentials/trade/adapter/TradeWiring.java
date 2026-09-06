package com.uxplima.uxmessentials.trade.adapter;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.trade.TradeRepositories;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.adapter.outbound.log.Slf4jLogger;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.trade.adapter.inbound.command.TradeCommand;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.CrossServerTradeView;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.CrossTradeWindow;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeExperiencePrompt;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeMoneyPrompt;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeSessions;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeView;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeWindow;
import com.uxplima.uxmessentials.trade.adapter.inbound.listener.TradeReconcileListener;
import com.uxplima.uxmessentials.trade.adapter.outbound.BukkitTradeExperience;
import com.uxplima.uxmessentials.trade.adapter.outbound.BukkitTradeItemDelivery;
import com.uxplima.uxmessentials.trade.adapter.outbound.BusTradeBus;
import com.uxplima.uxmessentials.trade.adapter.outbound.LoggingTradeAudit;
import com.uxplima.uxmessentials.trade.application.CrossServerTrade;
import com.uxplima.uxmessentials.trade.application.TradeConfig;
import com.uxplima.uxmessentials.trade.application.TradeCooldown;
import com.uxplima.uxmessentials.trade.application.TradeMessageKey;
import com.uxplima.uxmessentials.trade.application.TradeRequests;
import com.uxplima.uxmessentials.trade.application.TradeSettlement;
import com.uxplima.uxmessentials.trade.application.TradeSignal;
import com.uxplima.uxmessentials.trade.application.port.TradeAudit;
import com.uxplima.uxmessentials.trade.application.port.TradeEconomy;
import com.uxplima.uxmessentials.trade.application.port.TradeEscrowStore;
import com.uxplima.uxmessentials.trade.application.port.TradeExperience;
import com.uxplima.uxmessentials.trade.application.port.TradeItemDelivery;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

/**
 * Constructs the trade context's adapters over the injected kernel ports. The same-server trade window is the in-memory
 * {@link TradeSessions} registry, the {@link TradeView} that opens and drives each participant's inventory view over a
 * shared session, and the {@link TradeListener} that routes the window's click/drag/close/quit events. Phase 3 adds the
 * money row: the {@link TextInput}-backed amount prompt behind the {@link TradeMoneyPrompt} seam and the
 * {@link TradeSettlement} that moves staked money all-or-nothing through the {@link TradeEconomy} port at commit, the
 * port is present only when economy is wired, so without it a trade moves items only. Phase 4 adds the {@code /trade}
 * request/accept flow. The in-memory {@link TradeRequests} book and the per-player {@link TradeCooldown}, both clock
 * driven: plus the config-gated completed-trade audit through the {@link TradeAudit} port. A same-server trade holds
 * its session purely in memory, so there is still no repository or migration; the cross-server bus escrow lands in the
 * final phase behind this same {@link Wired} seam.
 */
@NullMarked
public final class TradeWiring {

    /** The dedicated audit channel a completed trade's line is written to: the same channel the other contexts use. */
    private static final String AUDIT_CHANNEL = "com.uxplima.uxmessentials.audit";

    private TradeWiring() {}

    /**
     * Build the trade adapters from {@code ctx}, ready to register. The {@code economy} seam is present only when the
     * economy provider is wired; when absent the money row is hidden and a trade moves items only.
     */
    public static Wired wire(
            ModuleContext ctx,
            TextInput textInput,
            @Nullable TradeEconomy economy,
            @Nullable Persistence persistence,
            Bus bus,
            Menus menus,
            MenuBindings menuBindings,
            Path dataFolder) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        TradeConfig config = TradeConfig.from(ctx.config());
        KernelPorts kernel = ctx.kernel();
        TradeSessions sessions = new TradeSessions();
        // Experience is a native server resource, so its seam is always wired and the settlement is always present; a
        // trade can move experience even on an install with no economy provider. Money legs only exist when the money
        // button is shown (economy wired), so the items-only stand-in economy is never charged when money is off.
        TradeExperience experience = new BukkitTradeExperience(Bukkit.getServer(), kernel.scheduler(), kernel.log());
        boolean moneyEnabled = economy != null;
        TradeSettlement settlement = new TradeSettlement(economy != null ? economy : itemsOnlyEconomy(), experience);
        TradeMoneyPrompt moneyPrompt = (player, viewer, currencyId, onSubmit, onCancel) -> textInput.prompt(
                player,
                viewer,
                InputRequest.of("trade.money", TradeMessageKey.TRADE_MONEY_PROMPT, Map.of("currency", currencyId)),
                onSubmit,
                onCancel);
        TradeExperiencePrompt experiencePrompt = (player, viewer, onSubmit, onCancel) -> textInput.prompt(
                player,
                viewer,
                InputRequest.of("trade.experience", TradeMessageKey.TRADE_EXPERIENCE_PROMPT),
                onSubmit,
                onCancel);
        TradeAudit audit = new LoggingTradeAudit(new Slf4jLogger(LoggerFactory.getLogger(AUDIT_CHANNEL)));
        // The window's chrome comes from modules/trade/gui/trade.conf; the currencies it may cycle through are the
        // module's allowed list, and an empty list is what turns the money button off on an install with no economy.
        TradeWindow window = new TradeWindow(
                kernel.messages(),
                menus,
                moneyEnabled ? config.currenciesAllowed() : List.of(),
                dataFolder,
                kernel.log());
        TradeView view = new TradeView(
                kernel.messages(),
                kernel.messageSink(),
                kernel.scheduler(),
                config,
                sessions,
                window,
                moneyPrompt,
                experiencePrompt,
                settlement,
                experience,
                audit,
                kernel.events());
        view.register(menuBindings);
        // The request/accept flow: a per-request expiry book and a per-player cooldown, both driven by the same clock,
        // gate /trade and open the window through the view once the target accepts. The clock is UTC so a request's
        // lifetime is independent of the host's zone (mirrors the moderation context).
        Clock clock = Clock.systemUTC();
        TradeRequests requests = new TradeRequests(clock, Duration.ofSeconds(config.requestExpirySeconds()));
        TradeCooldown cooldown = new TradeCooldown(clock, Duration.ofSeconds(config.cooldownSeconds()));
        // Cross-server escrow lands only when the module opts in (cross-server=true) and the network DB is present; a
        // same-server-only install wires none of it and /trade to a non-local name is refused.
        List<Listener> listeners = new java.util.ArrayList<>();
        listeners.add(view.newListener());
        @Nullable CrossServerTradeView crossView = null;
        if (config.crossServer() && persistence != null) {
            crossView = buildCrossServer(
                    ctx, config, economy, persistence, bus, clock, listeners, menus, menuBindings, dataFolder);
        }
        TradeCommand command =
                new TradeCommand(requests, cooldown, config, sessions, view::open, kernel.messages(), crossView);
        return new Wired(List.of(command), List.copyOf(listeners), config, sessions, view, crossView);
    }

    /**
     * Build the cross-server escrow flow: the jOOQ escrow store over the shared DB, the item delivery and bus seams, the
     * pure {@link CrossServerTrade} coordinator, and the solo trade window. The bus subscriber routes every inbound
     * signal to both the coordinator (the {@code READY}/{@code COMMIT}/{@code ABORT} escrow engine) and the view (the
     * rendezvous), and a join listener reconciles a rejoining player's dangling escrow. Returns the view the command
     * opens a remote trade through and appends the window and reconcile listeners to {@code listeners}.
     */
    private static CrossServerTradeView buildCrossServer(
            ModuleContext ctx,
            TradeConfig config,
            @Nullable TradeEconomy economy,
            Persistence persistence,
            Bus bus,
            Clock clock,
            List<Listener> listeners,
            Menus menus,
            MenuBindings menuBindings,
            Path dataFolder) {
        KernelPorts kernel = ctx.kernel();
        TradeEscrowStore escrows = TradeRepositories.escrowStore(persistence);
        TradeItemDelivery delivery = new BukkitTradeItemDelivery(kernel.scheduler());
        BusTradeBus tradeBus = new BusTradeBus(bus.publisher(), config.crossServer(), kernel.log());
        TradeEconomy escrowEconomy = economy != null ? economy : itemsOnlyEconomy();
        CrossServerTrade coordinator =
                new CrossServerTrade(escrows, escrowEconomy, tradeBus, delivery, clock, kernel.log());
        CrossServerTradeView crossView = new CrossServerTradeView(
                kernel.messages(),
                kernel.messageSink(),
                kernel.scheduler(),
                coordinator,
                tradeBus,
                new CrossTradeWindow(kernel.messages(), menus, dataFolder, kernel.log()));
        crossView.register(menuBindings);
        java.util.function.Consumer<TradeSignal> route = signal -> {
            coordinator.onSignal(signal);
            crossViewOnSignal(crossView, signal);
        };
        tradeBus.subscribe(route);
        bus.registry().register(tradeBus::onFrame);
        listeners.add(crossView.newListener());
        listeners.add(new TradeReconcileListener(kernel.scheduler(), coordinator));
        return crossView;
    }

    private static void crossViewOnSignal(CrossServerTradeView crossView, TradeSignal signal) {
        crossView.onSignal(signal);
    }

    /** A no-op economy for an items-only cross-server install (no economy provider); money is never staked in v1. */
    private static TradeEconomy itemsOnlyEconomy() {
        return new TradeEconomy() {
            @Override
            public boolean canAfford(
                    com.uxplima.uxmessentials.shared.domain.PlayerRef who,
                    java.math.BigDecimal amount,
                    String currencyId) {
                return true;
            }

            @Override
            public boolean transfer(
                    com.uxplima.uxmessentials.shared.domain.PlayerRef from,
                    com.uxplima.uxmessentials.shared.domain.PlayerRef to,
                    java.math.BigDecimal amount,
                    String currencyId) {
                return true;
            }

            @Override
            public boolean withdraw(
                    com.uxplima.uxmessentials.shared.domain.PlayerRef who,
                    java.math.BigDecimal amount,
                    String currencyId) {
                return true;
            }

            @Override
            public void deposit(
                    com.uxplima.uxmessentials.shared.domain.PlayerRef who,
                    java.math.BigDecimal amount,
                    String currencyId) {}
        };
    }

    /**
     * Everything the trade module contributes once wired: the Brigadier {@code /trade} command, the window's Bukkit
     * listener, the resolved config, the session registry, and the view. The {@link #open(Player, Player)} convenience
     * opens a window between two online players, the seam {@code /trade accept} and the window tests drive.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the Bukkit listeners to register
     * @param config the resolved trade config snapshot
     * @param sessions the in-memory registry of live trades
     * @param view the trade-window view that opens and drives sessions
     * @param crossView the cross-server trade window to drain on stop, or {@code null} when cross-server trading is off
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            TradeConfig config,
            TradeSessions sessions,
            TradeView view,
            @Nullable CrossServerTradeView crossView) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(sessions, "sessions");
            Objects.requireNonNull(view, "view");
        }

        /** Open a trade window between two online players. */
        public void open(Player a, Player b) {
            view.open(a, b);
        }

        /** Return every in-flight trade's items and close its windows; run on module stop or reload. */
        public void closeAll() {
            view.closeAll();
            if (crossView != null) {
                crossView.flushAll();
            }
        }
    }
}
