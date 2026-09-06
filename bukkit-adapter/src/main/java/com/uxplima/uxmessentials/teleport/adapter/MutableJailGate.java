package com.uxplima.uxmessentials.teleport.adapter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link JailGate} that forwards to a delegate which can be rebound at runtime. The teleport context is
 * wired before the moderation context lands (registry order), so its {@link TeleportEngine} and
 * {@link com.uxplima.uxmessentials.teleport.application.RequestTeleport} are built against this gate while it
 * still delegates to {@link JailGate#NEVER}. When moderation wires, it calls {@link #bind} to supply the
 * real jail policy, and every already-constructed teleport use case begins honouring it: no re-wiring.
 *
 * <p>If moderation is disabled the delegate stays {@link JailGate#NEVER}, so teleport degrades to "no one is
 * jailed" exactly as the soft-couple contract requires. The reference is atomic so the rebind on the enable
 * thread is safely visible to the region threads that read the gate.
 */
@NullMarked
public final class MutableJailGate implements JailGate {

    private final AtomicReference<JailGate> delegate = new AtomicReference<>(JailGate.NEVER);

    @Override
    public boolean isJailed(PlayerRef who) {
        return Objects.requireNonNull(delegate.get(), "delegate").isJailed(who);
    }

    /** Rebind to the real moderation-provided gate; called once when the moderation context wires. */
    public void bind(JailGate real) {
        delegate.set(Objects.requireNonNull(real, "real"));
    }
}
