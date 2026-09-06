package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link ModerationAudit} implementation that writes the moderation audit trail to the dedicated
 * {@code com.uxplima.uxmessentials.audit} channel as structured {@code event=<name> actor=<sender> key=value}
 * lines (docs/09-deployment.md §Audit logging). Free-form values (reasons) are double-quoted with backslash
 * escapes so a reason containing whitespace stays one greppable field. Every state-mutating sanction emits
 * exactly one line; {@code /kickall} emits one line with the affected count, never one per kicked player.
 *
 * <p>Audit lines are operator-facing, so they go through a {@link Logger} bound to the audit channel, never
 * the player-facing {@code MessageKey} catalog. The actor/target are logged by UUID for stable identity, with
 * the name carried where the catalogue lists it.
 */
@NullMarked
public final class LoggingModerationAudit implements ModerationAudit {

    private final Logger audit;

    public LoggingModerationAudit(Logger audit) {
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    @Override
    public void muted(
            PlayerRef actor, PlayerRef target, Optional<String> duration, boolean ok, Optional<String> reason) {
        audit.info(
                "event=player_mute actor={} target={} duration={} ok={} reason={}",
                actor.uuid(),
                target.uuid(),
                duration.orElse("permanent"),
                ok,
                quote(reason));
    }

    @Override
    public void unmuted(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {
        audit.info(
                "event=player_unmute actor={} target={} ok={} reason={}",
                actor.uuid(),
                target.uuid(),
                ok,
                quote(reason));
    }

    @Override
    public void jailed(
            PlayerRef actor,
            PlayerRef target,
            String jail,
            Optional<String> duration,
            boolean ok,
            Optional<String> reason) {
        audit.info(
                "event=player_jail actor={} target={} jail={} duration={} ok={} reason={}",
                actor.uuid(),
                target.uuid(),
                jail,
                duration.orElse("permanent"),
                ok,
                quote(reason));
    }

    @Override
    public void unjailed(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {
        audit.info(
                "event=player_unjail actor={} target={} ok={} reason={}",
                actor.uuid(),
                target.uuid(),
                ok,
                quote(reason));
    }

    @Override
    public void tempbanned(PlayerRef actor, PlayerRef target, String duration, boolean ok, Optional<String> reason) {
        audit.info(
                "event=player_tempban actor={} target={} duration={} ok={} reason={}",
                actor.uuid(),
                target.uuid(),
                duration,
                ok,
                quote(reason));
    }

    @Override
    public void unbanned(PlayerRef actor, PlayerRef target, boolean ok) {
        audit.info("event=player_unban actor={} target={} ok={}", actor.uuid(), target.uuid(), ok);
    }

    @Override
    public void warned(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {
        audit.info(
                "event=player_warn actor={} target={} ok={} reason={}", actor.uuid(), target.uuid(), ok, quote(reason));
    }

    @Override
    public void clearedWarns(PlayerRef actor, PlayerRef target, boolean ok, int count) {
        audit.info("event=player_unwarn actor={} target={} count={} ok={}", actor.uuid(), target.uuid(), count, ok);
    }

    @Override
    public void kicked(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {
        audit.info(
                "event=player_kick actor={} target={} ok={} reason={}", actor.uuid(), target.uuid(), ok, quote(reason));
    }

    @Override
    public void kickedAll(PlayerRef actor, int affected, Optional<String> reason) {
        audit.info(
                "event=player_kickall actor={} affected={} ok=true reason={}", actor.uuid(), affected, quote(reason));
    }

    @Override
    public void froze(PlayerRef actor, PlayerRef target, boolean frozen, boolean ok) {
        audit.info("event=player_freeze actor={} target={} frozen={} ok={}", actor.uuid(), target.uuid(), frozen, ok);
    }

    @Override
    public void ipBanned(
            PlayerRef actor,
            String targetIp,
            Optional<UUID> target,
            Optional<String> duration,
            boolean ok,
            Optional<String> reason) {
        audit.info(
                "event=player_banip actor={} target_ip={} target={} duration={} ok={} reason={}",
                actor.uuid(),
                targetIp,
                target.map(UUID::toString).orElse("none"),
                duration.orElse("permanent"),
                ok,
                quote(reason));
    }

    @Override
    public void ipUnbanned(PlayerRef actor, String targetIp, boolean ok) {
        audit.info("event=player_unbanip actor={} target_ip={} ok={}", actor.uuid(), targetIp, ok);
    }

    @Override
    public void altDetected(UUID uuid, String ip, List<UUID> matchedAlts, boolean kicked) {
        audit.info(
                "event=alt_detected uuid={} ip={} matched_alts={} action={}",
                uuid,
                ip,
                matchedAlts.stream().map(UUID::toString).collect(Collectors.joining(",", "[", "]")),
                kicked ? "kick" : "allow");
    }

    @Override
    public void jailLocationDefined(PlayerRef actor, String jail) {
        audit.info("event=jail_location_set actor={} jail={} ok=true", actor.uuid(), jail);
    }

    @Override
    public void jailLocationRemoved(PlayerRef actor, String jail) {
        audit.info("event=jail_location_delete actor={} jail={} ok=true", actor.uuid(), jail);
    }

    @Override
    public void lockdown(UUID actor, boolean enabled) {
        audit.info("event=server_lockdown actor={} enabled={} ok=true", actor, enabled);
    }

    private static String quote(Optional<String> reason) {
        String value = reason.orElse("");
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
