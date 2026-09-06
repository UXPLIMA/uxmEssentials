package com.uxplima.uxmessentials.itemworld.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.itemworld.domain.MobSpec;
import com.uxplima.uxmessentials.itemworld.domain.PurgeSelection;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the itemworld audit trail. The {@code com.uxplima.uxmessentials.audit} channel
 * (docs/09-deployment.md §Audit logging, docs/permissions.md §Audit trail). Only the <em>abusable</em>
 * itemworld verbs emit a line: a bulk {@code /give}, a {@code /spawnmob}, a {@code /spawner} retype, the
 * {@code /kill}//{@code /butcher}//{@code /killall}//{@code /remove} entity-purge family, and the {@code
 * /lightning}//{@code /fireball}//{@code /kittycannon}//{@code /break}//{@code /tree}//{@code /nuke} admin-fun
 * verbs. The cosmetic and workstation verbs are not audited. The event names mirror the {@code itemworld_*}
 * rows in the audit catalogue one-for-one.
 *
 * <p>The implementation lands with the bukkit-adapter (mirroring {@code LoggingModerationAudit}); the use
 * cases depend only on this contract. Each abusable-verb audit line is additionally gated by a per-action
 * {@code audit} toggle in {@code itemworld.conf}, so an operator can quiet a high-volume verb while keeping
 * the channel for the rest.
 *
 * @see com.uxplima.uxmessentials.itemworld.application.ItemworldConfig#auditEnabled
 */
public interface ItemworldAudit {

    /** {@code event=itemworld_give}: a {@code /give}//{@code /i} whose amount exceeded the bulk threshold. */
    void gave(PlayerRef actor, PlayerRef target, String itemKey, int amount);

    /** {@code event=itemworld_spawnmob}, a {@code /spawnmob}. */
    void spawnedMob(PlayerRef actor, MobSpec spec, int spawned);

    /** {@code event=itemworld_spawner}, a {@code /spawner} retype. */
    void retypedSpawner(PlayerRef actor, String mobType);

    /** {@code event=itemworld_kill}, a {@code /kill} of a player or entity. */
    void killed(PlayerRef actor, String target);

    /** {@code event=itemworld_butcher}: a {@code /butcher}, with the count removed. */
    void butchered(PlayerRef actor, PurgeSelection selection, int removed);

    /** {@code event=itemworld_killall}: a {@code /killall}, with the count removed. */
    void killedAll(PlayerRef actor, PurgeSelection selection, int removed);

    /** {@code event=itemworld_remove}: a {@code /remove}, with the count removed. */
    void removed(PlayerRef actor, PurgeSelection selection, int removed);

    /** {@code event=itemworld_lightning}: a {@code /lightning}//{@code /smite}. */
    void struckLightning(PlayerRef actor, Optional<PlayerRef> target);

    /** {@code event=itemworld_fireball}, a {@code /fireball}. */
    void launchedFireball(PlayerRef actor);

    /** {@code event=itemworld_kittycannon}, a {@code /kittycannon}. */
    void firedKittycannon(PlayerRef actor);

    /** {@code event=itemworld_antioch}, an {@code /antioch}//{@code /grenade}. */
    void threwAntioch(PlayerRef actor);

    /** {@code event=itemworld_beezooka}, a {@code /beezooka}//{@code /beecannon}. */
    void firedBeezooka(PlayerRef actor);

    /** {@code event=itemworld_break}: a {@code /break} of the looked-at block, recorded with its type. */
    void brokeBlock(PlayerRef actor, String blockType);

    /** {@code event=itemworld_tree}, a {@code /tree} of the given type, grown where the caller is looking. */
    void grewTree(PlayerRef actor, String type);

    /** {@code event=itemworld_nuke}: a {@code /nuke} raining lightning over the looked-at area or a player. */
    void nuked(PlayerRef actor, Optional<PlayerRef> target);
}
