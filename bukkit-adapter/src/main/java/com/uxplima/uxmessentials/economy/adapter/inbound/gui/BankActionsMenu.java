package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import com.uxplima.uxmessentials.economy.application.BankService;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.AmountParseError;
import com.uxplima.uxmessentials.economy.domain.AmountParser;
import com.uxplima.uxmessentials.economy.domain.BankError;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
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
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the per-bank actions panel with the menu engine and opens it. A three-row panel reached by clicking a
 * bank in the {@code /bank} list: deposit, withdraw, members, logs and back. The bank the panel acts on is handed in
 * as the {@link BankActionsSubject} when the panel opens, so the title fills its argument from the bound bank without
 * the renderer touching a port; every button resolves its label from the economy catalog.
 *
 * <p>Deposit and withdraw capture an amount through the shared input seam, then the bank use case runs off the tick
 * thread, exactly as the old view did. Members opens the engine bank-members grid, logs opens the engine
 * transaction-history list scoped to this bank, and back reopens the engine bank list. All resolved through the
 * {@link BankNavigation} supplier injected at construction so the cross-links stay final and non-null without
 * post-construction setters. The menu holds no new domain logic. It replays the old view's handlers verbatim through
 * the engine.
 */
@NullMarked
public final class BankActionsMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "economy-bank-actions";

    private static final String SPEC_RESOURCE = "modules/economy/gui/economy-bank-actions.conf";
    private static final int ROWS = 3;

    private final Menus menus;
    private final BankService bankService;
    private final TextInput textInput;
    private final Scheduler scheduler;
    private final Messages messages;
    private final TransactionsHistoryMenu historyView;
    private final Supplier<BankNavigation> navigation;

    public BankActionsMenu(
            Menus menus,
            BankService bankService,
            TextInput textInput,
            Scheduler scheduler,
            Messages messages,
            TransactionsHistoryMenu historyView,
            Supplier<BankNavigation> navigation) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.bankService = Objects.requireNonNull(bankService, "bankService");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.historyView = Objects.requireNonNull(historyView, "historyView");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    /** Register the title placeholder, the five button actions, and the spec itself; called once at wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.placeholder("bank_actions_name", ctx -> subject(ctx).bank().name());
        bindings.action("economy:bank-deposit", ctx -> promptTransfer(ctx, true));
        bindings.action("economy:bank-deposit-withdraw", ctx -> promptTransfer(ctx, false));
        bindings.action("economy:bank-members", this::openMembers);
        bindings.action("economy:bank-logs", this::openLogs);
        bindings.action("economy:bank-actions-back", this::openBankList);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, ROWS, log));
    }

    /** Open the actions panel for {@code bank}; the bank is the subject the title and buttons act on. */
    public void open(Player player, SharedBank bank) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(bank, "bank");
        PlayerRef viewer = new PlayerRef(player.getUniqueId(), player.getName());
        menus.open(viewer, SPEC_ID, new BankActionsSubject(bank));
    }

    /** Members button: open the engine bank-members grid for this bank, exactly as the old members button did. */
    private void openMembers(MenuActionContext ctx) {
        SharedBank bank = ctx.subject(BankActionsSubject.class).bank();
        navigation.get().bankMembersMenu().open(ctx.player(), bank);
    }

    /** Logs button: open the engine transaction-history list scoped to this bank, as the old logs button did. */
    private void openLogs(MenuActionContext ctx) {
        SharedBank bank = ctx.subject(BankActionsSubject.class).bank();
        historyView.openForBank(ctx.viewer(), bank.id(), bank.name());
    }

    /** Back button: reopen the engine bank list, exactly as the old back button did. */
    private void openBankList(MenuActionContext ctx) {
        navigation.get().bankListMenu().open(ctx.player());
    }

    /**
     * Capture an amount through the input seam, then deposit (or withdraw) it, exactly as the old view did. A
     * malformed amount sends the existing parse-error rejection and reopens the bank list, no op runs.
     */
    private void promptTransfer(MenuActionContext ctx, boolean deposit) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        SharedBank bank = ctx.subject(BankActionsSubject.class).bank();
        EconomyMessageKey promptKey = deposit
                ? EconomyMessageKey.BANK_ACTIONS_DEPOSIT_PROMPT
                : EconomyMessageKey.BANK_ACTIONS_WITHDRAW_PROMPT;
        String inputKey = deposit ? "bank.deposit" : "bank.withdraw";
        textInput.prompt(
                player,
                viewer,
                InputRequest.of(inputKey, promptKey, Map.of("bank", bank.name())),
                input -> applyTransfer(player, viewer, bank, input, deposit),
                () -> navigation.get().bankListMenu().open(player));
    }

    /**
     * Parse the typed amount against the bank's currency and, when valid, run the deposit/withdraw off the tick
     * thread, then reopen the bank list. A malformed amount sends the parse rejection and reopens, no op runs.
     * Package-private so the amount branch is unit-tested without driving a live anvil, mirroring the old view.
     */
    void applyTransfer(Player player, PlayerRef viewer, SharedBank bank, String input, boolean deposit) {
        Result<Money, AmountParseError> parsed =
                AmountParser.parse(input, bank.balance().currency());
        if (parsed.isErr()) {
            player.sendMessage(text(viewer, EconomyMessageKey.BANK_ACTIONS_INVALID_AMOUNT, Map.of()));
            navigation.get().bankListMenu().open(player);
            return;
        }
        Money money = parsed.orElseThrow();
        scheduler.async(() -> {
            Result<Unit, BankError> res = deposit
                    ? bankService.deposit(viewer, bank.id(), money)
                    : bankService.withdraw(viewer, bank.id(), money);
            EconomyMessageKey okKey = deposit
                    ? EconomyMessageKey.BANK_ACTIONS_DEPOSIT_SUCCESS
                    : EconomyMessageKey.BANK_ACTIONS_WITHDRAW_SUCCESS;
            EconomyMessageKey errKey = deposit
                    ? EconomyMessageKey.BANK_ACTIONS_DEPOSIT_FAILED
                    : EconomyMessageKey.BANK_ACTIONS_WITHDRAW_FAILED;
            scheduler.onEntity(viewer, () -> {
                player.sendMessage(text(viewer, res.isOk() ? okKey : errKey, Map.of("bank", bank.id())));
                navigation.get().bankListMenu().open(player);
            });
        });
    }

    private Component text(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders)).decoration(TextDecoration.ITALIC, false);
    }

    private BankActionsSubject subject(MenuContext ctx) {
        return ctx.subject(BankActionsSubject.class);
    }

    /**
     * The subject of an open actions panel: the bank the deposit / withdraw / members / logs / back buttons act on.
     * The title placeholder reads {@link #bank()} directly, so the render touches no port.
     *
     * @param bank the shared bank this panel acts on
     */
    public record BankActionsSubject(SharedBank bank) {

        public BankActionsSubject {
            Objects.requireNonNull(bank, "bank");
        }
    }
}
