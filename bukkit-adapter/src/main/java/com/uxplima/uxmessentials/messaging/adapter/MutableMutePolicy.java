package com.uxplima.uxmessentials.messaging.adapter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link MutePolicy} that forwards to a delegate which can be rebound at runtime. Messaging is wired before
 * the moderation context lands (registry order), so its {@code SendMessage} / {@code SendMail} / {@code
 * HelpOp} use cases are built against this policy while it still delegates to {@link MutePolicy#NEVER}. When
 * moderation wires, it calls {@link #bind} to supply the real mute policy, and every already-constructed
 * messaging use case begins honouring it: no re-wiring.
 *
 * <p>If moderation is disabled the delegate stays {@link MutePolicy#NEVER}, so messaging degrades to "no one
 * is muted" exactly as the soft-couple contract requires. The reference is atomic so the rebind on the enable
 * thread is safely visible to the region threads that read the policy.
 */
@NullMarked
public final class MutableMutePolicy implements MutePolicy {

    private final AtomicReference<MutePolicy> delegate = new AtomicReference<>(MutePolicy.NEVER);

    @Override
    public boolean isMuted(PlayerRef who) {
        return Objects.requireNonNull(delegate.get(), "delegate").isMuted(who);
    }

    /** Rebind to the real moderation-provided policy; called once when the moderation context wires. */
    public void bind(MutePolicy real) {
        delegate.set(Objects.requireNonNull(real, "real"));
    }
}
