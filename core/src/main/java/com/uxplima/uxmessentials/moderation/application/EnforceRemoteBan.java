package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The live reaction to a ban issued on another backend: when a peer reports that a player's ban changed, this
 * re-evaluates the now-authoritative ban for that player <em>on this backend</em> and kicks them if it is in
 * effect and they are connected here. The durable enforcement is already free. Every backend re-reads the
 * shared ban row on the next login, so this only closes the live gap: a player a peer already had online is
 * removed at once rather than lingering until they reconnect.
 *
 * <p>The re-evaluation mirrors the ban-on-login check ({@link LoginEnforcement}): it reads the fresh
 * {@link TempbanState} from the shared DB and, if {@link TempbanState.Active} and active now, renders the same
 * {@link ModerationMessageKey#TEMPBAN_KICK} screen and kicks. A still-online player whose ban turns out not to
 * be active (lifted between the frame and this read, or an out-of-order frame) is left alone, the DB, not the
 * frame, is the source of truth.
 *
 * <p><b>Threading.</b> This runs off the tick thread (the bus dispatches inbound frames through
 * {@link com.uxplima.uxmessentials.shared.application.port.Scheduler#async}), so it must touch nothing Bukkit.
 * The only off-thread work is the shared-DB tempban read, which is safe. There is no online lookup here: the
 * kick is attempted unconditionally through the {@link Sanctions} adapter, which hops to the target's entity
 * region thread and silently no-ops when the player is not connected to this backend, so an offline target is
 * simply left to the login check on reconnect, with no Bukkit call ever made from the bus thread. The kick is
 * keyed by UUID through a name-less {@link PlayerRef}, so the use case never resolves a Bukkit player off-thread.
 *
 * <p>Self-origin frames never reach here: the bus client drops any frame whose origin equals this backend's id
 * before dispatching to a listener, so the player the local {@code /ban} already kicked is not kicked twice.
 */
public final class EnforceRemoteBan {

    private final ModerationRepository repository;
    private final Sanctions sanctions;
    private final Notifier notifier;
    private final Clock clock;

    public EnforceRemoteBan(ModerationRepository repository, Sanctions sanctions, Notifier notifier, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Re-evaluate {@code target}'s ban here and kick them if it is in effect; a no-op when not connected. */
    public void onRemoteBan(UUID target) {
        Objects.requireNonNull(target, "target");
        // A name-less ref keyed on the UUID: the kick adapter resolves the live player on the entity thread, so
        // no Bukkit lookup happens here on the async bus thread. The DB read below is safe off the tick thread.
        PlayerRef ref = new PlayerRef(target, target.toString());
        Instant now = clock.instant();
        if (repository.loadTempban(ref) instanceof TempbanState.Active ban && ban.isActiveAt(now)) {
            MessageKey key = ModerationMessageKey.TEMPBAN_KICK;
            Map<String, String> ph = Map.of(
                    "duration", SanctionDuration.format(Duration.between(now, ban.until())),
                    "reason", ban.reason().orElse(""));
            sanctions.kick(ref, key, notifier.render(ref, key, ph));
        }
    }
}
