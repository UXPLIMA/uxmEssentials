package com.uxplima.uxmessentials.teleport.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /tprandomplayer} (alias {@code /tprp}) into the teleport context's command surface, the
 * staff verb that hops you to a random visible online player, the roulette companion of {@code /tp}. It
 * rides the shared {@code uxmessentials.tp.use} node that {@code /tp} and {@code /goto} already use; this
 * guard fails if the literal drops out of the surface or ever wires under a different permission.
 */
class TpRandomPlayerSurfaceDriftTest {

    private static CommandSpec teleportSpec(String literal) {
        FeatureModule teleport = new DefaultModuleRegistry()
                .byId(ModuleId.of("teleport"))
                .orElseThrow(() -> new AssertionError("teleport module must be registered"));
        return teleport.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("teleport surface must expose a /" + literal + " command"));
    }

    @Test
    void teleportSurfaceExposesTpRandomPlayer() {
        assertThat(teleportSpec("tprandomplayer").literal()).isEqualTo("tprandomplayer");
    }

    @Test
    void tpRandomPlayerReusesTheStaffTeleportPermission() {
        assertThat(teleportSpec("tprandomplayer").permission()).isEqualTo("uxmessentials.tp.use");
    }
}
