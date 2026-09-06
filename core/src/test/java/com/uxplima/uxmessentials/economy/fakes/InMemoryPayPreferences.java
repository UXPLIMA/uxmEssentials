package com.uxplima.uxmessentials.economy.fakes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.economy.application.port.PayPreferences;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A hand-rolled {@link PayPreferences} for the pay tests. A per-player accept-pay flag defaulting to
 * accepting, flipped by {@code /paytoggle}.
 */
public final class InMemoryPayPreferences implements PayPreferences {

    private final Map<PlayerRef, Boolean> accepting = new ConcurrentHashMap<>();

    @Override
    public boolean acceptsPay(PlayerRef target) {
        return accepting.getOrDefault(target, Boolean.TRUE);
    }

    @Override
    public boolean toggle(PlayerRef who) {
        return accepting.merge(who, Boolean.FALSE, (current, ignored) -> !current);
    }

    /** Test seam: force {@code who}'s flag to {@code value} without going through {@link #toggle}. */
    public void set(PlayerRef who, boolean value) {
        accepting.put(who, value);
    }
}
