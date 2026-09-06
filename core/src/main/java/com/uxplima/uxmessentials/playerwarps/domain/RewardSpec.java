package com.uxplima.uxmessentials.playerwarps.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * One configured rating reward, expressed purely in this context's own terms: an optional money amount in a named
 * currency and an optional console-command template. The command template carries a {@code %player%} placeholder
 * the granter substitutes with the rewarded player's name before it is dispatched from the console. Item rewards
 * are deliberately out of scope for this task; only money and a command are modelled.
 *
 * <p>Both arms are independently optional, and an {@link #isEmpty() empty} spec grants nothing: the shipped
 * default for a rewards sub-group that is on but leaves one side of it unconfigured. Instances are always built
 * through {@link #of} or {@link #none}, which normalise a non-positive amount or a blank command to "absent" so a
 * {@code money = 0} / {@code command = ""} config reads as no reward at all rather than a zero credit.
 *
 * @param money the amount to credit, present only when strictly positive
 * @param currencyId the currency the {@link #money} is credited in ({@code "default"} for the server default)
 * @param command the console-command template to dispatch, present only when non-blank
 */
public record RewardSpec(Optional<BigDecimal> money, String currencyId, Optional<String> command) {

    public RewardSpec {
        Objects.requireNonNull(money, "money");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(command, "command");
        money.ifPresent(amount -> {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("reward money must be strictly positive when present: " + amount);
            }
        });
    }

    /** The empty reward: no money and no command, so {@code grant} is a no-op. */
    public static RewardSpec none() {
        return new RewardSpec(Optional.empty(), "default", Optional.empty());
    }

    /**
     * Build a spec from raw config values, normalising a non-positive {@code money} or a blank {@code command} to
     * "absent" so an unconfigured side of the reward grants nothing rather than a zero credit or an empty dispatch.
     */
    public static RewardSpec of(BigDecimal money, String currencyId, String command) {
        Optional<BigDecimal> amount = money != null && money.signum() > 0 ? Optional.of(money) : Optional.empty();
        Optional<String> template =
                command != null && !command.isBlank() ? Optional.of(command.strip()) : Optional.empty();
        return new RewardSpec(amount, currencyId == null ? "default" : currencyId, template);
    }

    /** True when this spec would grant nothing, neither a money credit nor a command dispatch is configured. */
    public boolean isEmpty() {
        return money.isEmpty() && command.isEmpty();
    }
}
