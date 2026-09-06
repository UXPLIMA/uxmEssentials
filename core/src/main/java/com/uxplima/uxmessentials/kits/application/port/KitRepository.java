package com.uxplima.uxmessentials.kits.application.port;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;

/**
 * The store of operator-curated kit definitions. Kits are config-driven. Each definition lives in its own
 * {@code modules/kits/kits/<id>.conf} (Configurate), loaded once on enable and swapped atomically on reload
 * so this port is the application's read/write view over that config-backed catalog rather than a database
 * table. The authoring commands ({@code /kit create}, {@code /kit del}, {@code /kit editor}) go through
 * {@link #save} and {@link #delete}, which the adapter persists to the kit's own file.
 *
 * <p>Kits are server-wide, so a {@link KitId} is unique across the whole catalog. Lookup and the listing are
 * the hot paths and stay in memory; saving and deleting are rare authoring operations.
 */
public interface KitRepository {

    /** The kit defined under {@code id}, or empty when no such kit exists. */
    Optional<KitDefinition> find(KitId id);

    /** Every defined kit, in catalog order. */
    List<KitDefinition> all();

    /** True when a kit is already defined under {@code id}. */
    boolean exists(KitId id);

    /** Create or overwrite the kit {@code definition}, persisting it back to the config-backed catalog. */
    void save(KitDefinition definition);

    /** Remove the kit defined under {@code id}, if present. */
    void delete(KitId id);
}
