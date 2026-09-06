package com.uxplima.uxmessentials.communication.application.port;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.communication.domain.StoredAnnouncement;

/**
 * Outbound port for the DB-backed announcement set the editor GUI owns. Where the config announcer
 * ({@code announcer.conf}) is file-managed and read-only at runtime, this store holds the announcements an operator
 * creates, edits, enables/disables, and deletes from the {@code /announce} editor, one row per id in the
 * {@code communication_announcement} table. The announcer rotates over the config set <em>plus</em> the
 * {@link #enabled() enabled} store set, so an edit through this port takes effect on the next announcer tick (the
 * merged source re-reads) with no reload.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>DB-backed</b>. The database is the source of truth (a stored announcement survives a world
 * rollback). Every method is called off the tick thread. The editor writes through the shared {@code Scheduler}'s
 * async executor, and the announcer reads the merged set from its async loop, so no JVM lock is held. {@link #save}
 * is a single upsert on the id key, so two edits to the same id serialise at the database.
 *
 * <p>The implementation lives in the persistence adapter; the communication context depends only on this port. The
 * lines, channels, condition, and sound a {@link StoredAnnouncement} carries are operator content, never a
 * {@code MessageKey} and never parity-checked: only the editor's framing text is.
 */
public interface AnnouncementStore {

    /** Every stored announcement, enabled or not, ordered by id: the full set the editor list renders. */
    List<StoredAnnouncement> all();

    /** Only the enabled stored announcements: the set the announcer merges into its rotation. */
    List<StoredAnnouncement> enabled();

    /** The stored announcement with {@code id}, or empty when none is stored under it. */
    Optional<StoredAnnouncement> find(String id);

    /** Whether an announcement is already stored under {@code id} (the editor's create-collision check). */
    boolean exists(String id);

    /** Upsert {@code announcement} on its id key: a re-save under an existing id overwrites that row in place. */
    void save(StoredAnnouncement announcement);

    /** Delete the announcement stored under {@code id}; returns whether a row was removed. */
    boolean delete(String id);
}
