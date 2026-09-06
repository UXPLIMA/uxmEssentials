package com.uxplima.uxmessentials.economy.application;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.TaxExemption;
import com.uxplima.uxmessentials.economy.application.port.TaxSink;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Bundles the three pay-tax collaborators, the {@link TaxPolicy} rule, the {@link TaxSink} destination, and the
 * {@link TaxExemption} gate, so {@link Pay} takes one collaborator rather than three. {@link #collect} is called
 * after the gross transfer has credited the receiver: it works out the tax, routes it out of the receiver, and
 * returns what was taken (zero when the payer is exempt, the policy is off, or the cut rounds to nothing). A zero
 * return means {@code Pay} behaves exactly as an untaxed transfer.
 */
public final class PayTaxation {

    private final TaxPolicy policy;
    private final TaxSink sink;
    private final TaxExemption exemption;

    public PayTaxation(TaxPolicy policy, TaxSink sink, TaxExemption exemption) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.exemption = Objects.requireNonNull(exemption, "exemption");
    }

    /** A no-op taxation (disabled policy, no exemptions) for callers that don't tax. */
    public static PayTaxation none() {
        return new PayTaxation(TaxPolicy.disabled(), (receiver, tax) -> {}, payer -> false);
    }

    /**
     * Collect the tax on a gross transfer of {@code gross} that has already credited {@code receiver}. Routes
     * the tax out of {@code receiver} through the sink and returns the amount taken, or zero when {@code payer}
     * is exempt or no tax applies.
     */
    public Money collect(PlayerRef payer, PlayerRef receiver, Money gross) {
        Objects.requireNonNull(payer, "payer");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(gross, "gross");
        if (exemption.isExempt(payer)) {
            return Money.zero(gross.currency());
        }
        Money tax = policy.tax(gross);
        if (tax.isZero()) {
            return tax;
        }
        sink.route(receiver, tax);
        return tax;
    }
}
