package com.uxplima.uxmessentials.vaults.domain;

/**
 * The closed set of reasons a vault operation is refused. Each maps to one {@code VaultsMessageKey} the
 * application layer renders for the player; the aggregate returns one of these in a {@code Result} rather than
 * throwing, so a refused open is an ordinary outcome, not an exception.
 */
public enum VaultError {

    /** The requested vault index exceeds the owner's resolved {@code uxmessentials.vault.amount.<n>} quota. */
    AMOUNT_EXCEEDED,

    /** The owner owns no vaults yet, so {@code /vault} with no index has nothing to list or default to. */
    NONE_OWNED,

    /** The per-action economy fee (create or open) exceeds the player's balance, so the action is refused. */
    CANNOT_AFFORD,

    /** A {@code /vault delete <n>} of an index the owner has no vault row for: there is nothing to remove. */
    DELETE_UNKNOWN,

    /**
     * A presentation change ({@code /vault rename}, {@code /vault icon}) targeting an index the owner has no
     * vault row for: there is nothing to rename or re-icon.
     */
    VAULT_UNKNOWN
}
