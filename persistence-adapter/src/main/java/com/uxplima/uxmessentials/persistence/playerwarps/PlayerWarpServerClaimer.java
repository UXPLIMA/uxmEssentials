package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps.PLAYER_WARPS;

import java.util.Collection;
import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * Stamps this backend's {@code network.server-id} onto the {@code player_warps} rows the T6 data migration and the
 * basic create path leave with a NULL {@code server_id}, so P7's cross-server teleport can route to a warp's home
 * backend. V70 already added the nullable {@code server_id} column; this is a runtime routine, not a migration,
 * because SQL cannot know the runtime backend id or which worlds are loaded on this server.
 *
 * <p>V62 backfilled its own server-id column to the constant default {@code server-1} inside the migration SQL
 * correct only for a single-server install. Player-warps supports a real Velocity + Redis network where a warp that
 * lives in backend B's worlds must be stamped {@code server=B}, not a constant, so the claim runs at enable and is
 * scoped to the worlds loaded on this backend.
 *
 * <p>The claim is idempotent by construction: it only ever writes rows where {@code server_id IS NULL}, so a second
 * enable claims nothing and a row already stamped (by this backend or another) is never overwritten. It is also
 * always scoped. When no worlds are loaded it writes nothing rather than running an unscoped UPDATE that would
 * wrongly claim another backend's warps.
 */
@NullMarked
public final class PlayerWarpServerClaimer extends JooqRepository {

    public PlayerWarpServerClaimer(DSLContext dsl) {
        super(dsl);
    }

    /**
     * Claim over the shared {@link Persistence} handle, hiding the jOOQ {@code DSLContext} from the bukkit-adapter
     * caller (jOOQ is off the consumer's compile classpath) exactly as {@link PlayerWarpDataMigration#run} does. This
     * is the entry point the player-warps wiring calls off the tick thread, chained after the T6 migration.
     */
    public static int claim(Persistence persistence, String serverId, Collection<String> localWorldUids) {
        Objects.requireNonNull(persistence, "persistence");
        return new PlayerWarpServerClaimer(persistence.dsl()).claim(serverId, localWorldUids);
    }

    /**
     * Stamp {@code serverId} onto every NULL-{@code server_id} row whose {@code world} uid is in
     * {@code localWorldUids}, returning the number of rows claimed (for the enable log line). The {@code world}
     * column holds the world uid as canonical 36-character text ({@code WorldRef.uid().toString()}, the shape
     * {@code PlayerWarpRows} writes), so the caller passes uid strings, not world names.
     *
     * @param serverId this backend's non-blank {@code network.server-id}
     * @param localWorldUids the uid strings of the worlds loaded on this backend at enable time; an empty collection
     *     claims nothing (no worlds ⇒ nothing local to claim, and never an unscoped UPDATE)
     * @return the number of rows stamped; {@code 0} when nothing matched or no worlds were passed
     */
    public int claim(String serverId, Collection<String> localWorldUids) {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(localWorldUids, "localWorldUids");
        if (serverId.isBlank()) {
            throw new IllegalArgumentException("serverId must not be blank");
        }
        if (localWorldUids.isEmpty()) {
            return 0;
        }
        return write(dsl -> dsl.update(PLAYER_WARPS)
                .set(PLAYER_WARPS.SERVER_ID, serverId)
                .where(PLAYER_WARPS.SERVER_ID.isNull())
                .and(PLAYER_WARPS.WORLD.in(localWorldUids))
                .execute());
    }
}
