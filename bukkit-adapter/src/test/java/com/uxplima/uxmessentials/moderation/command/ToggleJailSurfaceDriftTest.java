package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /togglejail} into the moderation context's command surface, the convenience wrapper that
 * releases a jailed target or jails a free one in a single command, reusing the {@code /jail} and
 * {@code /unjail} use cases. This guard fails if the literal drops out of the surface or ever wires under a
 * node other than its own {@code uxmessentials.moderation.togglejail}.
 */
class ToggleJailSurfaceDriftTest {

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
    void moderationSurfaceExposesToggleJail() {
        assertThat(moderationSpec("togglejail").literal()).isEqualTo("togglejail");
    }

    @Test
    void toggleJailWiresUnderItsOwnNode() {
        assertThat(moderationSpec("togglejail").permission()).isEqualTo("uxmessentials.moderation.togglejail");
    }
}
