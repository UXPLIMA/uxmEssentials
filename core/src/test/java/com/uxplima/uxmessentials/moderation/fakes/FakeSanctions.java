package com.uxplima.uxmessentials.moderation.fakes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A {@link Sanctions} fake that records the live-player actions a use case drives, kicks, jail/release
 * teleports, and tracks the frozen set, so a test can assert that an online {@code /tempban} kicks, a
 * {@code /jail} teleports into the jail, and a {@code /freeze} marks the target frozen, all without a server.
 */
public final class FakeSanctions implements Sanctions {

    public final List<PlayerRef> kicked = new ArrayList<>();
    public final List<String> jailedInto = new ArrayList<>();
    public final List<PlayerRef> released = new ArrayList<>();
    private final Set<UUID> frozen = new HashSet<>();
    private final List<PlayerRef> online;

    public FakeSanctions(PlayerRef... online) {
        this.online = List.of(online);
    }

    @Override
    public void kick(PlayerRef target, MessageKey reasonKey, String reasonText) {
        kicked.add(target);
    }

    @Override
    public Collection<PlayerRef> onlinePlayers() {
        return online;
    }

    @Override
    public void freeze(PlayerRef target) {
        frozen.add(target.uuid());
    }

    @Override
    public void unfreeze(PlayerRef target) {
        frozen.remove(target.uuid());
    }

    @Override
    public boolean isFrozen(PlayerRef target) {
        return frozen.contains(target.uuid());
    }

    @Override
    public void sendToJail(PlayerRef target, String jail) {
        jailedInto.add(jail);
    }

    @Override
    public void releaseFromJail(PlayerRef target) {
        released.add(target);
    }
}
