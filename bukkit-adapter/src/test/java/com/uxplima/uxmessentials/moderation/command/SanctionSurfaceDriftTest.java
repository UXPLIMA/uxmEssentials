package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /sanction} into the moderation context's command surface. The read-only aggregator that shows a
 * player's current mute, jail and ban state plus the active warning count in one read. This guard fails if the
 * literal drops out of the surface or ever wires under a node other than its own
 * {@code uxmessentials.moderation.sanction}.
 */
class SanctionSurfaceDriftTest {

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
    void moderationSurfaceExposesSanction() {
        assertThat(moderationSpec("sanction").literal()).isEqualTo("sanction");
    }

    @Test
    void sanctionWiresUnderItsOwnNode() {
        assertThat(moderationSpec("sanction").permission()).isEqualTo("uxmessentials.moderation.sanction");
    }
}
