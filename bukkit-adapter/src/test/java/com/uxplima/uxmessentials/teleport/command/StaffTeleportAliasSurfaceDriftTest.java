package com.uxplima.uxmessentials.teleport.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins the staff teleport verbs. {@code /goto} (move you to the target, mirrors {@code /tp})
 * and {@code /bring} (pull the target to you, mirrors {@code /tphere}). Into the teleport context's command
 * surface. Both ride the shared {@code uxmessentials.tp.use} node that {@code /tp} and {@code /tphere}
 * already use; this guard fails if either drops out of the surface or ever wires under a different
 * permission, which would otherwise drift the permissions reference and the permission catalogue.
 */
class StaffTeleportAliasSurfaceDriftTest {

    private static CommandSpec staffSpec(String literal) {
        FeatureModule teleport = new DefaultModuleRegistry()
                .byId(ModuleId.of("teleport"))
                .orElseThrow(() -> new AssertionError("teleport module must be registered"));
        return teleport.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("teleport surface must expose a /" + literal + " command"));
    }

    @Test
    void teleportSurfaceExposesGoto() {
        assertThat(staffSpec("goto").literal()).isEqualTo("goto");
    }

    @Test
    void teleportSurfaceExposesBring() {
        assertThat(staffSpec("bring").literal()).isEqualTo("bring");
    }

    @Test
    void gotoAndBringReuseTheStaffTeleportPermission() {
        assertThat(staffSpec("goto").permission()).isEqualTo("uxmessentials.tp.use");
        assertThat(staffSpec("bring").permission()).isEqualTo("uxmessentials.tp.use");
    }
}
