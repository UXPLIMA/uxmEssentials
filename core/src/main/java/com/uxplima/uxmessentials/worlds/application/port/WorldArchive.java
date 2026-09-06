package com.uxplima.uxmessentials.worlds.application.port;

import java.util.List;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.domain.BackupId;
import com.uxplima.uxmessentials.worlds.domain.BackupRef;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/**
 * Outbound port over the on-disk backup store for a world: the snapshot zips kept under the backups
 * directory. The only place in the worlds context that performs the long-running archive I/O, every
 * method is the adapter's responsibility, dispatched off-tick through the {@code Scheduler} port.
 */
public interface WorldArchive {

    /**
     * Kick the asynchronous snapshot zip of {@code world} and return the new backup's id immediately.
     * The {@code initiator} is carried so the adapter, on completion, can notify the right operator
     * the {@code WORLD_BACKUP_CREATED} / {@code WORLD_BACKUP_FAILED} message is the adapter's, fired
     * when the off-tick zip finishes, not here.
     */
    Result<BackupId, WorldError> backup(PlayerRef initiator, WorldName world);

    /** The backups recorded for {@code world}, newest-first; empty when none exist. */
    List<BackupRef> list(WorldName world);

    /**
     * Orchestrate restoring {@code world} from backup {@code id}: evacuate any players, replace the
     * world folder from the archive, and reload the world. The {@code WORLD_RESTORED} /
     * {@code WORLD_RESTORE_FAILED} completion notification to {@code initiator} is the adapter's.
     */
    Result<Unit, WorldError> restore(PlayerRef initiator, WorldName world, BackupId id);
}
