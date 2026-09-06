package com.uxplima.uxmessentials.teleport.adapter;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.homes.application.HomeRespawnLocator;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link HomeRespawnLocator} that forwards to a delegate which can be rebound at runtime. The teleport
 * context is wired before the homes context lands (registry order), so its respawn listener is built against
 * this locator while it still resolves to empty. When homes wires, it calls {@link #bind} to supply the
 * cache-backed locator, and the already-registered respawn listener begins resolving the {@code HOME} step
 * no re-wiring.
 *
 * <p>If homes is disabled the delegate stays the empty locator, so a {@code HOME} step in a configured
 * respawn chain simply falls through to the next step, exactly as the soft-couple contract requires. The
 * reference is atomic so the rebind on the enable thread is safely visible to the region threads that read it
 * inside the respawn event.
 */
@NullMarked
public final class MutableHomeRespawnLocator implements HomeRespawnLocator {

    private static final HomeRespawnLocator NONE = who -> Optional.empty();

    private final AtomicReference<HomeRespawnLocator> delegate = new AtomicReference<>(NONE);

    @Override
    public Optional<Position> respawnHome(PlayerRef who) {
        return Objects.requireNonNull(delegate.get(), "delegate").respawnHome(who);
    }

    /** Rebind to the real homes-provided locator; called once when the homes context wires. */
    public void bind(HomeRespawnLocator real) {
        delegate.set(Objects.requireNonNull(real, "real"));
    }
}
