package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the server-wide eco-admin screen with the menu engine and opens it. A three-row panel reached from the
 * bare-/eco hub's [Server-wide] button: Give to All (an amount captured through the shared input seam, credited to
 * every online wallet) and Reset All (confirm-gated, zeroing every online balance), with, when more than one
 * currency is configured: a shared paginated {@link CurrencyPickerMenu} between them to switch the active currency.
 * Both ops operate on the currently-online roster, the same scope the {@code /eco giveall|resetall} commands use.
 *
 * <p>The screen carries only the active currency as its {@link BulkSubject}, so each button's active-currency line
 * fills from the shared {@code eco_currency} placeholder without the renderer touching a port. The roster is
 * enumerated on the global region thread (the one thread {@code Server.getOnlinePlayers()} is safely readable on
 * Folia) when an op fires, snapshotted to {@link PlayerRef}s, and the bulk op runs off the tick thread, exactly as
 * the old view did. The menu holds no new domain logic. Every visible string resolves from the economy catalog.
 *
 * <p>The shared {@code eco_currency} placeholder and the {@code economy:multi-currency} view condition are
 * registered by {@link EconomyTargetMenu} (both screens are wired against one {@code MenuBindings}), so this menu
 * registers only its own bulk actions.
 */
@NullMarked
public final class EconomyBulkMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "economy-bulk";

    private static final String SPEC_RESOURCE = "modules/economy/gui/economy-bulk.conf";
    private static final int ROWS = 3;

    private final Menus menus;
    private final GuiText guiText;
    private final Scheduler scheduler;
    private final TextInput textInput;
    private final Server server;
    private final EcoAdminOps ops;
    private final CurrencyRegistry currencies;
    private final EconomyNotifier notifier;
    private final CurrencyPickerMenu currencyPicker;
    private final BiConsumer<Player, PlayerRef> onBack;

    public EconomyBulkMenu(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            TextInput textInput,
            Server server,
            EcoAdminOps ops,
            CurrencyRegistry currencies,
            EconomyNotifier notifier,
            CurrencyPickerMenu currencyPicker,
            BiConsumer<Player, PlayerRef> onBack) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.server = Objects.requireNonNull(server, "server");
        this.ops = Objects.requireNonNull(ops, "ops");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.currencyPicker = Objects.requireNonNull(currencyPicker, "currencyPicker");
        this.onBack = Objects.requireNonNull(onBack, "onBack");
    }

    /** Register the bulk actions the spec names and the spec itself; called once at economy wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.action("economy:bulk-giveall", this::promptGiveAll);
        bindings.action("economy:bulk-resetall", this::confirmResetAll);
        bindings.action("economy:bulk-select-currency", this::openCurrencyPicker);
        bindings.action("economy:bulk-back", ctx -> onBack.accept(ctx.player(), ctx.viewer()));
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, ROWS, log));
    }

    /** Open the server-wide screen for {@code viewer}, with the default currency active. */
    public void open(Player viewer, PlayerRef viewerRef) {
        open(viewer, viewerRef, currencies.defaultCurrency());
    }

    /** Open the server-wide screen for {@code viewer} with {@code active} as the currency the actions apply to. */
    public void open(Player viewer, PlayerRef viewerRef, Currency active) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(viewerRef, "viewerRef");
        Objects.requireNonNull(active, "active");
        menus.open(viewerRef, SPEC_ID, new BulkSubject(active));
    }

    /** Capture an amount through the input seam, then credit every online wallet, exactly as before. */
    private void promptGiveAll(MenuActionContext ctx) {
        Player viewer = ctx.player();
        PlayerRef viewerRef = ctx.viewer();
        Currency active = ctx.subject(BulkSubject.class).active();
        textInput.prompt(
                viewer,
                viewerRef,
                InputRequest.of("eco.bulk-amount", EconomyMessageKey.ECO_ADMIN_GUI_AMOUNT_PROMPT),
                text -> applyGiveAll(viewer, viewerRef, active, text),
                () -> open(viewer, viewerRef, active));
    }

    /**
     * Parse the typed amount, and on success snapshot the online roster on the global thread and credit it off the
     * tick thread, then re-open. A malformed amount sends the existing parse-error rejection and re-opens, no op
     * runs. Package-private so the amount branch is unit-tested without driving a live anvil, mirroring the old view.
     */
    void applyGiveAll(Player viewer, PlayerRef viewerRef, Currency active, String raw) {
        Result<Money, AmountParseError> parsed = AmountParser.parse(raw, active);
        if (parsed.isErr()) {
            notifier.send(viewerRef, parsed.errorOrThrow().messageKey());
            open(viewer, viewerRef, active);
            return;
        }
        Money money = parsed.orElseThrow();
        scheduler.onGlobal(() -> {
            List<PlayerRef> roster = roster();
            scheduler.async(() -> ops.giveAll(viewerRef, roster, money));
        });
        open(viewer, viewerRef, active);
    }

    /** Confirm-gate the reset-all through the engine confirm dialog; yes zeroes every online balance and re-opens. */
    private void confirmResetAll(MenuActionContext ctx) {
        Player viewer = ctx.player();
        PlayerRef viewerRef = ctx.viewer();
        Currency active = ctx.subject(BulkSubject.class).active();
        Component title = guiText.text(viewerRef, EconomyMessageKey.ECO_ADMIN_GUI_RESETALL_CONFIRM_TITLE);
        menus.confirm(
                viewerRef, title, () -> resetAll(viewer, viewerRef, active), () -> open(viewer, viewerRef, active));
    }

    /** Snapshot the online roster on the global thread and reset it off the tick thread, then re-open. */
    void resetAll(Player viewer, PlayerRef viewerRef, Currency active) {
        scheduler.onGlobal(() -> {
            List<PlayerRef> roster = roster();
            scheduler.async(() -> ops.resetAll(viewerRef, roster, active));
        });
        open(viewer, viewerRef, active);
    }

    /** Open the shared picker; choosing a currency re-opens this screen with it active (subject carries the new active). */
    private void openCurrencyPicker(MenuActionContext ctx) {
        Player viewer = ctx.player();
        PlayerRef viewerRef = ctx.viewer();
        Currency active = ctx.subject(BulkSubject.class).active();
        List<Currency> all = List.copyOf(currencies.all());
        currencyPicker.open(viewer, viewerRef, all, active, chosen -> open(viewer, viewerRef, chosen));
    }

    private List<PlayerRef> roster() {
        List<PlayerRef> refs = new ArrayList<>();
        for (Player online : server.getOnlinePlayers()) {
            refs.add(BukkitRefs.toRef(online));
        }
        return List.copyOf(refs);
    }

    /**
     * The subject of an open server-wide screen: the currency the bulk actions apply to. The button placeholders
     * read {@link #active()} through the shared {@code eco_currency} placeholder, so the render touches no port;
     * switching currency re-opens with a fresh subject carrying the new active.
     *
     * @param active the currency the bulk give-all / reset-all apply to
     */
    public record BulkSubject(Currency active) implements EcoCurrencySubject {

        public BulkSubject {
            Objects.requireNonNull(active, "active");
        }
    }
}
