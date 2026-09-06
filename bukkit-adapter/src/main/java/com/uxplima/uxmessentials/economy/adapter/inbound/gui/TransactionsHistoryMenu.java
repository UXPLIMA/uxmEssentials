package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.port.HistoryRecord;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Registers the read-only transaction-history list with the menu engine and opens it. One spec serves the three
 * original entry points (a player's history, the global log, and a bank's logs) because they differ only in the
 * records they query and the title; the grid, nav buttons, and per-record icons are identical. A click does
 * nothing, because a ledger row is immutable.
 *
 * <p>The history read is a DB query (it touches no Bukkit API), so each open runs {@code flush} plus the bounded
 * query off the tick thread and hands the already-read rows in as the menu subject; the {@code economy:transactions}
 * list source only reads that subject. The subject also carries the title key and its argument, so the single
 * {@code @%history_title_key%} title resolves to the global/player/bank line and fills {@code {history_player}} /
 * {@code {history_bank}} from the same open. The {@code history_*} placeholders fill each icon from the bound
 * record, prefixed so they never collide with the generic engine tokens. Every label resolves from the economy
 * catalog, so no user-facing text lives here. The geometry mirrors the original paginated list: a grid across the
 * top five rows, the two nav arrows, and a close button on the bottom row.
 */
@NullMarked
public final class TransactionsHistoryMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "economy-transactions";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/economy/gui/economy-transactions.conf";

    /** Mirrors the per-page cap the original view used, so the off-thread read stays bounded. */
    private static final int PAGE_LIMIT = 200;

    private final Menus menus;
    private final TransactionHistory history;
    private final Scheduler scheduler;
    private final DateTimeFormatter formatter;

    public TransactionsHistoryMenu(Menus menus, TransactionHistory history, Scheduler scheduler, ZoneId timeZone) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.history = Objects.requireNonNull(history, "history");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        // The render zone is configured (default UTC), not the host's default, so a record reads the same on every
        // server in a network. The formatter is built once here, never on the GUI-open hot path.
        this.formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(Objects.requireNonNull(timeZone, "timeZone"));
    }

    /** Register the list source, the per-record and title placeholders, and the spec itself; called once at wiring. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("economy:transactions", ctx -> subject(ctx).records());
        bindings.placeholder("history_title_key", ctx -> subject(ctx).titleKey());
        // The player and bank titles each take one argument; both read the subject's single title argument, and only
        // the token the active title key names is filled: the other simply goes unused.
        bindings.placeholder("history_player", ctx -> subject(ctx).titleArg());
        bindings.placeholder("history_bank", ctx -> subject(ctx).titleArg());
        bindings.placeholder("history_icon", ctx -> iconMaterial(entry(ctx)));
        bindings.placeholder("history_type_key", ctx -> typeKey(entry(ctx)));
        bindings.placeholder("history_id", ctx -> Long.toString(entry(ctx).id()));
        bindings.placeholder("history_date", ctx -> formatter.format(entry(ctx).timestamp()));
        bindings.placeholder("history_from", ctx -> sender(entry(ctx)));
        bindings.placeholder("history_to", ctx -> receiver(entry(ctx)));
        bindings.placeholder("history_amount", ctx -> amountText(entry(ctx)));
        bindings.placeholder("history_reason", ctx -> entry(ctx).reason());
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /** Resolve {@code targetPlayer}'s history (or the global log when null) off-thread, then open it for {@code viewer}. */
    public void open(PlayerRef viewer, @Nullable UUID targetPlayer, String targetName) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(targetName, "targetName");
        scheduler.async(() -> {
            history.flush();
            HistoryView snapshot = targetPlayer == null
                    ? new HistoryView(
                            List.copyOf(history.queryGlobalTransactions(PAGE_LIMIT, 0)),
                            EconomyMessageKey.HISTORY_GUI_TITLE_GLOBAL.key(),
                            "")
                    : new HistoryView(
                            List.copyOf(history.queryTransactions(targetPlayer, PAGE_LIMIT, 0)),
                            EconomyMessageKey.HISTORY_GUI_TITLE_PLAYER.key(),
                            targetName);
            menus.open(viewer, SPEC_ID, snapshot);
        });
    }

    /** Resolve {@code bankId}'s logs off-thread, then open the read-only list for {@code viewer}. */
    public void openForBank(PlayerRef viewer, String bankId, String bankName) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(bankId, "bankId");
        Objects.requireNonNull(bankName, "bankName");
        scheduler.async(() -> {
            history.flush();
            HistoryView snapshot = new HistoryView(
                    List.copyOf(history.queryBankTransactions(bankId, PAGE_LIMIT, 0)),
                    EconomyMessageKey.HISTORY_GUI_TITLE_BANK.key(),
                    bankName);
            menus.open(viewer, SPEC_ID, snapshot);
        });
    }

    private HistoryView subject(MenuContext ctx) {
        return ctx.subject(HistoryView.class);
    }

    private HistoryRecord entry(MenuContext ctx) {
        return ctx.entry(HistoryRecord.class);
    }

    /** The per-record icon material name, mirroring the deleted view's switch so the grid renders identically. */
    private static String iconMaterial(HistoryRecord record) {
        if (record.reason().startsWith("ADMIN_")) {
            return "GOLD_BLOCK";
        }
        if ("CREDIT".equalsIgnoreCase(record.type())) {
            return "GREEN_WOOL";
        }
        if ("DEBIT".equalsIgnoreCase(record.type())) {
            return "RED_WOOL";
        }
        return "PAPER";
    }

    /** The type-label catalog key for a record, mirroring the deleted view's switch. */
    private static String typeKey(HistoryRecord record) {
        if (record.reason().startsWith("ADMIN_")) {
            return EconomyMessageKey.HISTORY_GUI_TYPE_ADMIN.key();
        }
        if ("CREDIT".equalsIgnoreCase(record.type())) {
            return EconomyMessageKey.HISTORY_GUI_TYPE_CREDIT.key();
        }
        if ("DEBIT".equalsIgnoreCase(record.type())) {
            return EconomyMessageKey.HISTORY_GUI_TYPE_DEBIT.key();
        }
        return EconomyMessageKey.HISTORY_GUI_TYPE_TRANSFER.key();
    }

    private static String sender(HistoryRecord record) {
        if (record.fromName() != null) {
            return record.fromName();
        }
        return record.fromUuid() != null ? record.fromUuid() : "-";
    }

    private static String receiver(HistoryRecord record) {
        if (record.toName() != null) {
            return record.toName();
        }
        return record.toUuid() != null ? record.toUuid() : "-";
    }

    private static String amountText(HistoryRecord record) {
        return record.amount().toPlainString() + " " + record.currency();
    }

    /**
     * The subject of an open history grid: the already-read, already-bounded records plus the title key and its
     * single argument. The list source reads {@link #records()}; the title placeholders read {@link #titleKey()}
     * and {@link #titleArg()}, so the one spec renders the global, player, or bank line from the same open.
     *
     * @param records the rows to render, newest-first, capped at {@link #PAGE_LIMIT}
     * @param titleKey the catalog key for the title line (global, player, or bank)
     * @param titleArg the {@code {history_player}}/{@code {history_bank}} argument that title takes, or empty
     */
    public record HistoryView(List<HistoryRecord> records, String titleKey, String titleArg) {

        public HistoryView {
            records = List.copyOf(Objects.requireNonNull(records, "records"));
            Objects.requireNonNull(titleKey, "titleKey");
            Objects.requireNonNull(titleArg, "titleArg");
        }
    }
}
