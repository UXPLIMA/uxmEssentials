package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.playerstate.application.port.AfkStatus;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * An {@link AfkStatus} that forwards to a delegate which can be rebound at runtime. The playerstate context (and
 * its playtime sampler) is wired before the presence context lands in registry order, so the sampler is built
 * against this holder while it still delegates to {@link AfkStatus#NEVER}. When presence wires, the bootstrap
 * calls {@link #bind} with a {@code PresenceAfkStatus} over the live presence store, and the already-running
 * sampler begins classifying AFK seconds correctly: no re-wiring. Same rebindable-holder shape messaging's
 * {@code MutableAfkStatus} uses for its AFK courtesy notice.
 *
 * <p>If presence is disabled the delegate stays {@link AfkStatus#NEVER}, so every sample counts as active time
 * the honest answer when there is no AFK source. The reference is atomic so the rebind on the enable thread is
 * safely visible to the sampler's async threads.
 */
@NullMarked
public final class MutablePlaytimeAfkStatus implements AfkStatus {

    private final AtomicReference<AfkStatus> delegate = new AtomicReference<>(AfkStatus.NEVER);

    @Override
    public boolean isAfk(PlayerRef who) {
        return Objects.requireNonNull(delegate.get(), "delegate").isAfk(who);
    }

    /** Rebind to the real presence-provided status; called once when the presence context wires. */
    public void bind(AfkStatus real) {
        delegate.set(Objects.requireNonNull(real, "real"));
    }
}
