package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The hand-wired registry every feature module's management GUI plugs into. A module, in its own wiring,
 * registers one {@link ManagementGuiEntry} (its label, icon, permission node, and an opener) and the
 * {@code /uxmess gui} hub draws each registered entry the viewer is permitted as a clickable icon.
 *
 * <p>This is a single instance constructed in {@code bootstrap/PluginModule} and passed to each module's
 * wiring (the DI rule. No statics, constructor injection); a disabled module simply never registers, so
 * an empty registry is the valid first state. Registration order is preserved so the hub's icon order is
 * deterministic; a second registration under the same {@link ManagementGuiEntry#id()} replaces the first,
 * which keeps a module's re-wire on reload idempotent.
 *
 * <p>Ownership: the backing map is mutated only during bootstrap wiring (single-threaded) and read
 * thereafter when the hub opens; the defensive copy {@link #entries()} returns guards a reader against a
 * late registration without locking on the read path.
 */
@NullMarked
public final class ManagementGuiRegistry {

    private final Map<String, ManagementGuiEntry> entries = new LinkedHashMap<>();

    /** Register (or replace, by id) a module's hub entry. Called from the module's wiring at bootstrap. */
    public void register(ManagementGuiEntry entry) {
        Objects.requireNonNull(entry, "entry");
        entries.put(entry.id(), entry);
    }

    /** Every registered entry, in registration order; a defensive copy safe to iterate. */
    public List<ManagementGuiEntry> entries() {
        return List.copyOf(entries.values());
    }

    /** The entries {@code viewer} is permitted to see and open, in registration order. */
    public List<ManagementGuiEntry> entriesFor(PlayerRef viewer, Permissions permissions) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(permissions, "permissions");
        List<ManagementGuiEntry> permitted = new ArrayList<>();
        for (ManagementGuiEntry entry : entries.values()) {
            if (permissions.has(viewer, entry.permission())) {
                permitted.add(entry);
            }
        }
        return List.copyOf(permitted);
    }
}
