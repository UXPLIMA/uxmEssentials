package com.uxplima.uxmessentials.staff.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.staff.adapter.outbound.StaffModeStoreImpl;
import com.uxplima.uxmessentials.staff.application.EnterStaffMode;
import com.uxplima.uxmessentials.staff.application.ExitStaffMode;
import com.uxplima.uxmessentials.staff.application.RecoverStaffLoadout;
import com.uxplima.uxmessentials.staff.application.SendStaffChat;
import com.uxplima.uxmessentials.staff.application.port.StaffInspector;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed staff use cases and the shared stores the commands and the listener hold, built once per
 * module start by {@code StaffWiring}. Held so every collaborator reads the same use cases and the same mode
 * store. The {@link EnterStaffMode}/{@link ExitStaffMode} pair owns the item-loss-safe capture/restore
 * ordering; {@link SendStaffChat} fans the staff channel; {@link StaffInspector} drives the EXAMINE gadget; the
 * {@link StaffModeStoreImpl} is the active-staff marker the listener consults on quit and on disable.
 *
 * @param enter {@code /staffmode} entering, capture, persist, then swap to the gadget hotbar
 * @param exit {@code /staffmode} leaving. Restore the real loadout, then delete the stored copy
 * @param recover finish an interrupted exit. Restore an orphaned loadout row on enter or on join
 * @param staffChat {@code /staffchat} and {@code /sc}
 * @param inspector the EXAMINE gadget's inventory-open seam
 * @param store the active-staff marker, enumerated on disable to exit everyone still in staff mode
 */
@NullMarked
public record StaffServices(
        EnterStaffMode enter,
        ExitStaffMode exit,
        RecoverStaffLoadout recover,
        SendStaffChat staffChat,
        StaffInspector inspector,
        StaffModeStoreImpl store) {

    public StaffServices {
        Objects.requireNonNull(enter, "enter");
        Objects.requireNonNull(exit, "exit");
        Objects.requireNonNull(recover, "recover");
        Objects.requireNonNull(staffChat, "staffChat");
        Objects.requireNonNull(inspector, "inspector");
        Objects.requireNonNull(store, "store");
    }
}
