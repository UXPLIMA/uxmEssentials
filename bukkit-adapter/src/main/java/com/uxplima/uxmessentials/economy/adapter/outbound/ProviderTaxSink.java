package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.TaxSink;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link TaxSink} backed by the active {@link EconomyProvider}. A configured holding account ({@code
 * pay.tax.sink = "account:<uuid>"}) routes the tax into that wallet with a transfer; the default void sink
 * ({@code pay.tax.sink = "void"}) destroys it with a guarded debit. Either is a single atomic move on the
 * receiver, who was just credited the gross, so the move never fails for lack of funds. The rare non-success (a
 * holding account already at its max-balance) leaves the tax with the receiver, money is conserved either way,
 * and is logged for the operator rather than swallowed.
 */
@NullMarked
public final class ProviderTaxSink implements TaxSink {

    private final EconomyProvider provider;
    private final Optional<PlayerRef> account;
    private final Logger log;

    public ProviderTaxSink(EconomyProvider provider, Optional<PlayerRef> account, Logger log) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.account = Objects.requireNonNull(account, "account");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void route(PlayerRef receiver, Money tax) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(tax, "tax");
        if (account.isPresent()) {
            if (!(provider.transfer(receiver, account.get(), tax) instanceof TransferResult.Allow)) {
                log.warn("Pay tax could not be routed to the holding account; it stays with {}", receiver.name());
            }
        } else if (provider.debit(receiver, tax).isErr()) {
            log.warn("Pay tax could not be voided from {}; it stays with them", receiver.name());
        }
    }
}
