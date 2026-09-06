package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.RatingRewardGranter;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.RewardSpec;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickCommandRunner;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * The Bukkit {@link RatingRewardGranter}: credits a reward's money to the subject's wallet through the player-warps
 * economy seam and dispatches its console command with {@code %player%} substituted for the subject's name. The
 * money credit reuses {@link PlayerWarpEconomy#refund} (a plain, guarded wallet credit) and the rate use case
 * that drives this granter already runs off the tick thread, so the DB write never touches the main thread. The
 * command must run as the console on the global region thread, so it hops there through the injected
 * {@link Scheduler} port (never a raw off-thread {@code dispatchCommand}).
 *
 * <p>The economy is optional: with no provider present a money reward is a logged no-op (a command-only reward
 * still fires), so the sub-group stays usable before economy is wired. The {@code WarpEconomy} soft-coupling
 * precedent. An {@link RewardSpec#isEmpty() empty} spec grants nothing. Item rewards are out of scope by design.
 */
@NullMarked
public final class BukkitRatingRewardGranter implements RatingRewardGranter {

    private static final String PLAYER_TOKEN = "%player%";

    private final Optional<PlayerWarpEconomy> economy;
    private final Scheduler scheduler;
    private final ClickCommandRunner commands;
    private final Logger log;

    public BukkitRatingRewardGranter(
            Optional<PlayerWarpEconomy> economy, Scheduler scheduler, ClickCommandRunner commands, Logger log) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void grant(PlayerRef subject, RewardSpec spec) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(spec, "spec");
        spec.money().ifPresent(amount -> creditMoney(subject, amount, spec.currencyId()));
        spec.command().ifPresent(template -> dispatchCommand(subject, template));
    }

    /** Credit {@code amount} to {@code subject}, logging a provider fault or a missing economy without throwing. */
    private void creditMoney(PlayerRef subject, BigDecimal amount, String currencyId) {
        if (economy.isEmpty()) {
            log.warn("event=pwarp_rate_reward_no_economy player={} detail=money_reward_skipped", subject.uuid());
            return;
        }
        Result<Unit, ChargeError> result = economy.orElseThrow().refund(subject, amount, currencyId);
        if (result.isErr()) {
            log.warn("event=pwarp_rate_reward_credit_failed player={} error={}", subject.uuid(), result.errorOrThrow());
        }
    }

    /** Substitute {@code %player%} and dispatch the command from the console on the global region thread. */
    private void dispatchCommand(PlayerRef subject, String template) {
        String resolved = template.replace(PLAYER_TOKEN, subject.name());
        scheduler.onGlobal(() -> commands.runAsConsole(resolved));
    }
}
