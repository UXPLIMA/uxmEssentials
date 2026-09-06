package com.uxplima.uxmessentials.teleport.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;

/**
 * Outbound port for spawn resolution: the operator-set per-world spawn, the single global main spawn used
 * when a world has no spawn of its own, operator-defined named spawns, and the per-world spawn-mirror rules.
 * The {@link com.uxplima.uxmessentials.teleport.application.ResolveSpawn} use case folds a mirror (when
 * present) through this directory so a mirrored {@code /spawn} reads the live target-world spawn rather than
 * a copied location.
 *
 * <p>Two read methods address the per-world spawn at different layers. {@link #operatorSpawn} returns only
 * the operator-set row (empty when none) so the use case can fall through to the {@link #mainSpawn} and then
 * the directory's own last-resort {@link #defaultSpawn}; {@link #defaultSpawn} is the bottom of the chain and
 * may fold in a platform fallback (the vanilla world spawn) so {@code /spawn} still answers on a fresh server.
 */
public interface SpawnDirectory {

    /**
     * The bottom-of-the-chain default spawn for {@code world}: the operator-set per-world spawn or, when none
     * is set, whatever last-resort the adapter folds in (the vanilla world spawn). Empty only when nothing at
     * all resolves.
     */
    Optional<Position> defaultSpawn(WorldRef world);

    /** Only the operator-set per-world spawn for {@code world}, with no fallback: empty when none is set. */
    Optional<Position> operatorSpawn(WorldRef world);

    /** The global main spawn ({@code /setmainspawn}) used when a world has no spawn of its own, or empty. */
    Optional<Position> mainSpawn();

    /** A named spawn ({@code /spawn <name>}), or empty when no spawn answers to that name. */
    Optional<Position> namedSpawn(String name);

    /** The spawn-mirror rule for {@code world}, when {@code /spawn} there redirects elsewhere. */
    Optional<SpawnMirror> mirrorFor(WorldRef world);

    /** Set or replace the per-world spawn for {@code world}. */
    void setDefaultSpawn(WorldRef world, Position position);

    /** Set or replace the named spawn {@code name}. */
    void setNamedSpawn(String name, Position position);

    /** Set or replace the global main spawn. */
    void setMainSpawn(Position position);

    /** Remove the operator-set per-world spawn for {@code world}; true when a spawn existed and was removed. */
    boolean removeDefaultSpawn(WorldRef world);

    /** Set or replace the spawn-mirror redirect for {@link SpawnMirror#sourceWorld()}. */
    void setMirror(SpawnMirror mirror);
}
