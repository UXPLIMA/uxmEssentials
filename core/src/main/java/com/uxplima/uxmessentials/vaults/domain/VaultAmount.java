package com.uxplima.uxmessentials.vaults.domain;

/**
 * A resolved vault-count quota: either a concrete cap or "unlimited". The application layer resolves the raw
 * number through the shared {@code Permissions} quota reducer (the highest matching
 * {@code uxmessentials.vault.amount.<n>} node, falling back to the config default) and hands the aggregate this
 * value object, so the {@code -1} unlimited sentinel never leaks into the index comparison as a magic number.
 *
 * <p>The cap is checked against the one-based index the player requests ({@code /vault <n>}): a request for
 * vault {@code n} is allowed only when {@code n} is within the cap. A re-open of an already-owned vault is
 * always allowed (it consumes no new quota): that distinction lives in {@link Vault} rather than here.
 *
 * @param cap the maximum number of vaults the owner may have, or any value when {@link #unlimited}
 * @param unlimited true when the owner may own any number of vaults
 */
public record VaultAmount(int cap, boolean unlimited) {

    /** A concrete cap; a negative value is rejected (the unlimited case has its own factory). */
    public static VaultAmount of(int cap) {
        if (cap < 0) {
            throw new IllegalArgumentException("vault amount must not be negative: " + cap);
        }
        return new VaultAmount(cap, false);
    }

    /** The "no limit at all" quota: an owner with the {@code -1} sentinel or unlimited meta. */
    public static VaultAmount noLimit() {
        return new VaultAmount(Integer.MAX_VALUE, true);
    }

    /** True when the owner is permitted to own a vault at one-based {@code index}. */
    public boolean allows(int index) {
        return unlimited || index <= cap;
    }
}
