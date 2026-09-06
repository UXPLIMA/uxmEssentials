package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCapability;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /pwarp withdraw <name>}: pay the warp's accrued earnings out to its owner's balance. Allowed to the owner
 * and a co-owner by the capability matrix ({@link WarpCapability#WITHDRAW}). A trusted delegate may release the
 * takings but never redirect them. The verb resolves the warp ({@link PlayerWarpError#NOT_FOUND}) and gates the
 * actor ({@link PlayerWarpError#NO_PERMISSION}) before touching any money.
 *
 * <p><strong>The payout always goes to {@code warp.owner()}</strong>, whoever triggered it: the bank is the owner's
 * takings, so a co-owner releasing it credits the owner, not themselves. The economy seam is soft-coupled, injected
 * as an {@link Optional}. With no provider present there is no money system, so a withdraw is a graceful no-op notice
 * ({@link PlayerwarpsMessageKey#PWARP_NOTHING_TO_WITHDRAW}) rather than a failure. With a provider present the payout
 * result is mapped: a successful payout (an empty bank returns success per the economy contract) notifies
 * {@link PlayerwarpsMessageKey#PWARP_WITHDRAWN}, and a provider fault surfaces as
 * {@link PlayerWarpError#WITHDRAW_FAILED}.
 */
public final class WithdrawEarnings {

    private final PlayerWarpRepository repository;
    private final WarpAuthorization authorization;
    private final Notifier notifier;
    private final Optional<PlayerWarpEconomy> economy;

    public WithdrawEarnings(
            PlayerWarpRepository repository,
            WarpAuthorization authorization,
            Notifier notifier,
            Optional<PlayerWarpEconomy> economy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    /** Withdraw warp {@code name}'s earnings to its owner, or refuse when the actor may not or no economy is present. */
    public Result<Unit, PlayerWarpError> withdraw(PlayerRef actor, PlayerWarpName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<PlayerWarp> found = repository.findByName(name);
        if (found.isEmpty()) {
            notifier.send(actor, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        PlayerWarp warp = found.get();
        if (!authorization.allows(warp, actor.uuid(), WarpCapability.WITHDRAW)) {
            notifier.send(actor, PlayerWarpError.NO_PERMISSION.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NO_PERMISSION);
        }
        if (economy.isEmpty()) {
            notifier.send(actor, PlayerwarpsMessageKey.PWARP_NOTHING_TO_WITHDRAW, Map.of("warp", name.value()));
            return Result.ok();
        }
        // The bank is the owner's takings: the payout target is always the warp's owner, never the acting co-owner.
        Result<Unit, ChargeError> payout = economy.get().withdraw(warp.id().orElseThrow(), warp.owner());
        if (payout.isErr()) {
            notifier.send(actor, PlayerWarpError.WITHDRAW_FAILED.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.WITHDRAW_FAILED);
        }
        notifier.send(actor, PlayerwarpsMessageKey.PWARP_WITHDRAWN, Map.of("warp", name.value()));
        return Result.ok();
    }
}
