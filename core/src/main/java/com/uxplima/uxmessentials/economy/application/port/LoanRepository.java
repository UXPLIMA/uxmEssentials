package com.uxplima.uxmessentials.economy.application.port;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.domain.Loan;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Outbound port for managing debtor loans and credit scores in storage.
 *
 * <p>The two money-moving methods, {@link #disburse} and {@link #applyRepayment}, are <strong>atomic</strong>:
 * each performs the wallet leg (a guarded credit/debit on the same native ledger) and the loan-row change in one
 * transaction, committing together or not at all. A wallet leg that cannot apply (insufficient funds, balance
 * cap) leaves the loan row untouched and returns the modelled {@link TransferError}, so money is never created
 * or lost by a half-applied loan move (docs/02-concurrency §6.7).
 */
public interface LoanRepository {

    /** Finds a loan by its unique ID. */
    Optional<Loan> findById(String id);

    /** Lists all active loans held by a specific player. */
    List<Loan> findByDebtor(PlayerRef debtor);

    /** Returns all active loans globally (used for auto-repayment tasks). */
    List<Loan> findAllActive();

    /** Saves or updates a loan record. */
    void save(Loan loan);

    /** Deletes a loan record (e.g. when paid off). */
    void delete(String id);

    /**
     * Atomically credit {@code loan}'s principal to the debtor's wallet and insert the loan row in one
     * transaction. The credit honours the currency's max-balance clamp; if it is rejected the loan row is not
     * written and the {@link TransferError} is returned.
     */
    Result<Unit, TransferError> disburse(Loan loan);

    /**
     * Atomically debit {@code paid} from the debtor's wallet (the guarded debit) and apply {@code updatedLoan}
     * in one transaction, updating the row when the loan still owes, or deleting it when fully paid. If the
     * guarded debit changes no rows (insufficient funds) the loan is left untouched and
     * {@link TransferError#INSUFFICIENT_FUNDS} is returned.
     */
    Result<Unit, TransferError> applyRepayment(PlayerRef debtor, Money paid, Loan updatedLoan, boolean fullyPaid);

    /** Retrieves the current credit score rating for a player. */
    Loan.CreditScore getCreditScore(PlayerRef player);

    /** Updates a player's credit rating/score record. */
    void saveCreditScore(Loan.CreditScore creditScore);
}
