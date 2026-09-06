package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link ModerationAudit} decorator that, when the module's {@code discord-notify} knob is on, additionally
 * emits a human-readable {@code event=punishment_notify} line on the shared
 * {@code com.uxplima.uxmessentials.audit} channel for each successful punishment. A "who punished whom" notice
 * (staff, target, type, reason, duration) rendered with player <em>names</em> rather than the operator audit's
 * UUIDs, so the optional Discord bridge that forwards the audit channel surfaces a readable message. It wraps
 * the real operator audit unchanged: every call is delegated first, and the extra notice is best-effort and
 * additive.
 *
 * <p>The seam is reused, not hard-wired to Discord: the notice is one more line on the same SLF4J channel the
 * bridge already subscribes to (docs/09-deployment §Audit logging). With no bridge installed the line simply
 * lands in the audit log like any other, so this never depends on the Discord jar and never fails when the
 * bridge is absent. Only the punitive verbs (ban/tempban, mute, jail, warn, kick) notify; lifts, freezes and
 * infrastructure events do not.
 */
@NullMarked
public final class DiscordPunishmentAudit implements ModerationAudit {

    private final ModerationAudit delegate;
    private final Logger notifications;
    private final boolean enabled;

    public DiscordPunishmentAudit(ModerationAudit delegate, Logger notifications, boolean enabled) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.enabled = enabled;
    }

    @Override
    public void muted(
            PlayerRef actor, PlayerRef target, Optional<String> duration, boolean ok, Optional<String> reason) {
        delegate.muted(actor, target, duration, ok, reason);
        if (ok) {
            notify("mute", actor, target, duration.orElse("permanent"), reason);
        }
    }

    @Override
    public void unmuted(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {
        delegate.unmuted(actor, target, ok, reason);
    }

    @Override
    public void jailed(
            PlayerRef actor,
            PlayerRef target,
            String jail,
            Optional<String> duration,
            boolean ok,
            Optional<String> reason) {
        delegate.jailed(actor, target, jail, duration, ok, reason);
        if (ok) {
            notify("jail", actor, target, duration.orElse("permanent"), reason);
        }
    }

    @Override
    public void unjailed(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {
        delegate.unjailed(actor, target, ok, reason);
    }

    @Override
    public void tempbanned(PlayerRef actor, PlayerRef target, String duration, boolean ok, Optional<String> reason) {
        delegate.tempbanned(actor, target, duration, ok, reason);
        if (ok) {
            // A permanent ban reuses the tempban row with the "permanent" label; distinguish it in the notice.
            notify("permanent".equals(duration) ? "ban" : "tempban", actor, target, duration, reason);
        }
    }

    @Override
    public void unbanned(PlayerRef actor, PlayerRef target, boolean ok) {
        delegate.unbanned(actor, target, ok);
    }

    @Override
    public void warned(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {
        delegate.warned(actor, target, ok, reason);
        if (ok) {
            notify("warn", actor, target, "none", reason);
        }
    }

    @Override
    public void clearedWarns(PlayerRef actor, PlayerRef target, boolean ok, int count) {
        delegate.clearedWarns(actor, target, ok, count);
    }

    @Override
    public void kicked(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {
        delegate.kicked(actor, target, ok, reason);
        if (ok) {
            notify("kick", actor, target, "none", reason);
        }
    }

    @Override
    public void kickedAll(PlayerRef actor, int affected, Optional<String> reason) {
        delegate.kickedAll(actor, affected, reason);
    }

    @Override
    public void froze(PlayerRef actor, PlayerRef target, boolean frozen, boolean ok) {
        delegate.froze(actor, target, frozen, ok);
    }

    @Override
    public void ipBanned(
            PlayerRef actor,
            String targetIp,
            Optional<UUID> target,
            Optional<String> duration,
            boolean ok,
            Optional<String> reason) {
        delegate.ipBanned(actor, targetIp, target, duration, ok, reason);
    }

    @Override
    public void ipUnbanned(PlayerRef actor, String targetIp, boolean ok) {
        delegate.ipUnbanned(actor, targetIp, ok);
    }

    @Override
    public void altDetected(UUID uuid, String ip, List<UUID> matchedAlts, boolean kicked) {
        delegate.altDetected(uuid, ip, matchedAlts, kicked);
    }

    @Override
    public void jailLocationDefined(PlayerRef actor, String jail) {
        delegate.jailLocationDefined(actor, jail);
    }

    @Override
    public void jailLocationRemoved(PlayerRef actor, String jail) {
        delegate.jailLocationRemoved(actor, jail);
    }

    @Override
    public void lockdown(UUID actor, boolean enabled) {
        delegate.lockdown(actor, enabled);
    }

    private void notify(String type, PlayerRef actor, PlayerRef target, String duration, Optional<String> reason) {
        if (!enabled) {
            return;
        }
        notifications.info(
                "event=punishment_notify type={} staff={} target={} duration={} reason={}",
                type,
                actor.name(),
                target.name(),
                duration,
                quote(reason));
    }

    private static String quote(Optional<String> reason) {
        String value = reason.orElse("");
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
