package com.uxplima.uxmessentials.economy.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.BankError;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Outbound port for joint shared bank accounts persistence.
 *
 * <p>The two money-moving methods, {@link #deposit} and {@link #withdraw}, are <strong>atomic</strong>: each
 * performs the player's wallet leg (a guarded debit/credit on the native ledger) and the bank-balance change in
 * one transaction, committing together or not at all. A deposit whose guarded wallet debit changes no rows, a
 * withdraw whose guarded bank-balance update changes no rows, or a move against a bank that was deleted
 * concurrently, all leave the other side untouched and return the modelled {@link BankError}. The bank's
 * sufficiency check is a guarded {@code UPDATE … WHERE balance >= ?}, not a JVM compare, so two concurrent
 * withdrawals can never both overdraw the bank.
 */
public interface BankRepository {

    /** Finds a shared bank by its unique ID identifier. */
    Optional<SharedBank> findById(String id);

    /** Saves or updates the shared bank details and balance. */
    void save(SharedBank bank);

    /** Permanently deletes a shared bank account from storage. */
    void delete(String id);

    /** Lists all shared bank IDs a player is associated with. */
    List<String> findBankIdsForPlayer(UUID uuid);

    /** Every shared bank, for periodic maintenance such as interest accrual. */
    List<SharedBank> findAll();

    /**
     * Atomically add {@code interest} to {@code bankId}'s balance with a single {@code UPDATE … SET balance =
     * balance + ?} (so a concurrent deposit/withdraw can never be clobbered). This is a system credit, interest
     * is minted into the bank, no member is debited: applied only when the bank's currency matches.
     */
    void creditBank(String bankId, Money interest);

    /**
     * Atomically debit {@code amount} from {@code player}'s wallet (the guarded debit) and add it to bank
     * {@code bankId}'s balance in one transaction. If the guarded wallet debit changes no rows the bank balance
     * is left untouched and {@link BankError#INSUFFICIENT_FUNDS} is returned; if the bank-balance add matches no
     * rows (the bank was deleted concurrently) the whole transaction rolls back with {@link BankError#NOT_FOUND}
     * so the player is never debited while the money vanishes.
     */
    Result<Unit, BankError> deposit(String bankId, PlayerRef player, Money amount);

    /**
     * Atomically subtract {@code amount} from bank {@code bankId}'s balance via a guarded
     * {@code UPDATE … WHERE balance >= ?} and credit it to {@code player}'s wallet in one transaction. When the
     * guarded bank update changes no rows the wallet is left untouched and {@link BankError#INSUFFICIENT_BANK_FUNDS}
     * is returned (whether the bank is short or gone); a wallet credit the clamp rejects rolls the whole
     * transaction back with {@link BankError#BALANCE_MAX_EXCEEDED}.
     */
    Result<Unit, BankError> withdraw(String bankId, PlayerRef player, Money amount);
}
