package com.uxplima.uxmessentials.economy.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The economy context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code WALLET_BALANCE} ↔ {@code wallet.balance}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in
 * the context. Every message resolves through one of these, and the keys mirror the economy GLOSSARY
 * terms ({@code wallet.*}, {@code pay.*}, {@code baltop.*}, {@code currency.*}).
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum EconomyMessageKey implements MessageKey {

    // /balance
    WALLET_BALANCE("wallet.balance"),
    WALLET_BALANCE_OTHER("wallet.balance-other"),

    // /pay
    PAY_SENT("pay.sent"),
    PAY_RECEIVED("pay.received"),
    PAY_SELF("pay.self"),
    PAY_INSUFFICIENT("pay.insufficient"),
    PAY_BELOW_MINIMUM("pay.below-minimum"),
    PAY_INVALID_AMOUNT("pay.invalid-amount"),
    PAY_TARGET_DISABLED("pay.target-disabled"),
    PAY_TARGET_UNKNOWN("pay.target-unknown"),
    PAY_ERROR("pay.error"),
    PAY_CONFIRM_PROMPT("pay.confirm-prompt"),
    PAY_CONFIRM_NONE("pay.confirm-none"),
    PAY_CONFIRM_EXPIRED("pay.confirm-expired"),
    PAY_CURRENCY_DISABLED("pay.currency-disabled"),
    PAY_TAX_APPLIED("pay.tax-applied"),

    // Pay confirmation GUI
    PAY_CONFIRM_GUI_TITLE("pay.confirm-gui-title"),
    PAY_CONFIRM_GUI_CONFIRM_NAME("pay.confirm-gui-confirm-name"),
    PAY_CONFIRM_GUI_CONFIRM_LORE("pay.confirm-gui-confirm-lore"),
    PAY_CONFIRM_GUI_RECIPIENT_NAME("pay.confirm-gui-recipient-name"),
    PAY_CONFIRM_GUI_VALUE_NAME("pay.confirm-gui-value-name"),
    PAY_CONFIRM_GUI_VALUE_LORE("pay.confirm-gui-value-lore"),
    PAY_CONFIRM_GUI_CANCEL_NAME("pay.confirm-gui-cancel-name"),
    PAY_CONFIRM_GUI_CANCEL_LORE("pay.confirm-gui-cancel-lore"),

    // /paytoggle
    PAY_TOGGLE_ON("pay.toggle-on"),
    PAY_TOGGLE_OFF("pay.toggle-off"),

    // /payall
    PAYALL_SENT("wallet.payall-sent"),

    // currency selection
    CURRENCY_UNKNOWN("currency.unknown"),
    CURRENCY_UNSUPPORTED("currency.unsupported"),
    CURRENCY_NO_PERMISSION("currency.no-permission"),

    // balance clamp
    BALANCE_MAX_EXCEEDED("wallet.max-exceeded"),

    // /baltop
    BALTOP_HEADER("baltop.header"),
    BALTOP_ROW("baltop.row"),
    BALTOP_EMPTY("baltop.empty"),
    BALTOP_REFRESHED("eco.baltop.refreshed"),

    // /worth
    WORTH_RESULT("wallet.worth-result"),
    WORTH_RESULT_STACK("wallet.worth-result-stack"),
    WORTH_UNKNOWN_ITEM("wallet.worth-unknown-item"),
    WORTH_NO_ITEM_IN_HAND("wallet.worth-no-item-in-hand"),
    WORTH_NOT_SELLABLE("wallet.worth-not-sellable"),

    // /sell
    SELL_SOLD("wallet.sell-sold"),
    SELL_NOTHING_TO_SELL("wallet.sell-nothing"),
    SELL_NOT_SELLABLE("wallet.sell-not-sellable"),
    SELL_NO_ITEM_IN_HAND("wallet.sell-no-item-in-hand"),
    // /sellall
    SELLALL_SUMMARY("wallet.sellall-summary"),

    // /setworth
    SETWORTH_SET("wallet.setworth-set"),
    SETWORTH_CLEARED("wallet.setworth-cleared"),
    SETWORTH_NOT_SET("wallet.setworth-not-set"),
    SETWORTH_UNKNOWN_ITEM("wallet.setworth-unknown-item"),
    SETWORTH_NO_ITEM_IN_HAND("wallet.setworth-no-item-in-hand"),

    // eco admin
    ECO_ADMIN_GIVEN("eco.admin.given"),
    ECO_ADMIN_TAKEN("eco.admin.taken"),
    ECO_ADMIN_SET("eco.admin.set"),
    ECO_ADMIN_RESET("eco.admin.reset"),
    ECO_ADMIN_GIVEALL("eco.admin.giveall"),
    ECO_ADMIN_GIVERANDOM("eco.admin.giverandom"),
    ECO_ADMIN_RESETALL("eco.admin.resetall"),
    ECO_ADMIN_RESETALL_CONFIRM("eco.admin.resetall-confirm"),
    ECO_ADMIN_TARGET_UNKNOWN("eco.admin.target-unknown"),
    ECO_ADMIN_NO_TARGETS("eco.admin.no-targets"),
    ECO_ADMIN_BACKUP_CREATED("eco.admin.backup-created"),
    ECO_ADMIN_BACKUP_FAILED("eco.admin.backup-failed"),
    ECO_ADMIN_EXPORT_CREATED("eco.admin.export-created"),
    ECO_ADMIN_RESTORE_TARGET_UNKNOWN("eco.admin.restore-target-unknown"),
    ECO_ADMIN_RESTORE_SUCCESS("eco.admin.restore-success"),
    ECO_ADMIN_RESTORE_NOT_FOUND("eco.admin.restore-not-found"),
    ECO_ADMIN_RESTORE_FAILED("eco.admin.restore-failed"),
    ECO_ADMIN_NOTE_GIVEN("eco.admin.note-given"),
    ECO_ADMIN_NOTE_OFFLINE("eco.admin.note-offline"),

    // Command costs
    COMMAND_COST_CHARGED("eco.command-cost.charged"),
    COMMAND_COST_INSUFFICIENT("eco.command-cost.insufficient"),

    // The charge receipt every feature that takes money as a side effect emits: the sentence, then one label
    // per feature naming what the money went to, so the line reads "Paid 50 coins for a warp".
    CHARGED("eco.charged"),
    CHARGE_WARP("eco.charge.warp"),
    CHARGE_KIT("eco.charge.kit"),
    CHARGE_HOME("eco.charge.home"),
    CHARGE_RANK("eco.charge.rank"),
    CHARGE_TRADE("eco.charge.trade"),
    CHARGE_ACTION("eco.charge.action"),
    CHARGE_TELEPORT("eco.charge.teleport"),
    CHARGE_VAULT("eco.charge.vault"),
    CHARGE_PLAYERWARP("eco.charge.playerwarp"),

    // Banknotes
    BANKNOTE_WITHDRAWN("eco.banknote.withdrawn"),
    BANKNOTE_DEPOSITED("eco.banknote.deposited"),
    WITHDRAW_INVALID_AMOUNT("eco.banknote.invalid-amount"),
    WITHDRAW_INSUFFICIENT("eco.banknote.insufficient"),
    WITHDRAW_PHYSICAL_NOT_ALLOWED("eco.banknote.physical-not-allowed"),
    BANKNOTE_INVALID("eco.banknote.invalid"),
    DEPOSIT_NO_BANKNOTE("eco.banknote.deposit-no-banknote"),
    BANKNOTE_ITEM_NAME("eco.banknote.item-name"),
    BANKNOTE_ITEM_LORE_VALUE("eco.banknote.item-lore-value"),
    BANKNOTE_ITEM_LORE_SIGNATORY("eco.banknote.item-lore-signatory"),
    BANKNOTE_ITEM_LORE_HINT("eco.banknote.item-lore-hint"),

    // Baltop GUI
    BALTOP_GUI_TITLE("baltop.gui-title"),
    BALTOP_GUI_PREVIOUS("baltop.gui-previous"),
    BALTOP_GUI_NEXT("baltop.gui-next"),
    BALTOP_GUI_CLOSE("baltop.gui-close"),
    BALTOP_GUI_ENTRY_NAME("baltop.gui-entry-name"),
    BALTOP_GUI_ENTRY_LORE("baltop.gui-entry-lore"),

    // Wallet GUI
    WALLET_GUI_TITLE("wallet.gui-title"),
    WALLET_GUI_BALANCE_NAME("wallet.gui-balance-name"),
    WALLET_GUI_BALANCE_LORE("wallet.gui-balance-lore"),
    WALLET_GUI_BANKNOTES_NAME("wallet.gui-banknotes-name"),
    WALLET_GUI_BANKNOTES_LORE("wallet.gui-banknotes-lore"),
    WALLET_GUI_HISTORY_NAME("wallet.gui-history-name"),
    WALLET_GUI_HISTORY_LORE("wallet.gui-history-lore"),

    // Exchange
    EXCHANGE_SUCCESS("eco.exchange.success"),
    EXCHANGE_RATE_NOT_FOUND("eco.exchange.rate-not-found"),
    EXCHANGE_INSUFFICIENT_FUNDS("eco.exchange.insufficient-funds"),
    EXCHANGE_LIMIT_EXCEEDED("eco.exchange.limit-exceeded"),
    EXCHANGE_PROVIDER_UNSUPPORTED("eco.exchange.provider-unsupported"),
    EXCHANGE_INVALID_AMOUNT("eco.exchange.invalid-amount"),
    EXCHANGE_CURRENCY_DISABLED("eco.exchange.currency-disabled"),
    EXCHANGE_GUI_TITLE("eco.exchange.gui-title"),
    EXCHANGE_GUI_SOURCE_NAME("eco.exchange.gui-source-name"),
    EXCHANGE_GUI_SOURCE_LORE("eco.exchange.gui-source-lore"),
    EXCHANGE_GUI_TARGET_NAME("eco.exchange.gui-target-name"),
    EXCHANGE_GUI_TARGET_LORE("eco.exchange.gui-target-lore"),
    EXCHANGE_GUI_INFO_NAME("eco.exchange.gui-info-name"),
    EXCHANGE_GUI_INFO_LORE("eco.exchange.gui-info-lore"),
    EXCHANGE_GUI_NO_RATE_NAME("eco.exchange.gui-no-rate-name"),
    EXCHANGE_GUI_NO_RATE_LORE("eco.exchange.gui-no-rate-lore"),
    EXCHANGE_PROMPT("eco.exchange.prompt"),
    EXCHANGE_PROMPT_CANCEL("eco.exchange.prompt-cancel"),

    // Chat prompts shared by exchange/bank/loan flows
    PROMPT_CANCEL_TOKENS("eco.prompt.cancel-tokens"),
    PROMPT_CANCELLED("eco.prompt.cancelled"),

    // Loans
    LOAN_LIMIT_EXCEEDED("loan.limit-exceeded"),
    LOAN_NOT_FOUND("loan.not-found"),
    LOAN_WRONG_DEBTOR("loan.wrong-debtor"),
    LOAN_INSUFFICIENT_FUNDS("loan.insufficient-funds"),
    LOAN_INVALID_TERMS("loan.invalid-terms"),
    LOAN_PROVIDER_UNSUPPORTED("loan.provider-unsupported"),
    LOAN_APPROVED("loan.approved"),
    LOAN_PAID("loan.paid"),

    // Loan dashboard GUI
    LOAN_GUI_TITLE("loan.gui-title"),
    LOAN_GUI_CLOSE("loan.gui-close"),
    LOAN_GUI_REQUEST_NAME("loan.gui-request-name"),
    LOAN_GUI_REQUEST_LORE("loan.gui-request-lore"),
    LOAN_GUI_PROFILE_NAME("loan.gui-profile-name"),
    LOAN_GUI_PROFILE_SCORE("loan.gui-profile-score"),
    LOAN_GUI_PROFILE_INTEREST("loan.gui-profile-interest"),
    LOAN_GUI_PROFILE_LIMITS_HEADER("loan.gui-profile-limits-header"),
    LOAN_GUI_PROFILE_LIMIT_ROW("loan.gui-profile-limit-row"),
    LOAN_GUI_LOAN_NAME("loan.gui-loan-name"),
    LOAN_GUI_LOAN_PRINCIPAL("loan.gui-loan-principal"),
    LOAN_GUI_LOAN_REMAINING("loan.gui-loan-remaining"),
    LOAN_GUI_LOAN_INTEREST("loan.gui-loan-interest"),
    LOAN_GUI_LOAN_INSTALLMENTS_LEFT("loan.gui-loan-installments-left"),
    LOAN_GUI_LOAN_INSTALLMENT_PAYOUT("loan.gui-loan-installment-payout"),
    LOAN_GUI_LOAN_NEXT_DEBIT("loan.gui-loan-next-debit"),
    LOAN_GUI_LOAN_NEXT_DEBIT_OVERDUE("loan.gui-loan-next-debit-overdue"),
    LOAN_GUI_LOAN_NEXT_DEBIT_REMAINING("loan.gui-loan-next-debit-remaining"),
    LOAN_GUI_LOAN_DIVIDER("loan.gui-loan-divider"),
    LOAN_GUI_LOAN_HINT_INSTALLMENT("loan.gui-loan-hint-installment"),
    LOAN_GUI_LOAN_HINT_FULL("loan.gui-loan-hint-full"),
    LOAN_GUI_LOAN_HINT_CUSTOM("loan.gui-loan-hint-custom"),
    LOAN_GUI_CURRENCY_TITLE("loan.gui-currency-title"),
    LOAN_GUI_CURRENCY_NAME("loan.gui-currency-name"),
    LOAN_GUI_CURRENCY_LORE("loan.gui-currency-lore"),
    LOAN_GUI_CURRENCY_HINT("loan.gui-currency-hint"),
    LOAN_GUI_NO_CURRENCIES("loan.gui-no-currencies"),
    LOAN_GUI_AMOUNT_PROMPT("loan.gui-amount-prompt"),
    LOAN_GUI_INVALID_AMOUNT("loan.gui-invalid-amount"),
    LOAN_GUI_INSTALLMENTS_PROMPT("loan.gui-installments-prompt"),
    LOAN_GUI_INSTALLMENTS_RANGE("loan.gui-installments-range"),
    LOAN_GUI_INSTALLMENTS_INVALID("loan.gui-installments-invalid"),
    LOAN_GUI_APPROVED("loan.gui-approved"),
    LOAN_GUI_APPROVED_DETAIL("loan.gui-approved-detail"),
    LOAN_GUI_REJECTED("loan.gui-rejected"),
    LOAN_GUI_CUSTOM_PROMPT("loan.gui-custom-prompt"),
    LOAN_GUI_CUSTOM_PAID("loan.gui-custom-paid"),
    LOAN_GUI_PAYMENT_FAILED("loan.gui-payment-failed"),
    LOAN_GUI_INSTALLMENT_PAID("loan.gui-installment-paid"),
    LOAN_GUI_INSTALLMENT_FAILED("loan.gui-installment-failed"),
    LOAN_GUI_PAID_OFF("loan.gui-paid-off"),
    LOAN_GUI_PAID_OFF_FAILED("loan.gui-paid-off-failed"),

    // Shared banks
    BANK_ID_TAKEN("bank.id-taken"),
    BANK_NOT_FOUND("bank.not-found"),
    BANK_NO_PERMISSION("bank.no-permission"),
    BANK_INSUFFICIENT_FUNDS("bank.insufficient-funds"),
    BANK_INSUFFICIENT_BANK_FUNDS("bank.insufficient-bank-funds"),
    BANK_ALREADY_MEMBER("bank.already-member"),
    BANK_NOT_MEMBER("bank.not-member"),
    BANK_CANNOT_REMOVE_LEADER("bank.cannot-remove-leader"),
    BANK_PROVIDER_UNSUPPORTED("bank.provider-unsupported"),
    BANK_CREATED("bank.created"),
    BANK_DEPOSITED("bank.deposited"),
    BANK_WITHDREW("bank.withdrew"),
    BANK_MEMBER_ADDED("bank.member-added"),
    BANK_MEMBER_REMOVED("bank.member-removed"),

    // Bank list GUI
    BANK_LIST_GUI_TITLE("bank.list-gui-title"),
    BANK_LIST_GUI_PREV("bank.list-gui-prev"),
    BANK_LIST_GUI_NEXT("bank.list-gui-next"),
    BANK_LIST_GUI_CREATE("bank.list-gui-create"),
    BANK_LIST_GUI_CREATE_LORE("bank.list-gui-create-lore"),
    BANK_LIST_GUI_ICON_NAME("bank.list-gui-icon-name"),
    BANK_LIST_GUI_ICON_LORE("bank.list-gui-icon-lore"),
    BANK_CREATE_PROMPT_NAME("bank.create-prompt-name"),
    BANK_CREATE_NAME_EMPTY("bank.create-name-empty"),
    BANK_CREATE_SUCCESS("bank.create-success"),

    // Bank actions GUI
    BANK_ACTIONS_GUI_TITLE("bank.actions-gui-title"),
    BANK_ACTIONS_GUI_DEPOSIT_NAME("bank.actions-gui-deposit-name"),
    BANK_ACTIONS_GUI_DEPOSIT_LORE("bank.actions-gui-deposit-lore"),
    BANK_ACTIONS_GUI_WITHDRAW_NAME("bank.actions-gui-withdraw-name"),
    BANK_ACTIONS_GUI_WITHDRAW_LORE("bank.actions-gui-withdraw-lore"),
    BANK_ACTIONS_GUI_MEMBERS_NAME("bank.actions-gui-members-name"),
    BANK_ACTIONS_GUI_MEMBERS_LORE("bank.actions-gui-members-lore"),
    BANK_ACTIONS_GUI_LOGS_NAME("bank.actions-gui-logs-name"),
    BANK_ACTIONS_GUI_LOGS_LORE("bank.actions-gui-logs-lore"),
    BANK_ACTIONS_GUI_BACK("bank.actions-gui-back"),
    BANK_ACTIONS_DEPOSIT_PROMPT("bank.actions-deposit-prompt"),
    BANK_ACTIONS_WITHDRAW_PROMPT("bank.actions-withdraw-prompt"),
    BANK_ACTIONS_INVALID_AMOUNT("bank.actions-invalid-amount"),
    BANK_ACTIONS_DEPOSIT_SUCCESS("bank.actions-deposit-success"),
    BANK_ACTIONS_DEPOSIT_FAILED("bank.actions-deposit-failed"),
    BANK_ACTIONS_WITHDRAW_SUCCESS("bank.actions-withdraw-success"),
    BANK_ACTIONS_WITHDRAW_FAILED("bank.actions-withdraw-failed"),

    // Bank members GUI
    BANK_MEMBERS_GUI_TITLE("bank.members-gui-title"),
    BANK_MEMBERS_GUI_PREV("bank.members-gui-prev"),
    BANK_MEMBERS_GUI_NEXT("bank.members-gui-next"),
    BANK_MEMBERS_GUI_ADD("bank.members-gui-add"),
    BANK_MEMBERS_GUI_ADD_LORE("bank.members-gui-add-lore"),
    BANK_MEMBERS_GUI_BACK("bank.members-gui-back"),
    BANK_MEMBERS_GUI_MEMBER_NAME("bank.members-gui-member-name"),
    BANK_MEMBERS_GUI_MEMBER_LORE("bank.members-gui-member-lore"),
    BANK_MEMBERS_GUI_REMOVED("bank.members-gui-removed"),
    BANK_MEMBERS_GUI_REMOVE_FAILED("bank.members-gui-remove-failed"),
    BANK_MEMBERS_GUI_ADD_PROMPT("bank.members-gui-add-prompt"),
    BANK_MEMBERS_GUI_NAME_EMPTY("bank.members-gui-name-empty"),
    BANK_MEMBERS_GUI_ADDED("bank.members-gui-added"),
    BANK_MEMBERS_GUI_ADD_FAILED("bank.members-gui-add-failed"),

    // Transaction history GUI
    HISTORY_GUI_TITLE_GLOBAL("eco.history.gui-title-global"),
    HISTORY_GUI_TITLE_PLAYER("eco.history.gui-title-player"),
    HISTORY_GUI_TITLE_BANK("eco.history.gui-title-bank"),
    HISTORY_GUI_PREV("eco.history.gui-prev"),
    HISTORY_GUI_NEXT("eco.history.gui-next"),
    HISTORY_GUI_CLOSE("eco.history.gui-close"),
    HISTORY_GUI_TYPE_ADMIN("eco.history.gui-type-admin"),
    HISTORY_GUI_TYPE_CREDIT("eco.history.gui-type-credit"),
    HISTORY_GUI_TYPE_DEBIT("eco.history.gui-type-debit"),
    HISTORY_GUI_TYPE_TRANSFER("eco.history.gui-type-transfer"),
    ECO_HISTORY_GUI_LORE("eco.history.gui-lore"),

    // Salary
    SALARY_PAYOUT("eco.salary.payout"),
    DAILY_REWARD_GRANTED("eco.daily-reward.granted"),

    // Physical Economy
    PHYSICAL_INVENTORY_FULL("eco.physical.inventory-full"),
    PHYSICAL_PENDING_RECEIVED("eco.physical.pending-received"),
    PHYSICAL_PLAYER_OFFLINE("eco.physical.player-offline"),

    // Eco-admin GUI (bare /eco), hub
    ECO_ADMIN_GUI_TITLE("eco.admin-gui.title"),
    ECO_ADMIN_GUI_MANAGE_NAME("eco.admin-gui.manage-name"),
    ECO_ADMIN_GUI_MANAGE_LORE("eco.admin-gui.manage-lore"),
    ECO_ADMIN_GUI_BULK_NAME("eco.admin-gui.bulk-name"),
    ECO_ADMIN_GUI_BULK_LORE("eco.admin-gui.bulk-lore"),
    ECO_ADMIN_GUI_HISTORY_NAME("eco.admin-gui.history-name"),
    ECO_ADMIN_GUI_HISTORY_LORE("eco.admin-gui.history-lore"),
    ECO_ADMIN_GUI_CLOSE("eco.admin-gui.close"),
    ECO_ADMIN_GUI_PICK_TITLE("eco.admin-gui.pick-title"),

    // Eco-admin GUI, per-player manage screen
    ECO_ADMIN_GUI_TARGET_TITLE("eco.admin-gui.target-title"),
    ECO_ADMIN_GUI_TARGET_HEAD_NAME("eco.admin-gui.target-head-name"),
    ECO_ADMIN_GUI_TARGET_HEAD_LORE("eco.admin-gui.target-head-lore"),
    ECO_ADMIN_GUI_GIVE_NAME("eco.admin-gui.give-name"),
    ECO_ADMIN_GUI_GIVE_LORE("eco.admin-gui.give-lore"),
    ECO_ADMIN_GUI_TAKE_NAME("eco.admin-gui.take-name"),
    ECO_ADMIN_GUI_TAKE_LORE("eco.admin-gui.take-lore"),
    ECO_ADMIN_GUI_SET_NAME("eco.admin-gui.set-name"),
    ECO_ADMIN_GUI_SET_LORE("eco.admin-gui.set-lore"),
    ECO_ADMIN_GUI_RESET_NAME("eco.admin-gui.reset-name"),
    ECO_ADMIN_GUI_RESET_LORE("eco.admin-gui.reset-lore"),
    ECO_ADMIN_GUI_RESET_CONFIRM_TITLE("eco.admin-gui.reset-confirm-title"),
    ECO_ADMIN_GUI_TARGET_HISTORY_NAME("eco.admin-gui.target-history-name"),
    ECO_ADMIN_GUI_TARGET_HISTORY_LORE("eco.admin-gui.target-history-lore"),
    ECO_ADMIN_GUI_CURRENCY_NAME("eco.admin-gui.currency-name"),
    ECO_ADMIN_GUI_CURRENCY_LORE("eco.admin-gui.currency-lore"),
    ECO_ADMIN_GUI_CURRENCY_ACTIVE_LORE("eco.admin-gui.currency-active-lore"),
    ECO_ADMIN_GUI_BACK("eco.admin-gui.back"),
    ECO_ADMIN_GUI_AMOUNT_PROMPT("eco.admin-gui.amount-prompt"),

    // Eco-admin GUI, server-wide bulk screen
    ECO_ADMIN_GUI_BULK_TITLE("eco.admin-gui.bulk-title"),
    ECO_ADMIN_GUI_GIVEALL_NAME("eco.admin-gui.giveall-name"),
    ECO_ADMIN_GUI_GIVEALL_LORE("eco.admin-gui.giveall-lore"),
    ECO_ADMIN_GUI_RESETALL_NAME("eco.admin-gui.resetall-name"),
    ECO_ADMIN_GUI_RESETALL_LORE("eco.admin-gui.resetall-lore"),
    ECO_ADMIN_GUI_RESETALL_CONFIRM_TITLE("eco.admin-gui.resetall-confirm-title"),

    // Eco-admin GUI. Currency picker (paginated, shared by the manage and bulk screens)
    ECO_ADMIN_GUI_SELECT_CURRENCY_NAME("eco.admin-gui.select-currency-name"),
    ECO_ADMIN_GUI_SELECT_CURRENCY_LORE("eco.admin-gui.select-currency-lore"),
    ECO_ADMIN_GUI_CURRENCY_PICKER_TITLE("eco.admin-gui.currency-picker-title"),
    ECO_ADMIN_GUI_CURRENCY_PICKER_PREVIOUS("eco.admin-gui.currency-picker-previous"),
    ECO_ADMIN_GUI_CURRENCY_PICKER_NEXT("eco.admin-gui.currency-picker-next");

    private final String key;

    EconomyMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
