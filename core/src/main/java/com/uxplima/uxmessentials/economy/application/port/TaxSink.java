package com.uxplima.uxmessentials.economy.application.port;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Where a {@code /pay} tax goes. The tax is collected <em>from the receiver</em> immediately after the gross
 * transfer credited them. The receiver always holds at least the tax at that moment, so the sink's single
 * guarded move can never fail for lack of funds and money is conserved in every outcome. An implementation
 * either voids the tax (a guarded debit that destroys it) or routes it to a server account (a transfer into a
 * configured holding wallet); both are atomic single moves, so a sink failure (e.g. the account at its
 * max-balance) leaves the tax with the receiver rather than destroying it.
 */
public interface TaxSink {

    /** Take {@code tax} out of {@code receiver}'s balance, voiding it or routing it to the configured account. */
    void route(PlayerRef receiver, Money tax);
}
