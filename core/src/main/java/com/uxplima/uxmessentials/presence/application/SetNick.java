package com.uxplima.uxmessentials.presence.application;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.presence.application.port.NickStore;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /nick <name>} and {@code /nick <player> <name>}: set a player's display name. The nick is validated
 * at entry (non-blank, within the length cap, and limited to the safe MiniMessage-free character set) before
 * any stamp, so a malformed nick never reaches the {@link NickStore}. Setting your own nick confirms with
 * {@link PresenceMessageKey#NICK_SET}; setting another player's (gated by the adapter's others-node) confirms
 * with {@link PresenceMessageKey#NICK_SET_OTHER} so the actor sees whose name changed.
 */
public final class SetNick {

    private static final int MAX_LENGTH = 16;
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_]+");

    private final NickStore store;
    private final Notifier notifier;

    public SetNick(NickStore store, Notifier notifier) {
        this.store = Objects.requireNonNull(store, "store");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set {@code target}'s nick to {@code nick} at {@code actor}'s request, validating before stamping. */
    public void set(PlayerRef actor, PlayerRef target, String nick) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(nick, "nick");
        String trimmed = nick.strip();
        if (!isValid(trimmed)) {
            notifier.send(actor, PresenceMessageKey.NICK_INVALID);
            return;
        }
        store.setNick(target, trimmed);
        confirm(actor, target, trimmed);
    }

    private void confirm(PlayerRef actor, PlayerRef target, String nick) {
        if (actor.equals(target)) {
            notifier.send(actor, PresenceMessageKey.NICK_SET, Map.of("nick", nick));
            return;
        }
        notifier.send(actor, PresenceMessageKey.NICK_SET_OTHER, Map.of("player", target.name(), "nick", nick));
    }

    private static boolean isValid(String nick) {
        return !nick.isEmpty()
                && nick.length() <= MAX_LENGTH
                && ALLOWED.matcher(nick).matches();
    }
}
