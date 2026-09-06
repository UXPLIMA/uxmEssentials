package com.uxplima.uxmessentials.moderation.application;

import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.CommandSpyStore;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /commandspy} (alias {@code /cspy}): flip whether a staff member watches the commands other players
 * run. While on, the command-watch listener fans out each player's command to this observer unless the
 * observer ran it themselves. The new state is reported to the staff member. The flag lives in the
 * {@link CommandSpyStore} (per-holder session state); this use case just flips it and renders the outcome
 * the moderation sibling of the messaging {@code SocialSpy} toggle.
 */
public final class CommandSpy {

    private final CommandSpyStore store;
    private final Notifier notifier;

    public CommandSpy(CommandSpyStore store, Notifier notifier) {
        this.store = Objects.requireNonNull(store, "store");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Flip {@code staff}'s commandspy state and tell them the new state. */
    public boolean toggle(PlayerRef staff) {
        Objects.requireNonNull(staff, "staff");
        boolean spyingNow = store.toggle(staff);
        notifier.send(staff, spyingNow ? ModerationMessageKey.COMMANDSPY_ON : ModerationMessageKey.COMMANDSPY_OFF);
        return spyingNow;
    }
}
