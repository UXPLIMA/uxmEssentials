package com.uxplima.uxmessentials.playerwarps.application.port;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * The narrow economy seam the player-warps context owns so a per-warp entry price can be charged <em>without</em>
 * a hard dependency on the economy context. It mirrors the sibling {@code warps} {@code WarpEconomy}, expressed
 * purely in this context's own terms ({@link BigDecimal} + a {@code currencyId} string, never an economy type)
 * but is richer because a player warp has an owner who banks the takings: a use of a priced warp both debits the
 * visitor and accrues to the owner's bank in one step.
 *
 * <p>Soft coupling: the port is injected into {@code UsePlayerWarp} as an {@link java.util.Optional}. With no
 * provider present a priced warp's cost is simply ignored at use time (the warp still teleports, for free) so
 * the context stays usable before economy is wired (the {@code WarpEconomy} precedent). With a provider present
 * the charge is the gate's guarded last step: it runs only after every other gate has passed, and the visit and
 * teleport happen only once it succeeds.
 *
 * <p>The configured owner <em>cut</em> (the share the server keeps rather than passing to the owner) is the
 * implementation's concern (P4-T4 reads it from config), not a gate parameter: the gate passes only the
 * {@code price} and its {@code currencyId}, keeping this seam as narrow as {@code WarpEconomy}. The
 * {@code chargeAndAccrue} contract is deliberately charge-then-settle with no check-then-charge window, so two
 * simultaneous uses can never both pass an affordability probe and then overdraw.
 */
public interface PlayerWarpEconomy {

    /**
     * Guarded debit of {@code payer} by {@code price} in {@code currencyId} and accrual of the price minus the
     * configured cut to the warp owner's bank, as one transaction. Under the native provider this is a single
     * atomic step; under a foreign provider a debit that succeeds but whose accrual then fails is unwound with a
     * compensating credit to the payer and reported as {@link ChargeError#ACCRUAL_FAILED}. There is no
     * check-then-charge: the debit either takes in full or reports {@link ChargeError#INSUFFICIENT_FUNDS}.
     */
    Result<Unit, ChargeError> chargeAndAccrue(PlayerRef payer, PlayerWarpId warp, BigDecimal price, String currencyId);

    /**
     * True when {@code who} could currently afford {@code amount} of {@code currencyId}. For UI preview only
     * the gate never calls this, because an affordability probe followed by a separate charge is exactly the
     * double-spend window {@link #chargeAndAccrue} closes.
     */
    boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId);

    /** Pay out the warp's accrued earnings to {@code to} (the owner), used by {@code /pwarp withdraw} (P4-T6). */
    Result<Unit, ChargeError> withdraw(PlayerWarpId warp, PlayerRef to);

    /**
     * Collect one {@code amount} of rent for {@code warp}, spending the warp's own bank first and the
     * {@code owner}'s wallet for the shortfall: a warp that earns pays its own rent. The bank is deducted by up to
     * {@code amount} in one guarded {@code UPDATE}; only the part the bank could not cover is then debited from the
     * owner's wallet via the DB-guarded debit. If the bank fully covers the rent there is no wallet debit at all.
     *
     * <p>There is no check-then-charge: when the wallet cannot cover the shortfall the guarded debit reports
     * {@link ChargeError#INSUFFICIENT_FUNDS}, and this call then <em>rolls back</em> the bank deduction with a
     * compensating credit so the bank is never left short without the rent actually being collected. The bank is
     * only spent when its currency matches the rent currency; a bank holding a different currency is left alone and
     * the whole rent is taken from the wallet.
     */
    Result<Unit, ChargeError> collectRent(PlayerWarpId warp, PlayerRef owner, BigDecimal amount, String currencyId);

    /** Credit {@code amount} of {@code currencyId} back to {@code to}, used by the refund paths in P7. */
    Result<Unit, ChargeError> refund(PlayerRef to, BigDecimal amount, String currencyId);

    /**
     * Guarded debit of {@code owner} by {@code amount} in {@code currencyId}, with nowhere to accrue, the sponsorship
     * fee leaves circulation (deflationary, like the rent shortfall and the entry-fee cut), so this is a plain owner
     * debit and nothing is banked. The debit is the DB-guarded, double-spend-safe point; there is no check-then-charge,
     * so it either takes in full or reports {@link ChargeError#INSUFFICIENT_FUNDS}. Used by {@code BuySponsorship}.
     */
    Result<Unit, ChargeError> chargeOwner(PlayerRef owner, BigDecimal amount, String currencyId);
}
