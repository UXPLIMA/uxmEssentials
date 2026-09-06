package com.uxplima.uxmessentials.messaging.adapter;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.messaging.application.port.AfkStatus;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * An {@link AfkStatus} that forwards to a delegate which can be rebound at runtime. Messaging is wired before
 * the presence context lands (registry order), so its {@code SendMessage} use case is built against this
 * status while it still delegates to {@link AfkStatus#NEVER}. When presence wires, the bootstrap calls
 * {@link #bind} to supply the real {@code PresenceAfkStatus}, and the already-constructed {@code SendMessage}
 * begins honouring it: no re-wiring. This is the same rebindable-holder shape {@link MutableMutePolicy} uses
 * for the moderation mute gate.
 *
 * <p>If presence is disabled the delegate stays {@link AfkStatus#NEVER}, so messaging degrades to "no one is
 * AFK" exactly as the soft-couple contract requires. The reference is atomic so the rebind on the enable
 * thread is safely visible to the region threads that read the status.
 */
@NullMarked
public final class MutableAfkStatus implements AfkStatus {

    private final AtomicReference<AfkStatus> delegate = new AtomicReference<>(AfkStatus.NEVER);

    @Override
    public Optional<String> afkReasonOf(PlayerRef who) {
        return Objects.requireNonNull(delegate.get(), "delegate").afkReasonOf(who);
    }

    /** Rebind to the real presence-provided status; called once when the presence context wires. */
    public void bind(AfkStatus real) {
        delegate.set(Objects.requireNonNull(real, "real"));
    }
}
