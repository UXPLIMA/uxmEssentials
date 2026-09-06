package com.uxplima.uxmessentials.trade.adapter.outbound;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.trade.application.TradeReceipt;
import com.uxplima.uxmessentials.trade.application.port.TradeAudit;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link TradeAudit} implementation that writes the trade audit trail to the dedicated
 * {@code com.uxplima.uxmessentials.audit} channel as one structured {@code event=trade_completed key=value} line per
 * completed swap (docs/09-deployment.md §Audit logging), mirroring the moderation, economy, and vaults audits. Both
 * participants are logged by UUID for stable identity, with names carried for the human-readable render, alongside each
 * side's total item quantity, staked money, and staked experience.
 *
 * <p>Audit lines are operator-facing, so they go through a {@link Logger} bound to the audit channel, never the
 * player-facing {@code MessageKey} catalog. The caller only invokes this when the module's {@code audit} knob is on.
 */
@NullMarked
public final class LoggingTradeAudit implements TradeAudit {

    private final Logger audit;

    public LoggingTradeAudit(Logger audit) {
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    @Override
    public void completed(TradeReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        audit.info(
                "event=trade_completed initiator={} initiator_name={} partner={} partner_name={} "
                        + "initiator_items={} partner_items={} initiator_money={} partner_money={} "
                        + "initiator_exp={} partner_exp={}",
                receipt.initiator().uuid(),
                receipt.initiator().name(),
                receipt.partner().uuid(),
                receipt.partner().name(),
                receipt.initiatorItems(),
                receipt.partnerItems(),
                money(receipt.initiatorMoney()),
                money(receipt.partnerMoney()),
                receipt.initiatorExperience(),
                receipt.partnerExperience());
    }

    /** Render a side's staked money as a compact {@code currency:amount} list, or {@code none} when nothing was staked. */
    private static String money(Map<String, BigDecimal> staked) {
        if (staked.isEmpty()) {
            return "none";
        }
        return staked.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue().toPlainString())
                .collect(Collectors.joining(","));
    }
}
