package com.uxplima.uxmessentials.worlds.adapter.inbound.listener;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.adapter.outbound.ForcedWorldEntryMarker;
import com.uxplima.uxmessentials.worlds.application.WorldAccessPolicy;
import com.uxplima.uxmessentials.worlds.application.WorldTeleportService;
import com.uxplima.uxmessentials.worlds.application.WorldsMessageKey;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.AccessDecision;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.event.WorldEntryDenied;
import org.jspecify.annotations.NullMarked;

/**
 * Enforces {@link WorldAccessPolicy} on every cross-world entry vector, not just the {@code /world} enter
 * command. A cancellable {@link PlayerTeleportEvent} covers {@code /tp}, {@code /back}, ender pearls, and any
 * plugin teleport; the {@link PlayerJoinEvent} catches a player logging into a world that has since become
 * restricted, redirecting them to the default world's spawn when configured to.
 */
@NullMarked
public final class WorldAccessListener implements Listener {

    private final WorldRepository repository;
    private final WorldAccessPolicy policy;
    private final WorldTeleportService teleportService;
    private final WorldEngine engine;
    private final DomainEventPublisher events;
    private final Scheduler scheduler;
    private final Notifier notifier;
    private final ForcedWorldEntryMarker forcedEntries;
    private final boolean redirectOnRestrictedJoin;

    public WorldAccessListener(
            WorldRepository repository,
            WorldAccessPolicy policy,
            WorldTeleportService teleportService,
            WorldEngine engine,
            DomainEventPublisher events,
            Scheduler scheduler,
            Notifier notifier,
            ForcedWorldEntryMarker forcedEntries,
            boolean redirectOnRestrictedJoin) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.teleportService = Objects.requireNonNull(teleportService, "teleportService");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.events = Objects.requireNonNull(events, "events");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.forcedEntries = Objects.requireNonNull(forcedEntries, "forcedEntries");
        this.redirectOnRestrictedJoin = redirectOnRestrictedJoin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (forcedEntries.consume(event.getPlayer().getUniqueId())) {
            return; // a worlds-initiated (staff /worlds tp or login redirect) hand-off: already adjudicated
        }
        Location to = event.getTo();
        if (to == null || event.getFrom().getWorld().equals(to.getWorld())) {
            return;
        }
        Optional<ManagedWorld> found = managedFor(to.getWorld().getName());
        if (found.isEmpty() || unrestricted(found.get())) {
            return;
        }
        ManagedWorld world = found.get();
        PlayerRef who = BukkitRefs.toRef(event.getPlayer());
        AccessDecision decision = policy.decide(who, world);
        if (!decision.allowed()) {
            event.setCancelled(true);
            events.publish(new WorldEntryDenied(world.name(), who, decision));
            notifyDenied(who, world, decision);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!redirectOnRestrictedJoin) {
            return;
        }
        Optional<ManagedWorld> found = managedFor(event.getPlayer().getWorld().getName());
        if (found.isEmpty() || unrestricted(found.get())) {
            return;
        }
        PlayerRef who = BukkitRefs.toRef(event.getPlayer());
        if (policy.decide(who, found.get()).allowed()) {
            return;
        }
        engine.defaultWorldName()
                .ifPresent(def -> scheduler.onEntity(who, () -> teleportService.forced(who, who, def)));
    }

    /** The managed world registered under {@code worldName}, or empty when unparseable or unmanaged. */
    private Optional<ManagedWorld> managedFor(String worldName) {
        WorldName name;
        try {
            name = WorldName.of(worldName);
        } catch (IllegalArgumentException unusableName) {
            return Optional.empty();
        }
        return repository.find(name);
    }

    /** A world neither access-restricted nor player-limited: no entry decision is needed. */
    private static boolean unrestricted(ManagedWorld world) {
        var settings = world.settings();
        return !settings.get(WorldProperties.ACCESS_RESTRICTED) && settings.get(WorldProperties.PLAYER_LIMIT) == 0;
    }

    private void notifyDenied(PlayerRef who, ManagedWorld world, AccessDecision decision) {
        String worldName = world.name().value();
        MessageKey key;
        Map<String, String> placeholders;
        if (decision == AccessDecision.DENIED_FULL) {
            key = WorldsMessageKey.WORLD_ENTER_DENIED_FULL;
            int limit = world.settings().get(WorldProperties.PLAYER_LIMIT);
            placeholders = Map.of("world", worldName, "limit", String.valueOf(limit));
        } else {
            key = WorldsMessageKey.WORLD_ENTER_DENIED_PERMISSION;
            placeholders = Map.of("world", worldName);
        }
        scheduler.onEntity(who, () -> notifier.send(who, key, placeholders));
    }
}
