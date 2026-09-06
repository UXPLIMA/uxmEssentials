package com.uxplima.uxmessentials.playerstate.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerInfo;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * {@code /getpos} (aliases {@code /coords}, {@code /whereami}) {@code [player]}: show a player's world and
 * coordinates plus look direction. A read-only query through the {@link PlayerInfo} port: nothing is mutated.
 * The viewer sees their own position, or another player's with the {@code .others} node; an offline target is a
 * silent no-op the adapter has already rejected before this runs.
 */
public final class ShowPosition {

    private final PlayerInfo info;
    private final Notifier notifier;

    public ShowPosition(PlayerInfo info, Notifier notifier) {
        this.info = Objects.requireNonNull(info, "info");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Show {@code who} their own position. */
    public void show(PlayerRef who) {
        showFor(who, who);
    }

    /** Show {@code actor} the position of {@code subject}. */
    public void showFor(PlayerRef actor, PlayerRef subject) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        Optional<Position> position = info.positionOf(subject);
        if (position.isEmpty()) {
            return;
        }
        Position at = position.get();
        Map<String, String> data = Map.of(
                "world", at.world().name(),
                "x", Integer.toString(at.blockX()),
                "y", Integer.toString(at.blockY()),
                "z", Integer.toString(at.blockZ()),
                "yaw", Long.toString(Math.round(at.yaw())),
                "pitch", Long.toString(Math.round(at.pitch())),
                "player", subject.name());
        notifier.send(
                actor,
                actor.equals(subject) ? PlayerstateMessageKey.GETPOS_SHOW : PlayerstateMessageKey.GETPOS_SHOW_OTHER,
                data);
    }
}
