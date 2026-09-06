package com.uxplima.uxmessentials.staff.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.Freeze;
import com.uxplima.uxmessentials.moderation.application.ModerationGuard;
import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.staff.adapter.outbound.ModerationStaffFreeze;
import com.uxplima.uxmessentials.staff.application.port.StaffFreeze.FreezeOutcome;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link ModerationStaffFreeze} routes the FREEZE gadget through the real moderation {@link Freeze} use case:
 * a free target freezes, a frozen one unfreezes, and an exempt target maps to the dedicated EXEMPT outcome.
 */
class ModerationStaffFreezeTest {

    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "Actor");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "Target");

    private FakeSanctions sanctions;
    private Set<UUID> exempt;
    private ModerationStaffFreeze freeze;

    @BeforeEach
    void setUp() {
        sanctions = new FakeSanctions();
        exempt = new HashSet<>();
        Freeze useCase = new Freeze(sanctions, new ModerationGuard(new ExemptPermissions(exempt)), notifier(), audit());
        freeze = new ModerationStaffFreeze(useCase, sanctions);
    }

    @Test
    void freezesAFreeTarget() {
        assertThat(freeze.toggle(ACTOR, TARGET)).isEqualTo(FreezeOutcome.FROZEN);
        assertThat(sanctions.isFrozen(TARGET)).isTrue();
    }

    @Test
    void unfreezesAFrozenTarget() {
        sanctions.freeze(TARGET);

        assertThat(freeze.toggle(ACTOR, TARGET)).isEqualTo(FreezeOutcome.UNFROZEN);
        assertThat(sanctions.isFrozen(TARGET)).isFalse();
    }

    @Test
    void reportsExemptForAnExemptTarget() {
        exempt.add(TARGET.uuid());

        assertThat(freeze.toggle(ACTOR, TARGET)).isEqualTo(FreezeOutcome.EXEMPT);
        assertThat(sanctions.isFrozen(TARGET)).isFalse();
    }

    private static Notifier notifier() {
        Messages messages = (viewer, key, placeholders) -> key.key();
        MessageSink sink = (viewer, renderedText) -> {};
        return new Notifier(messages, sink);
    }

    /** A no-op audit: the FREEZE mapping does not depend on what the audit records. */
    private static ModerationAudit audit() {
        return new NoopAudit();
    }

    private static final class FakeSanctions implements Sanctions {
        private final Set<UUID> frozen = new HashSet<>();

        @Override
        public void kick(PlayerRef target, MessageKey reasonKey, String reasonText) {}

        @Override
        public Collection<PlayerRef> onlinePlayers() {
            return Set.of();
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
        public void sendToJail(PlayerRef target, String jail) {}

        @Override
        public void releaseFromJail(PlayerRef target) {}
    }

    private record ExemptPermissions(Set<UUID> exemptUuids) implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return ModerationGuard.EXEMPT_NODE.equals(node) && exemptUuids.contains(who.uuid());
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class NoopAudit implements ModerationAudit {
        @Override
        public void muted(
                PlayerRef actor, PlayerRef target, Optional<String> duration, boolean ok, Optional<String> reason) {}

        @Override
        public void unmuted(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

        @Override
        public void jailed(
                PlayerRef actor,
                PlayerRef target,
                String jail,
                Optional<String> duration,
                boolean ok,
                Optional<String> reason) {}

        @Override
        public void unjailed(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

        @Override
        public void tempbanned(
                PlayerRef actor, PlayerRef target, String duration, boolean ok, Optional<String> reason) {}

        @Override
        public void unbanned(PlayerRef actor, PlayerRef target, boolean ok) {}

        @Override
        public void warned(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

        @Override
        public void clearedWarns(PlayerRef actor, PlayerRef target, boolean ok, int count) {}

        @Override
        public void kicked(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

        @Override
        public void kickedAll(PlayerRef actor, int affected, Optional<String> reason) {}

        @Override
        public void froze(PlayerRef actor, PlayerRef target, boolean frozen, boolean ok) {}

        @Override
        public void ipBanned(
                PlayerRef actor,
                String targetIp,
                Optional<UUID> target,
                Optional<String> duration,
                boolean ok,
                Optional<String> reason) {}

        @Override
        public void ipUnbanned(PlayerRef actor, String targetIp, boolean ok) {}

        @Override
        public void altDetected(UUID uuid, String ip, List<UUID> matchedAlts, boolean kicked) {}

        @Override
        public void jailLocationDefined(PlayerRef actor, String jail) {}

        @Override
        public void jailLocationRemoved(PlayerRef actor, String jail) {}

        @Override
        public void lockdown(UUID actor, boolean enabled) {}
    }
}
