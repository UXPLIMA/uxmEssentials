package com.uxplima.uxmessentials.persistence.staff;

import static com.uxplima.uxmessentials.persistence.jooq.tables.StaffLoadout.STAFF_LOADOUT;

import java.util.Base64;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.StaffLoadoutRecord;
import com.uxplima.uxmessentials.staff.domain.LoadoutBlob;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;
import org.jooq.Record;

/**
 * The anti-corruption mapping between a {@code staff_loadout} row and the domain {@link SavedLoadout}. The
 * scalar facts of the captured state, the held hotbar slot, the experience level and progress, the game-mode
 * name and the flight flag. Are first-class columns; the four item/effect regions are opaque payload, each
 * stored as the base64 text of the adapter's already-serialized bytes (the architecture persistence invariant,
 * docs/01-architecture.md), exactly as the vaults context stores its {@code ItemStack[]}. This class is the
 * single place that translation lives, so the row shape stays identical on every backend.
 *
 * <p>The flight flag is a {@code SMALLINT} carrying 0/1 rather than a {@code BOOLEAN} for portability; it
 * round-trips through {@code true == 1}.
 */
final class StaffLoadoutRows {

    private StaffLoadoutRows() {}

    /** Rebuild a {@link SavedLoadout} from a queried row. */
    static SavedLoadout toLoadout(Record row) {
        return new SavedLoadout(
                decode(row.get(STAFF_LOADOUT.INVENTORY)),
                decode(row.get(STAFF_LOADOUT.ARMOR)),
                decode(row.get(STAFF_LOADOUT.OFFHAND)),
                row.get(STAFF_LOADOUT.HELD_SLOT),
                row.get(STAFF_LOADOUT.EXP_LEVEL),
                row.get(STAFF_LOADOUT.EXP_PROGRESS),
                row.get(STAFF_LOADOUT.GAME_MODE),
                row.get(STAFF_LOADOUT.FLYING) != 0,
                row.get(STAFF_LOADOUT.ALLOW_FLIGHT) != 0,
                decode(row.get(STAFF_LOADOUT.POTION_EFFECTS)),
                row.get(STAFF_LOADOUT.VANISHED_BEFORE) != 0);
    }

    /** Populate a {@link StaffLoadoutRecord} from a domain {@link SavedLoadout} for an upsert. */
    static void apply(StaffLoadoutRecord record, String owner, String serverId, SavedLoadout loadout, long enteredAt) {
        record.setPlayer(owner)
                .setServerId(serverId)
                .setInventory(encode(loadout.inventory()))
                .setArmor(encode(loadout.armor()))
                .setOffhand(encode(loadout.offhand()))
                .setPotionEffects(encode(loadout.potionEffects()))
                .setHeldSlot(loadout.heldSlot())
                .setExpLevel(loadout.expLevel())
                .setExpProgress(loadout.expProgress())
                .setGameMode(loadout.gameMode())
                .setFlying((short) (loadout.flying() ? 1 : 0))
                .setAllowFlight((short) (loadout.allowFlight() ? 1 : 0))
                .setVanishedBefore((short) (loadout.vanishedBefore() ? 1 : 0))
                .setEnteredAt(enteredAt);
    }

    /** Base64 an opaque region's bytes for its TEXT column; an empty region encodes to the empty string. */
    private static String encode(LoadoutBlob region) {
        return Base64.getEncoder().encodeToString(region.bytes());
    }

    /** Decode a stored base64 cell back into an opaque region; a blank cell is an empty region. */
    private static LoadoutBlob decode(String cell) {
        if (cell.isBlank()) {
            return LoadoutBlob.empty();
        }
        return LoadoutBlob.of(Base64.getDecoder().decode(cell));
    }
}
