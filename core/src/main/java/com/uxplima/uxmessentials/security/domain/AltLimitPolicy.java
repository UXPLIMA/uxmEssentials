package com.uxplima.uxmessentials.security.domain;

/**
 * The pure decision behind the same-IP account cap: given how many distinct accounts have connected from one IP
 * token, decide whether one more (the account currently joining, already counted) may stay. A {@code
 * maxAccountsPerIp} of {@code 0} (or below) means no cap: every join is allowed and the guard only observes.
 *
 * <p>It is a value object so the rule is unit-testable without a player or a database. The join listener feeds it
 * the account count the store reports for the joining IP and kicks the player on {@link Decision#DENY}.
 *
 * @param maxAccountsPerIp the greatest number of distinct accounts allowed on one IP token, or {@code 0}/below for
 *     no cap
 */
public record AltLimitPolicy(int maxAccountsPerIp) {

    /** Whether the cap is switched off, so the guard can short-circuit before any decision. */
    public boolean unlimited() {
        return maxAccountsPerIp <= 0;
    }

    /**
     * Decide whether an IP now carrying {@code accountsOnIp} distinct accounts (the joining account included) is
     * within the cap. Unlimited policies always allow; otherwise the join is denied once the count exceeds the cap.
     */
    public Decision evaluate(int accountsOnIp) {
        if (unlimited()) {
            return Decision.ALLOW;
        }
        return accountsOnIp > maxAccountsPerIp ? Decision.DENY : Decision.ALLOW;
    }

    /** The verdict {@link AltLimitPolicy#evaluate} returns: admit the join, or refuse it as one alt too many. */
    public enum Decision {

        /** The IP is within the cap (or the cap is off), let the player stay. */
        ALLOW,

        /** The IP already carries the maximum distinct accounts, refuse this one. */
        DENY
    }
}
