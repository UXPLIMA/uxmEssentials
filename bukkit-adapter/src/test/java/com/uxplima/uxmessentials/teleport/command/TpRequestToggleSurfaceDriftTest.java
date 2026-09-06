package com.uxplima.uxmessentials.teleport.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins the explicit on/off teleport-request verbs, {@code /tpon} and {@code /tpoff}, into the teleport
 * context's command surface. These are the idempotent on/off verbs that sit alongside
 * the flip-style {@code /tptoggle}; this guard fails if either drops out of the surface or wires under a
 * permission node other than the shared {@code uxmessentials.tpa.toggle} {@code /tptoggle} already uses,
 * which would otherwise drift the permissions reference and the permission catalogue.
 */
class TpRequestToggleSurfaceDriftTest {

    private static CommandSpec toggleSpec(String literal) {
        FeatureModule teleport = new DefaultModuleRegistry()
                .byId(ModuleId.of("teleport"))
                .orElseThrow(() -> new AssertionError("teleport module must be registered"));
        return teleport.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("teleport surface must expose a /" + literal + " command"));
    }

    @Test
    void teleportSurfaceExposesTpon() {
        assertThat(toggleSpec("tpon").literal()).isEqualTo("tpon");
    }

    @Test
    void teleportSurfaceExposesTpoff() {
        assertThat(toggleSpec("tpoff").literal()).isEqualTo("tpoff");
    }

    @Test
    void tponReusesTheTptogglePermission() {
        assertThat(toggleSpec("tpon").permission()).isEqualTo("uxmessentials.tpa.toggle");
    }

    @Test
    void tpoffReusesTheTptogglePermission() {
        assertThat(toggleSpec("tpoff").permission()).isEqualTo("uxmessentials.tpa.toggle");
    }
}
