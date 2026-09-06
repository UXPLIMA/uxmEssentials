package com.uxplima.uxmessentials.messaging.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /socialspy}: flip whether a staff member observes other players' private messages, in one of two
 * forms. The no-argument {@code /socialspy} toggles the global ALL flag, observe every private message,
 * while {@code /socialspy <player>} toggles one player on the staff member's target set, so they observe
 * only conversations that player is a party to. The {@link SendMessage} fan-out delivers each message to
 * every eligible observer (ALL holders plus matching target-watchers), skipping a party to the conversation.
 * The new state is reported to the staff member. The flags live in the {@link SocialSpyStore} (per-holder
 * session/PDC state); this use case flips them and renders the outcome.
 */
public final class SocialSpy {

    private final SocialSpyStore socialSpy;
    private final Notifier notifier;

    public SocialSpy(SocialSpyStore socialSpy, Notifier notifier) {
        this.socialSpy = Objects.requireNonNull(socialSpy, "socialSpy");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Flip {@code staff}'s global socialspy (ALL) state and tell them the new state. */
    public boolean toggle(PlayerRef staff) {
        Objects.requireNonNull(staff, "staff");
        boolean spyingNow = socialSpy.toggle(staff);
        notifier.send(staff, spyingNow ? MessagingMessageKey.SOCIALSPY_ON : MessagingMessageKey.SOCIALSPY_OFF);
        return spyingNow;
    }

    /** Toggle {@code target} on {@code staff}'s socialspy target set and tell them the new state. */
    public boolean watch(PlayerRef staff, PlayerRef target) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(target, "target");
        boolean watchingNow = socialSpy.toggleTarget(staff, target);
        MessagingMessageKey key =
                watchingNow ? MessagingMessageKey.SOCIALSPY_WATCHING : MessagingMessageKey.SOCIALSPY_UNWATCHED;
        notifier.send(staff, key, Map.of("player", target.name()));
        return watchingNow;
    }
}
