package com.uxplima.uxmessentials.teleport.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /tpauto} into the teleport context's command surface. {@code /tpauto} auto-accepts incoming
 * teleport requests, the toggle that sits alongside {@code /tptoggle}, {@code /tpon} and
 * {@code /tpoff}; this guard fails if the literal drops out of the surface or wires under a node other than
 * {@code uxmessentials.tpa.auto}.
 */
class TpAutoSurfaceDriftTest {

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
    void teleportSurfaceExposesTpAuto() {
        assertThat(teleportSpec("tpauto").literal()).isEqualTo("tpauto");
    }

    @Test
    void tpAutoWiresUnderItsOwnNode() {
        assertThat(teleportSpec("tpauto").permission()).isEqualTo("uxmessentials.tpa.auto");
    }
}
