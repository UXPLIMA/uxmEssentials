package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /lockdown} into the moderation context's command surface, the server-wide login gate that
 * refuses every join but holders of the bypass permission. This guard fails if the literal drops out of the
 * surface or ever wires under a node other than {@code uxmessentials.moderation.lockdown}.
 */
class LockdownSurfaceDriftTest {

    private static CommandSpec moderationSpec(String literal) {
        FeatureModule moderation = new DefaultModuleRegistry()
                .byId(ModuleId.of("moderation"))
                .orElseThrow(() -> new AssertionError("moderation module must be registered"));
        return moderation.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("moderation surface must expose a /" + literal + " command"));
    }

    @Test
    void moderationSurfaceExposesLockdown() {
        assertThat(moderationSpec("lockdown").literal()).isEqualTo("lockdown");
    }

    @Test
    void lockdownWiresUnderItsOwnNode() {
        assertThat(moderationSpec("lockdown").permission()).isEqualTo("uxmessentials.moderation.lockdown");
    }
}
