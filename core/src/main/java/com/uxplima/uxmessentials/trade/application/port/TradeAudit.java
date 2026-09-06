package com.uxplima.uxmessentials.trade.application.port;

import com.uxplima.uxmessentials.trade.application.TradeReceipt;

/**
 * Outbound port for the trade audit trail. One {@code event=trade_completed} line on the shared
 * {@code com.uxplima.uxmessentials.audit} channel per completed swap, mirroring the moderation, economy, and vaults
 * audits (docs/09-deployment.md §Audit logging). The line records who traded whom and what each side gave, so a
 * completed trade is always replayable from the audit channel and the optional Discord bridge can surface it.
 *
 * <p>The audit is config-gated: the caller emits only when the module's {@code audit} knob is on, so a
 * {@link TradeReceipt} reaches the port only for a trade that actually completed. The implementation lands with the
 * bukkit-adapter; the application depends only on this contract.
 */
public interface TradeAudit {

    /** {@code event=trade_completed}: both sides confirmed and the swap settled. */
    void completed(TradeReceipt receipt);
}
