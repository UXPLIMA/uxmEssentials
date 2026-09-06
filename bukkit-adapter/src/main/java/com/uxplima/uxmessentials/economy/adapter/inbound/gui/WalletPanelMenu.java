package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the personal wallet dashboard ({@code /wallet [currency]}, and the economy entry on the
 * {@code /uxmess gui} hub) with the menu engine and opens it. A three-row panel for one currency: a virtual-balance
 * display, a physical-banknotes tally, a button into the engine transaction-history list, and a close button. The
 * menu holds no new domain logic; it reads the balance and tallies the viewer's banknotes off the tick thread when
 * the panel opens, exactly as the old view did, and hands both in as the {@link WalletSubject} the two display
 * items render from, then registers the per-display placeholders and the history-open action and loads the
 * {@code economy-wallet} spec.
 *
 * <p>The balance read and the banknote scan run on the same off-tick path the old dashboard used; the panel itself
 * is then opened through {@link Menus}, which hops to the viewer's entity thread to touch the live inventory. The
 * history button reopens the already-migrated {@code economy-transactions} list through {@link TransactionsHistoryMenu},
 * and the close button rides the engine's generic {@code close} action. Every visible string resolves from the
 * economy catalog.
 */
@NullMarked
public final class WalletPanelMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "economy-wallet";

    private static final String SPEC_RESOURCE = "modules/economy/gui/economy-wallet.conf";

    private final Menus menus;
    private final EconomyProvider economyProvider;
    private final Scheduler scheduler;
    private final EconomyNotifier notifier;
    private final TransactionsHistoryMenu historyView;
    private final NamespacedKey valueKey;
    private final NamespacedKey currencyKey;

    public WalletPanelMenu(
            Plugin plugin,
            Menus menus,
            EconomyProvider economyProvider,
            Scheduler scheduler,
            EconomyNotifier notifier,
            TransactionsHistoryMenu historyView) {
        Objects.requireNonNull(plugin, "plugin");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.economyProvider = Objects.requireNonNull(economyProvider, "economyProvider");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.historyView = Objects.requireNonNull(historyView, "historyView");
        // Created once here, never on the GUI-open hot path (CLAUDE.md NamespacedKey rule).
        this.valueKey = new NamespacedKey(plugin, "banknote_value");
        this.currencyKey = new NamespacedKey(plugin, "banknote_currency");
    }

    /** Register the per-display placeholders and the history-open action the spec names, and the spec itself. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.placeholder(
                "wallet_balance", ctx -> notifier.amount(subject(ctx).balance()));
        bindings.placeholder(
                "wallet_banknotes", ctx -> notifier.amount(subject(ctx).banknotes()));
        bindings.action("economy:open-history", this::openHistory);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 3, log));
    }

    /**
     * Open the dashboard for {@code viewer}, ranging the balance and the carried-banknote total for {@code currency}.
     * The balance read and the inventory scan run off the tick thread (the old view's threading), then the panel is
     * opened through the engine, which renders it on the viewer's entity thread.
     */
    public void open(Player viewer, Currency currency) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(currency, "currency");
        PlayerRef viewerRef = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        scheduler.async(() -> {
            Money balance = economyProvider.balance(viewerRef, currency);
            Money banknotes = Money.of(currency, calculateBanknotesValue(viewer, currency));
            menus.open(viewerRef, SPEC_ID, new WalletSubject(currency, balance, banknotes));
        });
    }

    /** Open the read-only transaction-history list for the viewer, exactly as the old history button did. */
    private void openHistory(MenuActionContext ctx) {
        Player player = ctx.player();
        historyView.open(ctx.viewer(), player.getUniqueId(), player.getName());
    }

    private WalletSubject subject(MenuContext ctx) {
        return ctx.subject(WalletSubject.class);
    }

    /**
     * Tally the value of the banknotes the viewer is carrying for {@code currency}, summing each PAPER stack whose
     * persistent value and currency keys match. Mirrors the old dashboard's scan; a malformed value stamp is skipped
     * rather than aborting the tally.
     */
    private BigDecimal calculateBanknotesValue(Player player, Currency currency) {
        BigDecimal total = BigDecimal.ZERO;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != Material.PAPER) {
                continue;
            }
            total = total.add(banknoteValue(item, currency));
        }
        return total;
    }

    /** The value one PAPER stack contributes to the tally, or zero when it is not a matching banknote. */
    private BigDecimal banknoteValue(ItemStack item, Currency currency) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return BigDecimal.ZERO;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String valStr = pdc.get(valueKey, PersistentDataType.STRING);
        String currStr = pdc.get(currencyKey, PersistentDataType.STRING);
        if (valStr == null
                || currStr == null
                || !currStr.equalsIgnoreCase(currency.id().value())) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(valStr).multiply(BigDecimal.valueOf(item.getAmount()));
        } catch (NumberFormatException malformed) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * The subject of an open wallet dashboard: the currency it displays plus the already-read balance and the
     * already-tallied carried-banknote total. The two display placeholders read {@link #balance()} and
     * {@link #banknotes()} directly, so the render touches no port.
     *
     * @param currency the currency the dashboard displays
     * @param balance the viewer's virtual balance for that currency, read at open time
     * @param banknotes the value of the physical banknotes the viewer carries for that currency, tallied at open time
     */
    public record WalletSubject(Currency currency, Money balance, Money banknotes) {

        public WalletSubject {
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(balance, "balance");
            Objects.requireNonNull(banknotes, "banknotes");
        }
    }
}
