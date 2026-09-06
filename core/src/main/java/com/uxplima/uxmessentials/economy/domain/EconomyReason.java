package com.uxplima.uxmessentials.economy.domain;

/**
 * Why a wallet change happened. A closed enum carried into the audit log so a ledger review can tell a
 * {@code /pay} from a kit refund from an admin correction, and so audit queries can {@code GROUP BY reason}
 * rather than parse free text (the economy GLOSSARY, {@code docs/11-economy-integration.md} §9.4). The
 * reason is set by the caller at the use-case boundary; a change with no explicit reason defaults to
 * {@link #UNSPECIFIED}.
 */
public enum EconomyReason {

    /** A player-initiated {@code /pay} transfer. */
    PAY,

    /** A per-warp {@code WarpCost} charge. */
    WARP_COST,

    /** A kit purchase debit. */
    KIT_PURCHASE,

    /** A kit refund credit. */
    KIT_REFUND,

    /** A quest/giveaway reward credit. */
    QUEST_REWARD,

    /** An admin {@code /eco set} to an exact balance. */
    ADMIN_SET,

    /** An admin {@code /eco give} credit. */
    ADMIN_GIVE,

    /** An admin {@code /eco take} debit. */
    ADMIN_TAKE,

    /** An admin {@code /eco reset} to the currency's starting balance. */
    ADMIN_RESET,

    /** A bulk {@code /eco giveall} credit. */
    ADMIN_GIVEALL,

    /** A bulk {@code /eco giverandom} credit. */
    ADMIN_GIVERANDOM,

    /** A bulk {@code /eco resetall} reset. */
    ADMIN_RESETALL,

    /** The starting balance credited to a freshly opened wallet on first join. */
    STARTING_BALANCE,

    /** A shared bank account deposit transaction. */
    BANK_DEPOSIT,

    /** A shared bank account withdrawal transaction. */
    BANK_WITHDRAW,

    /** The principal credited to a debtor's wallet when a loan is disbursed. */
    LOAN_DISBURSE,

    /** An installment debited from a debtor's wallet when a loan is repaid. */
    LOAN_REPAY,

    /** No reason was declared at the call site: logs a warning in dev builds. */
    UNSPECIFIED
}
