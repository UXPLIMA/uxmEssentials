package com.uxplima.uxmessentials.teleport.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /settpr} into the teleport context's command surface, the admin command that sets the
 * {@code /rtp} search zone at runtime by swapping the live pre-warmed queue's radii. This guard fails if the
 * literal drops out of the surface or ever wires under a node other than its own
 * {@code uxmessentials.teleport.settpr}, which would otherwise drift the permissions reference and
 * the permission catalogue.
 */
class SetTprSurfaceDriftTest {

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
    void teleportSurfaceExposesSetTpr() {
        assertThat(teleportSpec("settpr").literal()).isEqualTo("settpr");
    }

    @Test
    void setTprWiresUnderItsOwnNode() {
        assertThat(teleportSpec("settpr").permission()).isEqualTo("uxmessentials.teleport.settpr");
    }
}
