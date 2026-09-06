package com.uxplima.uxmessentials.moderation.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the moderation audit trail. The {@code com.uxplima.uxmessentials.audit} channel
 * (docs/09-deployment.md §Audit logging). Every state-mutating sanction emits exactly one structured
 * {@code event=<name> actor=<sender> key=value} line so an operator can route the channel to a retained file
 * and reconstruct moderation activity. The implementation lands with the bukkit-adapter; the use cases
 * depend only on this contract.
 *
 * <p>The {@code actor} is the issuing staff member (or the console); {@code target} the affected player;
 * {@code ok} whether the action took effect. A {@code reason} is logged on failure (and on success where the
 * catalogue lists it). The names mirror the catalogue rows one-for-one.
 */
public interface ModerationAudit {

    /** {@code event=player_mute}, a {@code /mute}/{@code /tempmute}. */
    void muted(PlayerRef actor, PlayerRef target, Optional<String> duration, boolean ok, Optional<String> reason);

    /** {@code event=player_unmute}, a {@code /unmute}. */
    void unmuted(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason);

    /** {@code event=player_jail}, a {@code /jail}. */
    void jailed(
            PlayerRef actor,
            PlayerRef target,
            String jail,
            Optional<String> duration,
            boolean ok,
            Optional<String> reason);

    /** {@code event=player_unjail}, an {@code /unjail}. */
    void unjailed(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason);

    /** {@code event=player_tempban}, a {@code /tempban} or a {@code /ban} (the permanent form). */
    void tempbanned(PlayerRef actor, PlayerRef target, String duration, boolean ok, Optional<String> reason);

    /** {@code event=player_unban}, an {@code /unban}. */
    void unbanned(PlayerRef actor, PlayerRef target, boolean ok);

    /** {@code event=player_warn}, a {@code /warn}. */
    void warned(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason);

    /** {@code event=player_unwarn}: an {@code /unwarn}, with the number of warnings cleared. */
    void clearedWarns(PlayerRef actor, PlayerRef target, boolean ok, int count);

    /** {@code event=player_kick}, a {@code /kick} or one target of {@code /kickall}. */
    void kicked(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason);

    /** {@code event=player_kickall}: a {@code /kickall}, one line with the affected count. */
    void kickedAll(PlayerRef actor, int affected, Optional<String> reason);

    /** {@code event=player_freeze}, a {@code /freeze} or {@code /unfreeze} ({@code frozen} tells which). */
    void froze(PlayerRef actor, PlayerRef target, boolean frozen, boolean ok);

    /** {@code event=player_banip}: a {@code /banip}, with the resolved target UUID when one was found. */
    void ipBanned(
            PlayerRef actor,
            String targetIp,
            Optional<UUID> target,
            Optional<String> duration,
            boolean ok,
            Optional<String> reason);

    /** {@code event=player_unbanip}, an {@code /unbanip}. */
    void ipUnbanned(PlayerRef actor, String targetIp, boolean ok);

    /** {@code event=alt_detected}: a connecting IP matched an existing sanction or the known-alt set at login. */
    void altDetected(UUID uuid, String ip, List<UUID> matchedAlts, boolean kicked);

    /** {@code event=jail_location_set}: a {@code /setjail} saved (or re-anchored) a stored jail location. */
    void jailLocationDefined(PlayerRef actor, String jail);

    /** {@code event=jail_location_delete}: a {@code /jail del} removed a stored jail location. */
    void jailLocationRemoved(PlayerRef actor, String jail);

    /** {@code event=server_lockdown}: a {@code /lockdown} flipped the server-wide login gate ({@code enabled}). */
    void lockdown(UUID actor, boolean enabled);
}
