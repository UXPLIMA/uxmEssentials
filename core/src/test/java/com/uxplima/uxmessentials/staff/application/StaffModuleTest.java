package com.uxplima.uxmessentials.staff.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

class StaffModuleTest {

    @Test
    void identityMatchesItsContextPackage() {
        assertThat(new StaffModule().id()).isEqualTo(ModuleId.of("staff"));
        assertThat(new StaffModule().configRoot()).isEqualTo("modules.staff");
    }

    @Test
    void shipsEnabledByDefault() {
        StaffModule module = new StaffModule();

        // With no override the module is on. It ships a default gadget hotbar and every command/gadget is
        // permission-gated, so a regular player sees nothing change until granted the staff nodes.
        assertThat(module.enabled(new FixedConfig(Map.of()))).isTrue();
        // An explicit disable in modules.conf turns it off.
        assertThat(module.enabled(new FixedConfig(Map.of("modules.staff.enabled", false))))
                .isFalse();
    }

    @Test
    void publishesTheStaffModeStaffChatAndStaffListCommands() {
        List<CommandSpec> commands = new StaffModule().commands();

        assertThat(commands).extracting(CommandSpec::literal).containsExactly("staffmode", "staffchat", "stafflist");
        assertThat(commands)
                .extracting(CommandSpec::permission)
                .containsExactly("uxmessentials.staff.mode", "uxmessentials.staff.chat", "uxmessentials.staff.list");
    }

    @Test
    void persistsNothingAndRegistersNoListenersInThePureModule() {
        StaffModule module = new StaffModule();

        // The staff_loadout table ships in the persistence baseline, not a module-owned location.
        assertThat(module.migrations()).isEmpty();
        // Bukkit-facing listeners land with the adapter, not the pure module.
        assertThat(module.listeners()).isEmpty();
    }

    /** A map-backed {@link ConfigStore} for driving the enable gate. */
    private record FixedConfig(Map<String, Object> values) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }
    }
}
