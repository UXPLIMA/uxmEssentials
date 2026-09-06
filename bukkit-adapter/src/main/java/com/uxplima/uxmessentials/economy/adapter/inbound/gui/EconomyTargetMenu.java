package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.AmountParseError;
import com.uxplima.uxmessentials.economy.domain.AmountParser;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the per-player eco-admin manage screen with the menu engine and opens it. A five-row panel for one
 * target player: the target's head with their balance per currency, the Give / Take / Set / Reset action buttons,
 * a [History] link into the engine transaction-history list, and, when more than one currency is configured, a
 * [Currency] item that opens the shared paginated {@link CurrencyPickerMenu} to switch the active currency the four
 * actions apply to. Give / Take / Set capture an amount through the shared input seam and run the matching
 * {@code EcoAdmin} op with {@code Money.of(active, amount)}; Reset is confirm-gated through the engine confirm
 * dialog before zeroing the balance.
 *
 * <p>The balances are read off the tick thread when the panel opens and handed in as the {@link TargetSubject} the
 * head and the buttons render from, exactly as the old view did; the op itself is dispatched off-tick as the
 * {@code /eco} command does. The menu holds no new domain logic. It replays the old view's handlers verbatim
 * through the engine. Every visible string resolves from the economy catalog.
 */
@NullMarked
public final class EconomyTargetMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "economy-target";

    private static final String SPEC_RESOURCE = "modules/economy/gui/economy-target.conf";
    private static final int ROWS = 5;

    private final Menus menus;
    private final GuiText guiText;
    private final Messages messages;
    private final Scheduler scheduler;
    private final TextInput textInput;
    private final EconomyProvider economy;
    private final EcoAdminOps ops;
    private final CurrencyRegistry currencies;
    private final EconomyNotifier notifier;
    private final TransactionsHistoryMenu historyView;
    private final CurrencyPickerMenu currencyPicker;
    private final BiConsumer<Player, PlayerRef> onBack;

    public EconomyTargetMenu(
            Menus menus,
            GuiText guiText,
            Messages messages,
            Scheduler scheduler,
            TextInput textInput,
            EconomyProvider economy,
            EcoAdminOps ops,
            CurrencyRegistry currencies,
            EconomyNotifier notifier,
            TransactionsHistoryMenu historyView,
            CurrencyPickerMenu currencyPicker,
            BiConsumer<Player, PlayerRef> onBack) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.ops = Objects.requireNonNull(ops, "ops");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.historyView = Objects.requireNonNull(historyView, "historyView");
        this.currencyPicker = Objects.requireNonNull(currencyPicker, "currencyPicker");
        this.onBack = Objects.requireNonNull(onBack, "onBack");
    }

    /**
     * Register the bindings the spec names. The four action buttons, the history/back/select-currency clicks, the
     * subject placeholders, and the multi-currency view condition, and the spec itself. The {@code eco_currency}
     * placeholder and the {@code economy:multi-currency} condition are shared with the bulk screen, so they are
     * registered once here (both screens are wired against one {@code MenuBindings}).
     */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.placeholder("eco_target", ctx -> subject(ctx).target().name());
        bindings.placeholder(
                "eco_currency",
                ctx -> ctx.subject(EcoCurrencySubject.class).active().plural());
        bindings.placeholder("eco_target_balances", this::balancesLore);
        bindings.condition(
                "economy:multi-currency", (ctx, args) -> currencies.all().size() > 1);
        bindings.action("economy:target-give", ctx -> prompt(ctx, EcoAdminOps.Verb.GIVE));
        bindings.action("economy:target-take", ctx -> prompt(ctx, EcoAdminOps.Verb.TAKE));
        bindings.action("economy:target-set", ctx -> prompt(ctx, EcoAdminOps.Verb.SET));
        bindings.action("economy:target-reset", this::confirmReset);
        bindings.action("economy:target-history", this::openHistory);
        bindings.action("economy:target-select-currency", this::openCurrencyPicker);
        bindings.action("economy:target-back", ctx -> onBack.accept(ctx.player(), ctx.viewer()));
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, ROWS, log));
    }

    /** Open the manage screen for {@code target} with the default currency active. */
    public void open(Player viewer, PlayerRef viewerRef, PlayerRef target) {
        open(viewer, viewerRef, target, currencies.defaultCurrency());
    }

    /**
     * Open the manage screen for {@code target} with {@code active} as the currency the actions apply to. The
     * balances are read off the tick thread, then the panel is opened through the engine, which renders it on the
     * viewer's entity thread.
     */
    public void open(Player viewer, PlayerRef viewerRef, PlayerRef target, Currency active) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(viewerRef, "viewerRef");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(active, "active");
        scheduler.async(() -> {
            List<Money> balances = readBalances(target);
            menus.open(viewerRef, SPEC_ID, new TargetSubject(target, active, List.copyOf(balances)));
        });
    }

    private List<Money> readBalances(PlayerRef target) {
        List<Money> balances = new ArrayList<>();
        for (Currency currency : currencies.all()) {
            balances.add(economy.balance(target, currency));
        }
        return balances;
    }

    /** The head's balance block: one {@code target-head-lore} line per carried balance, joined for the engine to split. */
    private String balancesLore(MenuContext ctx) {
        return subject(ctx).balances().stream()
                .map(balance -> messages.resolve(
                        ctx.viewer(),
                        EconomyMessageKey.ECO_ADMIN_GUI_TARGET_HEAD_LORE,
                        Map.of("eco_currency", balance.currency().plural(), "eco_amount", notifier.amount(balance))))
                .collect(Collectors.joining("\n"));
    }

    /** Capture an amount through the input seam, then dispatch {@code verb} for the active currency, exactly as before. */
    private void prompt(MenuActionContext ctx, EcoAdminOps.Verb verb) {
        Player viewer = ctx.player();
        PlayerRef viewerRef = ctx.viewer();
        TargetSubject subject = ctx.subject(TargetSubject.class);
        textInput.prompt(
                viewer,
                viewerRef,
                InputRequest.of("eco.amount", EconomyMessageKey.ECO_ADMIN_GUI_AMOUNT_PROMPT),
                text -> applyAmount(viewer, viewerRef, subject.target(), subject.active(), verb, text),
                () -> open(viewer, viewerRef, subject.target(), subject.active()));
    }

    /**
     * Parse the typed amount against the active currency and, when valid, dispatch the op off the tick thread, then
     * re-open the screen. A malformed amount sends the existing parse-error rejection and re-opens, no op runs.
     * Package-private so the amount branch is unit-tested without driving a live anvil, mirroring the old view.
     */
    void applyAmount(
            Player viewer, PlayerRef viewerRef, PlayerRef target, Currency active, EcoAdminOps.Verb verb, String raw) {
        Result<Money, AmountParseError> parsed = AmountParser.parse(raw, active);
        if (parsed.isErr()) {
            notifier.send(viewerRef, parsed.errorOrThrow().messageKey());
            open(viewer, viewerRef, target, active);
            return;
        }
        Money money = parsed.orElseThrow();
        scheduler.async(() -> ops.dispatch(verb, viewerRef, target, money));
        open(viewer, viewerRef, target, active);
    }

    /** Confirm-gate the reset through the engine confirm dialog; yes zeroes the balance off-tick and re-opens. */
    private void confirmReset(MenuActionContext ctx) {
        Player viewer = ctx.player();
        PlayerRef viewerRef = ctx.viewer();
        TargetSubject subject = ctx.subject(TargetSubject.class);
        PlayerRef target = subject.target();
        Currency active = subject.active();
        Component title = guiText.text(
                viewerRef, EconomyMessageKey.ECO_ADMIN_GUI_RESET_CONFIRM_TITLE, Map.of("eco_target", target.name()));
        menus.confirm(
                viewerRef,
                title,
                () -> {
                    scheduler.async(() -> ops.reset(viewerRef, target, active));
                    open(viewer, viewerRef, target, active);
                },
                () -> open(viewer, viewerRef, target, active));
    }

    /** Open the read-only transaction-history list for this target, exactly as the old history button did. */
    private void openHistory(MenuActionContext ctx) {
        TargetSubject subject = ctx.subject(TargetSubject.class);
        PlayerRef target = subject.target();
        historyView.open(ctx.viewer(), target.uuid(), target.name());
    }

    /** Open the shared picker; choosing a currency re-opens this screen with it active (subject carries the new active). */
    private void openCurrencyPicker(MenuActionContext ctx) {
        Player viewer = ctx.player();
        PlayerRef viewerRef = ctx.viewer();
        TargetSubject subject = ctx.subject(TargetSubject.class);
        PlayerRef target = subject.target();
        List<Currency> all = List.copyOf(currencies.all());
        currencyPicker.open(
                viewer, viewerRef, all, subject.active(), chosen -> open(viewer, viewerRef, target, chosen));
    }

    private TargetSubject subject(MenuContext ctx) {
        return ctx.subject(TargetSubject.class);
    }

    /**
     * The subject of an open manage screen: the target being managed, the currency the actions apply to, and the
     * target's already-read balance per currency. The head and button placeholders read these directly, so the
     * render touches no port; switching currency re-opens with a fresh subject carrying the new active.
     *
     * @param target the player whose wallet is being managed
     * @param active the currency the Give / Take / Set / Reset actions apply to
     * @param balances the target's balance per configured currency, read at open time
     */
    public record TargetSubject(PlayerRef target, Currency active, List<Money> balances) implements EcoCurrencySubject {

        public TargetSubject {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(active, "active");
            balances = List.copyOf(balances);
        }
    }
}
