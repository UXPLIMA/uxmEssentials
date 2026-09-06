package com.uxplima.uxmessentials.shared.adapter.outbound.warmup;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link Warmups} implementation: a countdown scheduled through the injected {@link Scheduler}
 * with no PDC stamp, so a warmup is purely in-flight runtime state the owning module drops on
 * {@code stop()}. The duration is resolved from the player's numbered
 * {@code uxmessentials.<feature>.warmup.<seconds>} nodes via {@link Permissions} (the {@code min}
 * reducer. The shortest tier wins, {@code 0} removes the warmup), and the {@code .bypass} node starts
 * the teleport immediately, immune to move-cancel.
 *
 * <p>The countdown holds no scheduler resource: it sleeps once via {@link Scheduler#asyncAfter} and on
 * wake re-checks the handle's {@code cancelled} flag before hopping to the player's region thread to
 * run {@code onComplete}. The move-cancels-warmup invariant. Flipping that flag when the player's
 * origin block changes: is owned by the teleport context; this adapter only provides the primitive.
 */
@NullMarked
public final class SchedulerWarmups implements Warmups {

    private final Scheduler scheduler;
    private final Permissions permissions;

    public SchedulerWarmups(Scheduler scheduler, Permissions permissions) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    @Override
    public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(onComplete, "onComplete");
        Objects.requireNonNull(onCancel, "onCancel");
        Duration duration = resolveDuration(who, kind);
        if (duration.isZero() || duration.isNegative()) {
            onComplete.run();
            return new CompletedWarmup(who);
        }
        PendingWarmup handle = new PendingWarmup(who, onComplete, onCancel);
        scheduler.asyncAfter(duration, () -> fire(who, handle));
        return handle;
    }

    private void fire(PlayerRef who, PendingWarmup handle) {
        if (handle.isCancelled()) {
            return; // the owning context already cancelled this warmup; onCancel ran at cancel time
        }
        scheduler.onEntity(who, handle::complete);
    }

    private Duration resolveDuration(PlayerRef who, WarmupKind kind) {
        if (permissions.has(who, kind.bypassNode())) {
            return Duration.ZERO;
        }
        QuotaFamily family = QuotaFamily.threshold(kind.warmupNode());
        long seconds = permissions
                .resolveQuota(who, family, null, kind.defaultSeconds())
                .orElse(kind.defaultSeconds());
        return Duration.ofSeconds(Math.max(0L, seconds));
    }
}
