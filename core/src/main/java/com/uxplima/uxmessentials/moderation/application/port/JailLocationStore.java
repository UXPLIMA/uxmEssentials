package com.uxplima.uxmessentials.moderation.application.port;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.domain.StoredJail;

/**
 * Outbound port for the DB-backed jail locations an operator defines in-game with {@code /setjail}. This is
 * the writable complement to the read-only config {@link JailDirectory}: where the directory reflects the
 * {@code jails} list in {@code moderation.conf}, this store holds jails saved at a staff member's position and
 * survives a world rollback (it is relational, never PDC). The two are merged behind a combined directory so
 * {@code /jail} validates against both and the sanction adapter resolves a stored jail's location.
 *
 * <p>A jail is keyed by its canonical lowercase name (see {@link StoredJail}), so {@link #save} is an upsert
 * a {@code /setjail} onto an existing name overwrites that jail's location in place. The implementation lands
 * in the persistence adapter; the use cases depend only on this contract.
 */
public interface JailLocationStore {

    /** Save {@code jail}, overwriting any stored jail of the same name (an upsert on the name key). */
    void save(StoredJail jail);

    /** True when a jail with the canonical name {@code name} is stored. */
    boolean exists(String name);

    /** The stored jail named {@code name}, or empty when none is stored. */
    Optional<StoredJail> find(String name);

    /** Remove the stored jail named {@code name}; true when a row was removed, false when none existed. */
    boolean delete(String name);

    /** The stored jail names, sorted, for the combined directory to merge with the config names. */
    List<String> names();
}
