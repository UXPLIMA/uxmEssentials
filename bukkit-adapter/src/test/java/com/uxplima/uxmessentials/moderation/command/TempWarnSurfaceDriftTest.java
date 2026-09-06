package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /tempwarn} into the moderation context's command surface. It is the timed sibling of
 * {@code /warn}, a warning that lapses on its own, and like {@code /warns} and {@code /unwarn} it shares the
 * {@code uxmessentials.moderation.warn} node. This guard fails if the literal drops out of the surface or
 * wires under a different node.
 */
class TempWarnSurfaceDriftTest {

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
    void moderationSurfaceExposesTempWarn() {
        assertThat(moderationSpec("tempwarn").literal()).isEqualTo("tempwarn");
    }

    @Test
    void tempWarnSharesTheWarnNode() {
        assertThat(moderationSpec("tempwarn").permission()).isEqualTo("uxmessentials.moderation.warn");
    }
}
