package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The live reaction to a ban on another backend: {@link EnforceRemoteBan} re-reads the now-authoritative
 * tempban from the shared DB and kicks the target only if the ban is actually in effect. The use case runs off
 * the tick thread (the bus dispatches inbound frames asynchronously), so it must touch no Bukkit API: there is
 * no online lookup here. The kick is attempted unconditionally through the {@link Sanctions} adapter, which
 * hops to the target's entity region thread and silently no-ops when the player is not connected. A frame for a
 * player whose ban is not (or no longer) active never kicks: the DB, not the frame, is the source of truth.
 * Self-origin frames never reach here (the bus client drops them before dispatch), so this layer only ever sees
 * genuine peer bans.
 */
class EnforceRemoteBanTest {

    private static final Instant NOW = Instant.parse("2026-06-14T00:00:00Z");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "griefer");

    /**
     * A faithful {@link Sanctions} double mirroring the entity-hopping adapter: it records every UUID it was
     * asked to kick, and (modelling the adapter's resolve-on-entity-thread no-op) reports the kick as
     * delivered only when the target is in the online set. The use case never sees this distinction: it just
     * calls {@link #kick}, which is exactly the point: no online lookup happens inside the use case.
     */
    private static final class EntityHoppingSanctions implements Sanctions {
        final List<UUID> attempted = new ArrayList<>();
        final List<PlayerRef> delivered = new ArrayList<>();
        private final Set<UUID> online;

        EntityHoppingSanctions(Set<UUID> online) {
            this.online = online;
        }

        @Override
        public void kick(PlayerRef target, MessageKey reasonKey, String reasonText) {
            attempted.add(target.uuid());
            if (online.contains(target.uuid())) {
                delivered.add(target);
            }
        }

        @Override
        public Collection<PlayerRef> onlinePlayers() {
            return List.of();
        }

        @Override
        public void freeze(PlayerRef target) {}

        @Override
        public void unfreeze(PlayerRef target) {}

        @Override
        public boolean isFrozen(PlayerRef target) {
            return false;
        }

        @Override
        public void sendToJail(PlayerRef target, String jail) {}

        @Override
        public void releaseFromJail(PlayerRef target) {}
    }

    private static EnforceRemoteBan enforce(FakeModerationRepository repository, Sanctions sanctions) {
        return new EnforceRemoteBan(
                repository, sanctions, ModerationFakes.notifier(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void kicksAnOnlineTargetWhoseBanIsInEffect() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.saveTempban(
                TARGET,
                TempbanState.active(NOW.plus(Duration.ofDays(7)), Issuer.console("console"), Optional.of("ban"), NOW));
        EntityHoppingSanctions sanctions = new EntityHoppingSanctions(Set.of(TARGET.uuid()));

        enforce(repository, sanctions).onRemoteBan(TARGET.uuid());

        assertThat(sanctions.delivered).containsExactly(TARGET);
    }

    @Test
    void attemptsTheKickThroughTheEntityHoppingPortKeyedByUuid() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.saveTempban(
                TARGET,
                TempbanState.active(NOW.plus(Duration.ofDays(7)), Issuer.console("console"), Optional.empty(), NOW));
        EntityHoppingSanctions sanctions = new EntityHoppingSanctions(Set.of(TARGET.uuid()));

        enforce(repository, sanctions).onRemoteBan(TARGET.uuid());

        // The use case keys the kick on the target UUID, leaving the entity-thread resolution to the adapter
        // no PlayerLookup / Bukkit call ever happens here off the bus thread.
        assertThat(sanctions.attempted).containsExactly(TARGET.uuid());
    }

    @Test
    void leavesTheOfflineNoOpToTheKickAdapter() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.saveTempban(
                TARGET,
                TempbanState.active(NOW.plus(Duration.ofDays(7)), Issuer.console("console"), Optional.empty(), NOW));
        EntityHoppingSanctions sanctions = new EntityHoppingSanctions(new HashSet<>());

        enforce(repository, sanctions).onRemoteBan(TARGET.uuid());

        // The kick is attempted unconditionally; the adapter no-ops on the entity thread when the player is gone.
        assertThat(sanctions.attempted).containsExactly(TARGET.uuid());
        assertThat(sanctions.delivered).isEmpty();
    }

    @Test
    void doesNotKickWhenNoActiveBanIsOnRecord() {
        // The shared DB has no active ban (e.g. it was lifted between the frame and this read): nothing attempted.
        FakeModerationRepository repository = new FakeModerationRepository();
        EntityHoppingSanctions sanctions = new EntityHoppingSanctions(Set.of(TARGET.uuid()));

        enforce(repository, sanctions).onRemoteBan(TARGET.uuid());

        assertThat(sanctions.attempted).isEmpty();
    }

    @Test
    void doesNotKickWhenTheBanHasAlreadyExpired() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.saveTempban(
                TARGET,
                TempbanState.active(
                        NOW.minus(Duration.ofMinutes(1)),
                        Issuer.console("console"),
                        Optional.empty(),
                        NOW.minus(Duration.ofHours(1))));
        EntityHoppingSanctions sanctions = new EntityHoppingSanctions(Set.of(TARGET.uuid()));

        enforce(repository, sanctions).onRemoteBan(TARGET.uuid());

        assertThat(sanctions.attempted).isEmpty();
    }
}
