package com.uxplima.uxmessentials.teleport.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins the vertical teleport family, {@code /down} plus the {@code /ascend},
 * {@code /descend} and {@code /thru} verbs: into the teleport context's command surface. These
 * ship together; this guard fails if any of them ever drops out of the surface or wires under a
 * permission node other than the shared {@code uxmessentials.tp.vertical} the rest of the vertical verbs
 * already use, which would otherwise drift the permissions reference and the permission catalogue.
 */
class VerticalSurfaceDriftTest {

    private static CommandSpec verticalSpec(String literal) {
        FeatureModule teleport = new DefaultModuleRegistry()
                .byId(ModuleId.of("teleport"))
                .orElseThrow(() -> new AssertionError("teleport module must be registered"));
        return teleport.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("teleport surface must expose a /" + literal + " command"));
    }

    @Test
    void teleportSurfaceExposesDown() {
        assertThat(verticalSpec("down").literal()).isEqualTo("down");
    }

    @Test
    void downReusesTheSharedVerticalPermission() {
        assertThat(verticalSpec("down").permission()).isEqualTo("uxmessentials.tp.vertical");
    }

    @Test
    void teleportSurfaceExposesAscend() {
        assertThat(verticalSpec("ascend").literal()).isEqualTo("ascend");
    }

    @Test
    void teleportSurfaceExposesDescend() {
        assertThat(verticalSpec("descend").literal()).isEqualTo("descend");
    }

    @Test
    void ascendDescendReuseTheSharedVerticalPermission() {
        assertThat(verticalSpec("ascend").permission()).isEqualTo("uxmessentials.tp.vertical");
        assertThat(verticalSpec("descend").permission()).isEqualTo("uxmessentials.tp.vertical");
    }

    @Test
    void teleportSurfaceExposesThru() {
        assertThat(verticalSpec("thru").literal()).isEqualTo("thru");
    }

    @Test
    void thruReusesTheSharedVerticalPermission() {
        assertThat(verticalSpec("thru").permission()).isEqualTo("uxmessentials.tp.vertical");
    }
}
