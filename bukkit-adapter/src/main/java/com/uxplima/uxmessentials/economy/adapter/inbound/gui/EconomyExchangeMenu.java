package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.ExchangeOutcome;
import com.uxplima.uxmessentials.economy.application.ExchangeService;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.ExchangeRate;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the {@code /exchange} currency-swapper dashboard with the menu engine and opens it. A three-row panel for
 * one source/target currency pair: a source icon (slot 11), a target icon (slot 15), a convert button (slot 13) and a
 * close button. The source and target, and the viewer's balance in each, are read off the tick thread when the panel
 * opens and handed in as the {@link ExchangeSubject} the icons render from, exactly as the old view did; the
 * exchange itself runs off-tick as the {@code /exchange} command does.
 *
 * <p>Clicking the source or target opens the shared paginated {@link CurrencyPickerMenu}; choosing a currency
 * re-opens this panel with that side switched (the subject carries the new source/target), replacing the old inline
 * cycling with the same picker the eco-admin screens use. The convert button captures an amount through the shared
 * input seam, then runs {@link ExchangeService#exchange}. When no rate is configured between the two currencies the
 * convert button is replaced in place by a no-rate marker through a pair of mutually-exclusive view conditions. The
 * menu holds no new domain logic: it replays the old view's handlers through the engine. Every visible string
 * resolves from the economy catalog.
 */
@NullMarked
public final class EconomyExchangeMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "economy-exchange";

    private static final String SPEC_RESOURCE = "modules/economy/gui/economy-exchange.conf";
    private static final int ROWS = 3;

    private final Menus menus;
    private final EconomyProvider economyProvider;
    private final ExchangeService exchangeService;
    private final Scheduler scheduler;
    private final EconomyNotifier notifier;
    private final Messages messages;
    private final TextInput textInput;
    private final CurrencyPickerMenu currencyPicker;

    public EconomyExchangeMenu(
            Menus menus,
            EconomyProvider economyProvider,
            ExchangeService exchangeService,
            Scheduler scheduler,
            EconomyNotifier notifier,
            Messages messages,
            TextInput textInput,
            CurrencyPickerMenu currencyPicker) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.economyProvider = Objects.requireNonNull(economyProvider, "economyProvider");
        this.exchangeService = Objects.requireNonNull(exchangeService, "exchangeService");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.currencyPicker = Objects.requireNonNull(currencyPicker, "currencyPicker");
    }

    /**
     * Register the per-icon placeholders, the rate/no-rate view conditions, the source/target/convert actions, and
     * the spec itself; called once at economy wiring time.
     */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.placeholder(
                "exchange_source_material", ctx -> material(subject(ctx).source()));
        bindings.placeholder(
                "exchange_target_material", ctx -> material(subject(ctx).target()));
        bindings.placeholder(
                "exchange_source_currency", ctx -> subject(ctx).source().plural());
        bindings.placeholder(
                "exchange_target_currency", ctx -> subject(ctx).target().plural());
        bindings.placeholder(
                "exchange_source_balance", ctx -> notifier.amount(subject(ctx).sourceBalance()));
        bindings.placeholder(
                "exchange_target_balance", ctx -> notifier.amount(subject(ctx).targetBalance()));
        bindings.placeholder("exchange_rate", ctx -> rateText(subject(ctx)));
        bindings.placeholder("exchange_fee", ctx -> feeText(subject(ctx)));
        bindings.condition(
                "economy:exchange-has-rate", (ctx, args) -> rate(subject(ctx)).isPresent());
        bindings.condition(
                "economy:exchange-no-rate", (ctx, args) -> rate(subject(ctx)).isEmpty());
        bindings.action("economy:exchange-source", ctx -> openPicker(ctx, true));
        bindings.action("economy:exchange-target", ctx -> openPicker(ctx, false));
        bindings.action("economy:exchange-convert", this::promptConvert);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, ROWS, log));
    }

    /** Open the dashboard with the first two configured currencies as source and target; a no-op with none configured. */
    public void open(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        List<Currency> currencies = new ArrayList<>(economyProvider.currencies());
        if (currencies.isEmpty()) {
            return;
        }
        Currency source = currencies.get(0);
        Currency target = currencies.size() > 1 ? currencies.get(1) : source;
        open(viewer, source, target);
    }

    /**
     * Open the dashboard for {@code source}/{@code target}. The two balances are read off the tick thread, then the
     * panel is opened through the engine, which renders it on the viewer's entity thread: the old view's threading.
     */
    public void open(Player viewer, Currency source, Currency target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        PlayerRef viewerRef = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        scheduler.async(() -> {
            Money sourceBalance = economyProvider.balance(viewerRef, source);
            Money targetBalance = economyProvider.balance(viewerRef, target);
            menus.open(viewerRef, SPEC_ID, new ExchangeSubject(source, target, sourceBalance, targetBalance));
        });
    }

    /**
     * Open the shared picker for the source (or target) side; choosing a currency re-opens this panel with that side
     * switched, skipping the other side so the two never coincide, exactly as the old cycling guarded against.
     */
    private void openPicker(MenuActionContext ctx, boolean pickingSource) {
        Player viewer = ctx.player();
        PlayerRef viewerRef = ctx.viewer();
        ExchangeSubject subject = ctx.subject(ExchangeSubject.class);
        Currency current = pickingSource ? subject.source() : subject.target();
        List<Currency> all = new ArrayList<>(economyProvider.currencies());
        currencyPicker.open(
                viewer, viewerRef, all, current, chosen -> reopenWith(viewer, subject, chosen, pickingSource));
    }

    /** Re-open the panel with {@code chosen} on the picked side; if it would collide with the pinned side, keep as-is. */
    private void reopenWith(Player viewer, ExchangeSubject subject, Currency chosen, boolean pickingSource) {
        Currency pinned = pickingSource ? subject.target() : subject.source();
        if (chosen.equals(pinned)) {
            open(viewer, subject.source(), subject.target());
            return;
        }
        if (pickingSource) {
            open(viewer, chosen, subject.target());
        } else {
            open(viewer, subject.source(), chosen);
        }
    }

    /**
     * Convert button: capture an amount through the input seam, then run the exchange off the tick thread, exactly as
     * the old custom-amount path did. A malformed amount sends the existing rejection and re-opens, no exchange runs.
     */
    private void promptConvert(MenuActionContext ctx) {
        Player viewer = ctx.player();
        PlayerRef viewerRef = ctx.viewer();
        ExchangeSubject subject = ctx.subject(ExchangeSubject.class);
        Map<String, String> placeholders = Map.of(
                "source", subject.source().plural(), "target", subject.target().plural());
        textInput.prompt(
                viewer,
                viewerRef,
                InputRequest.of("exchange.amount", EconomyMessageKey.EXCHANGE_PROMPT, placeholders),
                input -> applyConvert(viewer, viewerRef, subject.source(), subject.target(), input),
                () -> open(viewer, subject.source(), subject.target()));
    }

    /**
     * Parse the typed amount and, when valid, run the exchange off the tick thread, then re-open. A malformed amount
     * sends the existing rejection and re-opens, no exchange runs. Package-private so the amount branch is
     * unit-tested without driving a live anvil, mirroring the old view.
     */
    void applyConvert(Player viewer, PlayerRef viewerRef, Currency source, Currency target, String raw) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(raw);
        } catch (NumberFormatException malformed) {
            viewer.sendMessage(StyledText.render(
                    messages.resolve(viewerRef, EconomyMessageKey.EXCHANGE_INVALID_AMOUNT, Map.of())));
            open(viewer, source, target);
            return;
        }
        scheduler.async(() -> {
            ExchangeOutcome result = exchangeService.exchange(viewerRef, amount, source, target);
            scheduler.onEntity(viewerRef, () -> {
                handleExchangeResult(viewer, viewerRef, result, source, target);
                open(viewer, source, target);
            });
        });
    }

    private void handleExchangeResult(
            Player player, PlayerRef viewerRef, ExchangeOutcome result, Currency source, Currency target) {
        switch (result.status()) {
            case SUCCESS ->
                send(
                        player,
                        viewerRef,
                        EconomyMessageKey.EXCHANGE_SUCCESS,
                        Map.of(
                                "source-amount", notifier.amount(Money.of(source, result.sourceAmount())),
                                "target-amount", notifier.amount(Money.of(target, result.targetAmount()))));
            case RATE_NOT_FOUND -> send(player, viewerRef, EconomyMessageKey.EXCHANGE_RATE_NOT_FOUND, Map.of());
            case INSUFFICIENT_FUNDS -> send(player, viewerRef, EconomyMessageKey.EXCHANGE_INSUFFICIENT_FUNDS, Map.of());
            case LIMIT_EXCEEDED -> send(player, viewerRef, EconomyMessageKey.EXCHANGE_LIMIT_EXCEEDED, Map.of());
            case PROVIDER_UNSUPPORTED ->
                send(player, viewerRef, EconomyMessageKey.EXCHANGE_PROVIDER_UNSUPPORTED, Map.of());
            case CURRENCY_DISABLED -> send(player, viewerRef, EconomyMessageKey.EXCHANGE_CURRENCY_DISABLED, Map.of());
            case FAILED -> {
                com.uxplima.uxmessentials.economy.domain.TransferError err = result.error();
                send(player, viewerRef, err != null ? err.messageKey() : EconomyMessageKey.PAY_ERROR, Map.of());
            }
        }
    }

    private void send(Player player, PlayerRef viewerRef, EconomyMessageKey key, Map<String, String> placeholders) {
        player.sendMessage(StyledText.render(messages.resolve(viewerRef, key, placeholders)));
    }

    private ExchangeSubject subject(MenuContext ctx) {
        return ctx.subject(ExchangeSubject.class);
    }

    private Optional<ExchangeRate> rate(ExchangeSubject subject) {
        return exchangeService
                .registry()
                .findRate(subject.source().id(), subject.target().id());
    }

    private String rateText(ExchangeSubject subject) {
        return rate(subject).map(r -> r.rate().toPlainString()).orElse("");
    }

    private String feeText(ExchangeSubject subject) {
        return rate(subject)
                .map(r -> r.feePercent()
                        .multiply(BigDecimal.valueOf(100))
                        .stripTrailingZeros()
                        .toPlainString())
                .orElse("");
    }

    /** The per-currency icon material name, paper by default, resolved through the operator-configured icon-material. */
    private static String material(Currency currency) {
        return CurrencyIcons.materialFor(currency, Material.PAPER).name();
    }

    /**
     * The subject of an open exchange dashboard: the source and target currencies and the viewer's balance in each,
     * read at open time. The icon placeholders read these directly, so the render touches no port; switching either
     * side re-opens with a fresh subject carrying the new pair and the freshly-read balances.
     *
     * @param source the currency the viewer spends
     * @param target the currency the viewer receives
     * @param sourceBalance the viewer's balance in the source currency, read at open time
     * @param targetBalance the viewer's balance in the target currency, read at open time
     */
    public record ExchangeSubject(Currency source, Currency target, Money sourceBalance, Money targetBalance) {

        public ExchangeSubject {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(sourceBalance, "sourceBalance");
            Objects.requireNonNull(targetBalance, "targetBalance");
        }
    }
}
