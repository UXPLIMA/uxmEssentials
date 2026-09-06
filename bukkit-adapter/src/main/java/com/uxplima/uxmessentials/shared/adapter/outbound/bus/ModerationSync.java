package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.BanChanged;
import com.uxplima.uxmessentials.shared.network.MuteChanged;
import org.jspecify.annotations.NullMarked;

/**
 * The moderation context's cross-server seam. The same two halves as {@link VaultSync}, but with no
 * repository decorator and no cache: a ban/mute is already enforced durably across the cluster by the shared
 * DB (every backend re-reads it on the next login), so this carries only the <em>live</em> effect.
 *
 * <ul>
 *   <li><b>Outbound</b>: {@link #publisher(BusPublisher)} binds the bus-agnostic {@link SanctionSync} port to
 *       the bus, so a successful {@code /ban}/{@code /tempban} publishes a {@link BanChanged} and a successful
 *       {@code /mute} publishes a {@link MuteChanged}, each stamped with this backend's origin.
 *   <li><b>Inbound</b>: {@link #listener(Consumer)} returns a {@link RemoteSyncListener} that, on a remote
 *       {@code BanChanged}, hands the target UUID to the wired re-enforcement action (the
 *       {@code EnforceRemoteBan} use case) which re-evaluates that target's ban here and kicks them if it is in
 *       effect and they are online. A {@code MuteChanged} is a no-op: the messaging mute gate re-reads the
 *       shared DB on the next chat attempt, so there is no in-memory mute state to refresh.
 * </ul>
 *
 * <p>Self-origin frames never reach the listener. The {@link BusCore} drops any frame whose origin equals
 * this backend's id before dispatch, so the player the local {@code /ban} already kicked is never kicked
 * twice. With the bus disabled the publisher is {@link SanctionSync#NONE} and the listener is never invoked, so
 * the single-server path is unchanged.
 */
@NullMarked
public final class ModerationSync {

    private ModerationSync() {}

    /** A {@link SanctionSync} that publishes a ban/mute frame on the bus after a successful local sanction. */
    public static SanctionSync publisher(BusPublisher bus) {
        Objects.requireNonNull(bus, "bus");
        return new Publishing(bus);
    }

    /**
     * A listener that hands a remote {@link BanChanged}'s target to {@code onRemoteBan} (the re-enforcement
     * action that kicks an online, still-banned target); a {@link MuteChanged} is a no-op. The action is a
     * {@code Consumer<UUID>} so the wiring passes {@code enforce::onRemoteBan} and this seam stays trivially
     * testable without the use case's port graph.
     */
    public static RemoteSyncListener listener(Consumer<UUID> onRemoteBan) {
        Objects.requireNonNull(onRemoteBan, "onRemoteBan");
        return message -> {
            if (message instanceof BanChanged changed) {
                onRemoteBan.accept(changed.target());
            }
            // MuteChanged: no in-memory mute cache to refresh: the mute gate re-reads the shared DB on next chat.
        };
    }

    /** Stamps each sanction with this backend's origin and routes the matching frame to peers. */
    private record Publishing(BusPublisher bus) implements SanctionSync {

        @Override
        public void banChanged(PlayerRef target) {
            bus.publish(new BanChanged(bus.serverId(), target.uuid()));
        }

        @Override
        public void muteChanged(PlayerRef target) {
            bus.publish(new MuteChanged(bus.serverId(), target.uuid()));
        }
    }
}
