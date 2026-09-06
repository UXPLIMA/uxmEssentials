package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /alts} into the moderation context's command surface. The alt-detection read that lists the
 * accounts sharing a target's last IP, the list companion of {@code /seenip}. It rides the shared
 * {@code uxmessentials.moderation.seen} node that {@code /seen} and {@code /seenip} already use; this guard
 * fails if the literal drops out of the surface or ever wires under a different permission.
 */
class AltsSurfaceDriftTest {

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
    void moderationSurfaceExposesAlts() {
        assertThat(moderationSpec("alts").literal()).isEqualTo("alts");
    }

    @Test
    void altsReusesTheSeenPermission() {
        assertThat(moderationSpec("alts").permission()).isEqualTo("uxmessentials.moderation.seen");
    }
}
