package com.uxplima.uxmessentials.messaging.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /socialspy} into the messaging context's command surface. The staff toggle that observes other
 * players' private messages, now in both its global and per-player forms. Both forms ride the one literal and
 * the one node, so the surface still declares a single {@code socialspy} spec; this guard fails if the literal
 * drops out of the surface or ever wires under a node other than {@code uxmessentials.msg.socialspy}.
 */
class SocialSpySurfaceDriftTest {

    private static CommandSpec messagingSpec(String literal) {
        FeatureModule messaging = new DefaultModuleRegistry()
                .byId(ModuleId.of("messaging"))
                .orElseThrow(() -> new AssertionError("messaging module must be registered"));
        return messaging.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("messaging surface must expose a /" + literal + " command"));
    }

    @Test
    void messagingSurfaceExposesSocialSpy() {
        assertThat(messagingSpec("socialspy").literal()).isEqualTo("socialspy");
    }

    @Test
    void socialSpyWiresUnderItsOwnNode() {
        assertThat(messagingSpec("socialspy").permission()).isEqualTo("uxmessentials.msg.socialspy");
    }
}
