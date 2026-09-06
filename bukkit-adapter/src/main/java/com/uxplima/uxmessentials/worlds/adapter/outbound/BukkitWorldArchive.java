package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.WorldTeleportService;
import com.uxplima.uxmessentials.worlds.application.WorldsMessageKey;
import com.uxplima.uxmessentials.worlds.application.WorldsSettings;
import com.uxplima.uxmessentials.worlds.application.port.WorldArchive;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.BackupId;
import com.uxplima.uxmessentials.worlds.domain.BackupRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link WorldArchive} adapter: orchestrates a world's backup and restore across the tick boundary.
 *
 * <p>A backup runs zip → prune: it copies the on-disk world folder (kept current by Paper/Folia's periodic
 * auto-save) rather than forcing a synchronous {@code World#save()}, because a full-world save has no single
 * owning thread on Folia. Each region of a world is owned by a different region thread, so flushing it from
 * the command's {@code onGlobal} hop is unsafe. The long-running zip and the prune of older archives run
 * off-tick through the {@code Scheduler}'s async context, and the completion notification bounces back onto
 * the operator's entity thread.
 *
 * <p>A restore is the dangerous half, it deletes the world folder, so it validates before it destroys:
 * the archive file must exist and the world must be managed (needed to reload it with its spec) <em>before</em>
 * any player is evacuated, the world is unloaded, or the folder is touched. Evacuation is asynchronous, so the
 * folder is touched only after the world is provably empty <em>and</em> unloaded: after evacuating, a bounded
 * global-thread drain polls until the world has no players left, then unloads it and checks the result; only a
 * confirmed unload hands off to the off-tick delete-tree and unzip, and the reload returns to the global thread.
 * If the drain times out, or the unload fails, or the world is somehow still loaded when the swap begins, the
 * folder is left untouched and the operator is told the restore failed. A missing archive or unmanaged world is
 * rejected up front with the folder left exactly as it was.
 */
@NullMarked
public final class BukkitWorldArchive implements WorldArchive {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    /** How many 50ms drain ticks to wait for evacuated players to leave before aborting the restore (~3s). */
    private static final int RESTORE_EVACUATE_MAX_TICKS = 60;

    private final Server server;
    private final Scheduler scheduler;
    private final WorldEngine engine;
    private final WorldRepository repository;
    private final WorldArchiver archiver;
    private final WorldTeleportService teleporter;
    private final ForcedWorldEntryMarker marker;
    private final WorldsSettings settings;
    private final Notifier notifier;
    private final Logger log;
    private final Path dataFolder;

    public BukkitWorldArchive(
            Server server,
            Scheduler scheduler,
            WorldEngine engine,
            WorldRepository repository,
            WorldArchiver archiver,
            WorldTeleportService teleporter,
            ForcedWorldEntryMarker marker,
            WorldsSettings settings,
            Notifier notifier,
            Logger log,
            Path dataFolder) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.archiver = Objects.requireNonNull(archiver, "archiver");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.marker = Objects.requireNonNull(marker, "marker");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.log = Objects.requireNonNull(log, "log");
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
    }

    @Override
    public Result<BackupId, WorldError> backup(PlayerRef initiator, WorldName world) {
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(world, "world");
        BackupId id = new BackupId(STAMP.format(Instant.now()));
        // The backup copies the on-disk world folder, which Paper/Folia keeps current through its periodic
        // auto-save. We deliberately do NOT call World#save() here: a full-world save has no single owning thread
        // on Folia (each of a world's regions is owned by a different region thread), so flushing it from this
        // onGlobal hop is unsafe. Relying on the auto-saved folder keeps the backup Folia-safe; the small window
        // of unsaved chunks is the same staleness any on-disk copy of a live world carries.
        scheduler.async(() -> doBackup(initiator, world, id));
        return Result.ok(id);
    }

    /** Off-tick: zip the world folder, prune older archives, and notify the initiator of the outcome. */
    private void doBackup(PlayerRef initiator, WorldName world, BackupId id) {
        try {
            archiver.zip(worldFolder(world), archiveFile(world, id));
            prune(world);
            notify(
                    initiator,
                    WorldsMessageKey.WORLD_BACKUP_CREATED,
                    Map.of("world", world.value(), "backup", id.value()));
        } catch (IOException e) {
            log.error("backup of " + world.value() + " failed", e);
            notify(initiator, WorldsMessageKey.WORLD_BACKUP_FAILED, Map.of("world", world.value()));
        }
    }

    @Override
    public List<BackupRef> list(WorldName world) {
        Objects.requireNonNull(world, "world");
        Path dir = backupsDir(world);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".zip"))
                    .map(this::toRef)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(BackupRef::createdAt).reversed())
                    .toList();
        } catch (IOException e) {
            log.error("listing backups of " + world.value() + " failed", e);
            return List.of();
        }
    }

    /** Map a backup archive path to a {@link BackupRef}, skipping a file whose stem is not a valid id. */
    private Optional<BackupRef> toRef(Path archive) {
        String fileName = archive.getFileName().toString();
        String stem = fileName.substring(0, fileName.length() - ".zip".length());
        try {
            BackupId id = new BackupId(stem);
            Instant createdAt = Files.getLastModifiedTime(archive).toInstant();
            return Optional.of(new BackupRef(id, createdAt, Files.size(archive)));
        } catch (IllegalArgumentException e) {
            return Optional.empty(); // a malformed file name is not one of our archives
        } catch (IOException e) {
            log.error("reading backup metadata of " + archive + " failed", e);
            return Optional.empty();
        }
    }

    /** Delete the oldest archives beyond the retention count; a prune failure never fails the backup. */
    private void prune(WorldName world) {
        List<BackupRef> all = list(world);
        int keep = settings.backupRetentionCount();
        if (all.size() <= keep) {
            return;
        }
        for (BackupRef ref : all.subList(keep, all.size())) { // list is newest-first, so this is the oldest
            try {
                Files.deleteIfExists(archiveFile(world, ref.id()));
            } catch (IOException e) {
                log.error("pruning backup " + ref.id().value() + " of " + world.value() + " failed", e);
            }
        }
    }

    @Override
    public Result<Unit, WorldError> restore(PlayerRef initiator, WorldName world, BackupId id) {
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(id, "id");
        Path archive = archiveFile(world, id);
        if (!Files.isRegularFile(archive)) {
            notify(initiator, WorldsMessageKey.WORLD_BACKUP_NOT_FOUND, Map.of("backup", id.value()));
            return Result.err(WorldError.BACKUP_NOT_FOUND); // validate before we destroy anything
        }
        Optional<ManagedWorld> managed = repository.find(world);
        if (managed.isEmpty()) {
            notify(initiator, WorldsMessageKey.WORLD_NOT_FOUND, Map.of("world", world.value()));
            return Result.err(WorldError.NOT_FOUND); // need the spec to reload the world afterwards
        }
        World w = server.getWorld(world.value());
        if (w != null) {
            evacuate(initiator, w);
        }
        ManagedWorld spec = managed.get();
        AtomicReference<AutoCloseable> handle = new AtomicReference<>();
        AtomicInteger attempts = new AtomicInteger();
        handle.set(scheduler.repeatGlobal(
                () -> drainTick(initiator, world, id, archive, spec, handle, attempts),
                Duration.ZERO,
                Duration.ofMillis(50)));
        return Result.ok();
    }

    /** Region thread: send every player in {@code w} to the default world, marking each as a forced entry. */
    private void evacuate(PlayerRef initiator, World w) {
        engine.defaultWorldName().ifPresent(def -> {
            for (Player p : List.copyOf(w.getPlayers())) {
                marker.mark(p.getUniqueId());
                teleporter.forced(initiator, BukkitRefs.toRef(p), def);
            }
        });
    }

    /**
     * Global-thread drain tick: wait for the evacuated world to empty, then unload it before any file is touched.
     *
     * <p>Evacuation is asynchronous ({@code teleportAsync}), so the players are still present for a few ticks after
     * {@link #restore} returns. This runs every 50ms and only proceeds once the world reports no players: it unloads,
     * stops itself, and, only if the unload succeeds, hands the folder swap off-tick. A vanished world short-circuits
     * to the swap; a world that never empties within {@link #RESTORE_EVACUATE_MAX_TICKS} is aborted with the folder
     * left intact, because deleting a still-loaded world's files corrupts the live server.
     */
    private void drainTick(
            PlayerRef initiator,
            WorldName world,
            BackupId id,
            Path archive,
            ManagedWorld managed,
            AtomicReference<AutoCloseable> handle,
            AtomicInteger attempts) {
        World live = server.getWorld(world.value());
        if (live == null) {
            closeHandle(handle); // already gone. Nothing to evacuate or unload
            scheduler.async(() -> swapAndReload(initiator, world, id, archive, managed));
            return;
        }
        if (live.getPlayers().isEmpty()) {
            Result<Unit, WorldError> unloaded = engine.unload(world, false); // checked, replacing the folder
            closeHandle(handle);
            if (unloaded.isErr()) {
                notify(initiator, WorldsMessageKey.WORLD_RESTORE_FAILED, Map.of("world", world.value()));
                return;
            }
            scheduler.async(() -> swapAndReload(initiator, world, id, archive, managed));
            return;
        }
        if (attempts.incrementAndGet() >= RESTORE_EVACUATE_MAX_TICKS) {
            closeHandle(handle);
            log.warn("restore of {} aborted: players did not leave in time", world.value());
            notify(initiator, WorldsMessageKey.WORLD_RESTORE_FAILED, Map.of("world", world.value()));
        }
    }

    /** Stop the drain loop, clearing the handle so a later tick cannot double-close it; log a close failure. */
    private void closeHandle(AtomicReference<AutoCloseable> handle) {
        AutoCloseable h = handle.getAndSet(null);
        if (h != null) {
            try {
                h.close();
            } catch (Exception e) {
                log.error("failed to stop restore drain", e);
            }
        }
    }

    /** Off-tick: replace the world folder from the archive, then reload the world back on the global thread. */
    private void swapAndReload(PlayerRef initiator, WorldName world, BackupId id, Path archive, ManagedWorld managed) {
        try {
            if (server.getWorld(world.value()) != null) {
                log.warn("restore aborted: world {} still loaded", world.value());
                notify(initiator, WorldsMessageKey.WORLD_RESTORE_FAILED, Map.of("world", world.value()));
                return; // never delete a loaded world's files
            }
            archiver.deleteTree(worldFolder(world));
            archiver.unzip(archive, worldFolder(world));
            scheduler.onGlobal(() -> finishRestore(initiator, world, id, managed));
        } catch (IOException e) {
            log.error("restore of " + world.value() + " failed", e);
            notify(initiator, WorldsMessageKey.WORLD_RESTORE_FAILED, Map.of("world", world.value()));
        }
    }

    /** Global thread: load the restored world with its spec and notify the initiator it is back. */
    private void finishRestore(PlayerRef initiator, WorldName world, BackupId id, ManagedWorld managed) {
        engine.load(managed);
        notify(initiator, WorldsMessageKey.WORLD_RESTORED, Map.of("world", world.value(), "backup", id.value()));
    }

    /** Folia-safe notify: deliver the completion message on the operator's own entity thread. */
    private void notify(PlayerRef ref, MessageKey key, Map<String, String> placeholders) {
        scheduler.onEntity(ref, () -> notifier.send(ref, key, placeholders));
    }

    private Path worldFolder(WorldName name) {
        return server.getWorldContainer().toPath().resolve(name.value());
    }

    private Path backupsDir(WorldName name) {
        return dataFolder.resolve(settings.backupDirectory()).resolve(name.value());
    }

    private Path archiveFile(WorldName world, BackupId id) {
        return backupsDir(world).resolve(id.value() + ".zip");
    }
}
