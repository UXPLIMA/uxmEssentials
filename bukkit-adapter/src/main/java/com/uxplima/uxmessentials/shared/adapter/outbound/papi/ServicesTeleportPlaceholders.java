package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownKind;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.WarmupTracker;
import com.uxplima.uxmessentials.teleport.application.port.BackLocationStore;
import com.uxplima.uxmessentials.teleport.application.port.RequestRegistry;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFlags;
import com.uxplima.uxmessentials.teleport.domain.BackLocation;
import org.jspecify.annotations.NullMarked;

/**
 * {@link TeleportPlaceholders} over the teleport context's read sides: the shared {@link Cooldowns} gate, the
 * in-flight {@link WarmupTracker}, the {@link BackLocationStore}, the {@link RequestRegistry} and the
 * {@link TeleportFlags}. These are the same stores the teleport commands read, so a placeholder agrees with
 * what the player experiences.
 *
 * <p>The cooldown read uses the generic shared {@code tp} kind. The one every cooldowned verb
 * ({@code /home}, {@code /warp}, {@code /rtp}, {@code /spawn}, {@code /back}) gates against, so the
 * placeholder reports the same remaining wait the gate would. {@link Cooldowns#check} returns the remaining
 * duration on its error side, which is exactly what the placeholder renders; a ready cooldown is empty. The
 * warmup remaining is read from the tracker's recorded completion instant minus the clock.
 *
 * <p>This adapter is only registered when teleport is enabled; the resolver degrades the placeholders to the
 * dash when the seam is absent.
 */
@NullMarked
public final class ServicesTeleportPlaceholders implements TeleportPlaceholders {

    /** The shared teleport cooldown feature segment ({@code uxmessentials.cooldown.tp.<seconds>}). */
    private static final String FEATURE = "tp";

    private final Cooldowns cooldowns;
    private final CooldownKind cooldownKind;
    private final WarmupTracker warmupTracker;
    private final BackLocationStore backStore;
    private final RequestRegistry requests;
    private final TeleportFlags flags;
    private final Clock clock;

    public ServicesTeleportPlaceholders(
            Cooldowns cooldowns,
            WarmupTracker warmupTracker,
            BackLocationStore backStore,
            RequestRegistry requests,
            TeleportFlags flags,
            Clock clock) {
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.warmupTracker = Objects.requireNonNull(warmupTracker, "warmupTracker");
        this.backStore = Objects.requireNonNull(backStore, "backStore");
        this.requests = Objects.requireNonNull(requests, "requests");
        this.flags = Objects.requireNonNull(flags, "flags");
        this.clock = Objects.requireNonNull(clock, "clock");
        // The default seconds is irrelevant to a read. Check() only consults the per-holder stamp and the
        // bypass node, so a neutral default keeps the kind valid without re-reading teleport.conf.
        this.cooldownKind = new CooldownKind(FEATURE, 0L, Cooldowns.CooldownStartPhase.TELEPORT);
    }

    @Override
    public Optional<Duration> cooldownRemaining(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return cooldowns.check(who, cooldownKind).asError();
    }

    @Override
    public Optional<Duration> warmupRemaining(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return warmupTracker.completesAt(who.uuid()).map(at -> {
            Duration left = Duration.between(clock.instant(), at);
            return left.isNegative() ? Duration.ZERO : left;
        });
    }

    @Override
    public Optional<BackView> backLocation(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return backStore.current(who).map(ServicesTeleportPlaceholders::view);
    }

    @Override
    public int incomingRequests(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return requests.pendingFor(who).size();
    }

    @Override
    public boolean hasOutgoingRequest(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return requests.outgoing(who).isPresent();
    }

    @Override
    public boolean acceptingRequests(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return flags.acceptsRequests(who);
    }

    private static BackView view(BackLocation location) {
        var position = location.position();
        return new BackView(position.world().name(), position.blockX(), position.blockY(), position.blockZ());
    }
}
