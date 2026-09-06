package com.uxplima.uxmessentials.persistence.playerwarps;

import java.util.Objects;
import java.util.UUID;

/**
 * A single "your player-warp was renamed" fact the one-shot legacy migration emits when it has to move a warp off
 * a name another owner already claimed. Player-warp names became globally unique in V70, so two owners who each
 * shipped a warp named {@code base} can no longer both keep it: the first claimant keeps the name and every later
 * one is renamed ({@code base} → {@code base2}, {@code base3}, …). One notice is produced per such rename.
 *
 * <p>The migration lives in the persistence layer and must not depend on the messaging module, so it takes a
 * {@code Consumer<PlayerWarpRenameNotice>} rather than reaching for a mailbox directly. Bootstrap decides where the
 * notice goes, an operator log line today, offline mail to the owner once that seam is threaded through the
 * player-warps wiring. This record is only the data carried across that seam.
 *
 * @param owner the warp owner whose warp was renamed
 * @param oldName the name the owner knew the warp by, exactly as it stood in the legacy table
 * @param newName the globally-unique name the warp now carries after collision resolution
 */
public record PlayerWarpRenameNotice(UUID owner, String oldName, String newName) {

    public PlayerWarpRenameNotice {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(oldName, "oldName");
        Objects.requireNonNull(newName, "newName");
    }
}
