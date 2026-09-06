package com.uxplima.uxmessentials.itemworld.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.itemworld.application.ItemworldConfig;
import com.uxplima.uxmessentials.itemworld.application.port.ItemworldAudit;
import com.uxplima.uxmessentials.itemworld.domain.MobSpec;
import com.uxplima.uxmessentials.itemworld.domain.PurgeSelection;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link ItemworldAudit} implementation that writes the abusable-verb audit trail to the dedicated
 * {@code com.uxplima.uxmessentials.audit} channel as structured {@code event=<name> actor=<uuid> key=value}
 * lines (docs/09-deployment.md §Audit logging), mirroring {@code LoggingModerationAudit}. Only the abusable
 * itemworld verbs reach this adapter, a bulk {@code /give}, a {@code /spawnmob}, a {@code /spawner} retype,
 * the entity-purge family, and the admin-fun verbs; the cosmetic and workstation verbs are not audited.
 *
 * <p>Each line is additionally gated by the per-action {@code audit.<event>} toggle an operator sets in
 * {@code itemworld.conf}, so a high-volume verb can be quieted while the channel stays open for the rest
 * (docs/09-deployment.md §itemworld.conf). The actor/target are logged by UUID for stable identity; the item
 * key and mob type are already normalised by the domain value objects. A disabled audit toggle is a no-op,
 * never a thrown error.
 */
@NullMarked
public final class LoggingItemworldAudit implements ItemworldAudit {

    private final Logger audit;
    private final ItemworldConfig config;

    public LoggingItemworldAudit(Logger audit, ItemworldConfig config) {
        this.audit = Objects.requireNonNull(audit, "audit");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public void gave(PlayerRef actor, PlayerRef target, String itemKey, int amount) {
        if (!config.auditEnabled("give")) {
            return;
        }
        audit.info(
                "event=itemworld_give actor={} target={} item={} amount={} ok=true",
                actor.uuid(),
                target.uuid(),
                itemKey,
                amount);
    }

    @Override
    public void spawnedMob(PlayerRef actor, MobSpec spec, int spawned) {
        if (!config.auditEnabled("spawnmob")) {
            return;
        }
        audit.info(
                "event=itemworld_spawnmob actor={} type={} amount={} spawned={} ok=true",
                actor.uuid(),
                spec.typeId(),
                spec.amount(),
                spawned);
    }

    @Override
    public void retypedSpawner(PlayerRef actor, String mobType) {
        if (!config.auditEnabled("spawner")) {
            return;
        }
        audit.info("event=itemworld_spawner actor={} type={} ok=true", actor.uuid(), mobType);
    }

    @Override
    public void killed(PlayerRef actor, String target) {
        if (!config.auditEnabled("kill")) {
            return;
        }
        audit.info("event=itemworld_kill actor={} target={} ok=true", actor.uuid(), target);
    }

    @Override
    public void butchered(PlayerRef actor, PurgeSelection selection, int removed) {
        if (!config.auditEnabled("butcher")) {
            return;
        }
        audit.info(
                "event=itemworld_butcher actor={} scope={} radius={} removed={} ok=true",
                actor.uuid(),
                selection.scope(),
                selection.radius(),
                removed);
    }

    @Override
    public void killedAll(PlayerRef actor, PurgeSelection selection, int removed) {
        if (!config.auditEnabled("killall")) {
            return;
        }
        audit.info(
                "event=itemworld_killall actor={} type={} removed={} ok=true",
                actor.uuid(),
                selection.typeId().orElse("all"),
                removed);
    }

    @Override
    public void removed(PlayerRef actor, PurgeSelection selection, int removed) {
        if (!config.auditEnabled("remove")) {
            return;
        }
        audit.info(
                "event=itemworld_remove actor={} type={} radius={} removed={} ok=true",
                actor.uuid(),
                selection.typeId().orElse("all"),
                selection.radius(),
                removed);
    }

    @Override
    public void struckLightning(PlayerRef actor, Optional<PlayerRef> target) {
        if (!config.auditEnabled("lightning")) {
            return;
        }
        audit.info(
                "event=itemworld_lightning actor={} target={} ok=true",
                actor.uuid(),
                target.map(PlayerRef::uuid).map(Object::toString).orElse("location"));
    }

    @Override
    public void launchedFireball(PlayerRef actor) {
        if (!config.auditEnabled("fireball")) {
            return;
        }
        audit.info("event=itemworld_fireball actor={} ok=true", actor.uuid());
    }

    @Override
    public void firedKittycannon(PlayerRef actor) {
        if (!config.auditEnabled("kittycannon")) {
            return;
        }
        audit.info("event=itemworld_kittycannon actor={} ok=true", actor.uuid());
    }

    @Override
    public void threwAntioch(PlayerRef actor) {
        if (!config.auditEnabled("antioch")) {
            return;
        }
        audit.info("event=itemworld_antioch actor={} ok=true", actor.uuid());
    }

    @Override
    public void firedBeezooka(PlayerRef actor) {
        if (!config.auditEnabled("beezooka")) {
            return;
        }
        audit.info("event=itemworld_beezooka actor={} ok=true", actor.uuid());
    }

    @Override
    public void brokeBlock(PlayerRef actor, String blockType) {
        if (!config.auditEnabled("break")) {
            return;
        }
        audit.info("event=itemworld_break actor={} block={} ok=true", actor.uuid(), blockType);
    }

    @Override
    public void grewTree(PlayerRef actor, String type) {
        if (!config.auditEnabled("tree")) {
            return;
        }
        audit.info("event=itemworld_tree actor={} type={} ok=true", actor.uuid(), type);
    }

    @Override
    public void nuked(PlayerRef actor, Optional<PlayerRef> target) {
        if (!config.auditEnabled("nuke")) {
            return;
        }
        audit.info(
                "event=itemworld_nuke actor={} target={} ok=true",
                actor.uuid(),
                target.map(PlayerRef::uuid).map(Object::toString).orElse("location"));
    }
}
