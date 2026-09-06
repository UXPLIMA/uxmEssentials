package com.uxplima.uxmessentials.moderation.fakes;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A capturing {@link SanctionSync}: every {@link #banChanged}/{@link #muteChanged} call is recorded so a test
 * can assert that a successful ban or mute announced the live cross-server effect, and that a refused one did
 * not.
 */
public final class RecordingSanctionSync implements SanctionSync {

    public final List<PlayerRef> bans = new ArrayList<>();
    public final List<PlayerRef> mutes = new ArrayList<>();

    @Override
    public void banChanged(PlayerRef target) {
        bans.add(target);
    }

    @Override
    public void muteChanged(PlayerRef target) {
        mutes.add(target);
    }
}
