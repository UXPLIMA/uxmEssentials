package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.EconomyAudit;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link EconomyAudit} implementation that writes the economy audit trail to the operator log as
 * structured {@code event=...} lines ({@code docs/09-deployment.md} §Audit logging,
 * {@code docs/11-economy-integration.md} §8). This is the {@code com.uxplima.uxmessentials.audit} channel
 * separate from the ledger ({@code transactions} table); the two are deliberately not conflated. Each line
 * carries the actor/target, the currency id, the amount, and the closed {@link EconomyReason} so a review can
 * {@code GROUP BY reason}; bulk verbs emit exactly one line with an affected-count, never per-target spam.
 *
 * <p>Audit lines are operator-facing, so they go through the {@link Logger} port (the operator path), never
 * the player-facing {@code MessageKey} catalog.
 */
@NullMarked
public final class LoggingEconomyAudit implements EconomyAudit {

    private final Logger log;

    public LoggingEconomyAudit(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void credited(PlayerRef owner, Money amount, EconomyReason reason) {
        log.info(
                "event=economy_credit owner={} currency={} amount={} reason={}",
                owner.uuid(),
                currency(amount),
                amount.amount(),
                reason);
    }

    @Override
    public void debited(PlayerRef owner, Money amount, EconomyReason reason) {
        log.info(
                "event=economy_debit owner={} currency={} amount={} reason={}",
                owner.uuid(),
                currency(amount),
                amount.amount(),
                reason);
    }

    @Override
    public void rejected(PlayerRef owner, Money requested, EconomyReason reason) {
        log.info(
                "event=economy_rejected owner={} currency={} amount={} reason={}",
                owner.uuid(),
                currency(requested),
                requested.amount(),
                reason);
    }

    @Override
    public void transferred(PlayerRef from, PlayerRef to, Money amount, EconomyReason reason) {
        log.info(
                "event=economy_transfer from={} to={} currency={} amount={} reason={}",
                from.uuid(),
                to.uuid(),
                currency(amount),
                amount.amount(),
                reason);
    }

    @Override
    public void adminMutation(PlayerRef actor, PlayerRef target, Money amount, EconomyReason reason) {
        log.info(
                "event=economy_admin actor={} target={} currency={} amount={} reason={}",
                actor.uuid(),
                target.uuid(),
                currency(amount),
                amount.amount(),
                reason);
    }

    @Override
    public void bulkMutation(PlayerRef actor, Money amount, int affected, EconomyReason reason) {
        log.info(
                "event=economy_admin_bulk actor={} currency={} amount={} affected={} reason={}",
                actor.uuid(),
                currency(amount),
                amount.amount(),
                affected,
                reason);
    }

    @Override
    public void worthSet(PlayerRef actor, String material, Money price) {
        log.info(
                "event=economy_setworth actor={} material={} currency={} price={}",
                actor.uuid(),
                material,
                currency(price),
                price.amount());
    }

    @Override
    public void worthCleared(PlayerRef actor, String material) {
        log.info("event=economy_setworth_clear actor={} material={}", actor.uuid(), material);
    }

    private static String currency(Money amount) {
        return amount.currency().id().value();
    }
}
