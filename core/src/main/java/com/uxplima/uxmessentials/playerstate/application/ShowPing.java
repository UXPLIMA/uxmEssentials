package com.uxplima.uxmessentials.playerstate.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerInfo;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /ping [player]}: show a player's round-trip latency in milliseconds. A read-only query through the
 * {@link PlayerInfo} port: nothing is mutated. The viewer sees their own ping, or another player's with the
 * {@code .others} node; an offline target is a silent no-op the adapter has already rejected before this runs.
 */
public final class ShowPing {

    private final PlayerInfo info;
    private final Notifier notifier;

    public ShowPing(PlayerInfo info, Notifier notifier) {
        this.info = Objects.requireNonNull(info, "info");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Show {@code who} their own ping. */
    public void show(PlayerRef who) {
        showFor(who, who);
    }

    /** Show {@code actor} the ping of {@code subject}. */
    public void showFor(PlayerRef actor, PlayerRef subject) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        Optional<Integer> ping = info.pingOf(subject);
        if (ping.isEmpty()) {
            return;
        }
        Map<String, String> data = Map.of("ping", Integer.toString(ping.get()), "player", subject.name());
        notifier.send(
                actor,
                actor.equals(subject) ? PlayerstateMessageKey.PING_SHOW : PlayerstateMessageKey.PING_SHOW_OTHER,
                data);
    }
}
