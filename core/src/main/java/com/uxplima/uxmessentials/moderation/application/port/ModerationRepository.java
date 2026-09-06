package com.uxplima.uxmessentials.moderation.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.domain.BanEntry;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.JailEntry;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.MuteEntry;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The DB-backed sanction store (the hard moderation invariant: state survives restart, never PDC). Each
 * sanction kind is its own table behind this one port; the {@link ModerationProfile} read rebuilds a
 * target's mute, jail and tempban in one shot, while the writes are keyed per kind so an {@code /unmute}
 * touches only the mute row.
 *
 * <p>An offline target's row is lazily materialized before any FK-bearing write ({@code ensureUserExists},
 * docs/09-deployment) so an offline {@code /jail} or {@code /banip} never breaks referential integrity. The
 * sweep methods remove sanctions whose wall-clock expiry has passed; the online-only jail countdown is not
 * swept here: it is decremented by the join/quit tick and released by {@code /unjail}.
 */
public interface ModerationRepository {

    /** Rebuild {@code target}'s mute/jail/tempban from the per-sanction tables; a clean target yields no rows. */
    ModerationProfile load(PlayerRef target);

    /** The current mute alone, for the {@code MutePolicy} hot path (no jail/tempban read). */
    MuteState loadMute(PlayerRef target);

    /** The current jail alone, for the {@code JailGate} hot path and the online-only tick. */
    JailState loadJail(PlayerRef target);

    /** The current tempban alone, for the ban-on-login check. */
    TempbanState loadTempban(PlayerRef target);

    /**
     * The bans still in effect at {@code now} (both timed and the far-future rows a {@code /ban} records),
     * newest-first and capped at {@code limit} so the {@code /banlist} read stays within budget. Expired
     * tempbans are filtered out at the query.
     */
    List<BanEntry> activeBans(Instant now, int limit);

    /**
     * The mutes still in effect at {@code now} (permanent rows and timed rows not yet expired), newest-first
     * and capped at {@code limit} so the {@code /mutelist} read stays within budget.
     */
    List<MuteEntry> activeMutes(Instant now, int limit);

    /**
     * The jails still in effect at {@code now} (permanent, online-only and wall-clock rows whose expiry has
     * not passed), newest-first and capped at {@code limit} so the {@code /jailedplayers} read stays within
     * budget. An online-only sentence is always returned while it has a row, since its countdown advances on
     * online time rather than the wall clock.
     */
    List<JailEntry> activeJails(Instant now, int limit);

    /** Upsert {@code target}'s mute. {@link MuteState.None} deletes the row. */
    void saveMute(PlayerRef target, MuteState mute);

    /** Upsert {@code target}'s jail. {@link JailState.None} deletes the row. */
    void saveJail(PlayerRef target, JailState jail);

    /** Upsert {@code target}'s tempban. {@link TempbanState.None} deletes the row. */
    void saveTempban(PlayerRef target, TempbanState tempban);

    /**
     * Append one warning to {@code target}'s history and return the count of warnings still in effect at the
     * appended warning's issue instant: a standing warning and any timed warning not yet lapsed. The history
     * itself stays append-only; the count simply excludes already-lapsed timed warnings.
     */
    int appendWarn(PlayerRef target, Warn warn);

    /** {@code target}'s warnings still in effect at {@code now}, newest-first (lapsed timed warnings dropped). */
    List<Warn> warns(PlayerRef target, Instant now);

    /** Remove all of {@code target}'s warnings; returns the number removed. */
    int clearWarns(PlayerRef target);

    /**
     * Remove only the warnings on {@code target} that {@code actor} issued (matched on the warn's
     * {@code warned_by} UUID), leaving every other staff member's warnings on {@code target} intact; returns
     * the number removed. A console/system actor (no UUID) matches no row. This is what {@code /staffrollback}
     * uses so rolling back one staff member never wipes another's warnings.
     */
    int clearWarnsByActor(PlayerRef target, PlayerRef actor);

    /** Upsert an IP ban (keyed by address); a re-ban of the same address overwrites. */
    void saveIpBan(IpBan ban);

    /** Remove the IP ban for {@code ip}; returns true when a row was removed. */
    boolean removeIpBan(String ip);

    /** The active IP ban for {@code ip} at {@code now}, if any (expired bans are not returned). */
    Optional<IpBan> activeIpBan(String ip, Instant now);

    /** Record or advance {@code who}'s last-seen/last-IP on join/quit. */
    void recordSeen(PlayerRef who, Optional<String> ip, Instant at);

    /**
     * {@code who}'s last-seen/last-IP record, if ever recorded. The address history behind {@code /alts} and the
     * STRICT ban fan-out is not here: it lives in the kernel
     * {@link com.uxplima.uxmessentials.shared.application.port.IpHistoryStore}, which security reads too, so one
     * capture feeds both contexts.
     */
    Optional<SeenRecord> seen(PlayerRef who);

    /** Lazily materialize {@code target}'s seen row so an offline FK-bearing write never breaks integrity. */
    void ensureUserExists(PlayerRef target, Instant at);

    /** Whether the server is currently locked down (only bypass-perm holders may join). Survives restart. */
    boolean isLockedDown();

    /** Set the server lockdown flag; persisted so a restart re-applies it until an operator lifts it. */
    void setLockedDown(boolean enabled);
}
