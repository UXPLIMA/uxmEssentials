package com.uxplima.uxmessentials.messaging.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.messaging.domain.MessagingError;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /ignore <player>}: add a player to the owner's persistent ignore list under
 * {@link IgnoreScope#ALL}, the verb form of the ignore behaviour. Ignoring yourself is rejected
 * ({@link MessagingError#IGNORE_SELF}); a repeat ignore of the same player is idempotent at the store, so
 * it simply re-confirms. After this, ignore-aware {@code /msg} and {@code /mail send} from the ignored
 * player are silently declined.
 */
public final class Ignore {

    private final IgnoreStore ignores;
    private final Notifier notifier;

    public Ignore(IgnoreStore ignores, Notifier notifier) {
        this.ignores = Objects.requireNonNull(ignores, "ignores");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** {@code owner} ignores {@code target} across every channel. */
    public Result<Unit, MessagingError> ignore(PlayerRef owner, PlayerRef target) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(target, "target");
        if (owner.equals(target)) {
            notifier.send(owner, MessagingError.IGNORE_SELF.messageKey());
            return Result.err(MessagingError.IGNORE_SELF);
        }
        ignores.ignore(owner, target, IgnoreScope.ALL);
        notifier.send(owner, MessagingMessageKey.IGNORE_ADDED, Map.of("player", target.name()));
        return Result.ok();
    }
}
