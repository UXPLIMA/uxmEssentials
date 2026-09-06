package com.uxplima.uxmessentials.communication.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /broadcastworld} into the communication context's command surface. It is the world-scoped
 * companion of {@code /broadcast} (same operator announcement, restricted to the sender's world) and wires
 * under its own {@code uxmessentials.communication.broadcastworld} node. This guard fails if the literal drops
 * out of the surface or wires under a different node.
 */
class BroadcastWorldSurfaceDriftTest {

    private static CommandSpec communicationSpec(String literal) {
        FeatureModule communication = new DefaultModuleRegistry()
                .byId(ModuleId.of("communication"))
                .orElseThrow(() -> new AssertionError("communication module must be registered"));
        return communication.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("communication surface must expose a /" + literal + " command"));
    }

    @Test
    void communicationSurfaceExposesBroadcastWorld() {
        assertThat(communicationSpec("broadcastworld").literal()).isEqualTo("broadcastworld");
    }

    @Test
    void broadcastWorldWiresUnderItsOwnNode() {
        assertThat(communicationSpec("broadcastworld").permission())
                .isEqualTo("uxmessentials.communication.broadcastworld");
    }
}
