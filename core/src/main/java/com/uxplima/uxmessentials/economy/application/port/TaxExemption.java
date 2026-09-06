package com.uxplima.uxmessentials.economy.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port deciding whether a payer is exempt from the {@code /pay} tax. Staff, shops, or anyone holding
 * the configured {@code pay.tax.bypass-permission}. Permission-driven like {@link BaltopExemption}, so it
 * survives a UUID change and needs no per-account flag; the check runs once per taxed transfer.
 */
public interface TaxExemption {

    /** True when {@code payer}'s transfers are not taxed. */
    boolean isExempt(PlayerRef payer);
}
