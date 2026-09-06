package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /commandspy} into the moderation context's command surface. The staff toggle that mirrors
 * {@code /socialspy} but watches commands rather than private messages. This guard fails if the literal
 * drops out of the surface or ever wires under a node other than {@code uxmessentials.moderation.commandspy}.
 */
class CommandSpySurfaceDriftTest {

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
    void moderationSurfaceExposesCommandSpy() {
        assertThat(moderationSpec("commandspy").literal()).isEqualTo("commandspy");
    }

    @Test
    void commandSpyWiresUnderItsOwnNode() {
        assertThat(moderationSpec("commandspy").permission()).isEqualTo("uxmessentials.moderation.commandspy");
    }
}
