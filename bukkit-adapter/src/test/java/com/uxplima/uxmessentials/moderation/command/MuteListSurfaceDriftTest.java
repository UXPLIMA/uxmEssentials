package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /mutelist} into the moderation context's command surface, the companion of {@code /banlist} for
 * reviewing active mutes. This guard fails if the literal drops out of the surface or wires under a node other
 * than {@code uxmessentials.moderation.mutelist}.
 */
class MuteListSurfaceDriftTest {

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
    void moderationSurfaceExposesMuteList() {
        assertThat(moderationSpec("mutelist").literal()).isEqualTo("mutelist");
    }

    @Test
    void muteListWiresUnderItsOwnNode() {
        assertThat(moderationSpec("mutelist").permission()).isEqualTo("uxmessentials.moderation.mutelist");
    }
}
