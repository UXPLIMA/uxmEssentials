package com.uxplima.uxmessentials.itemworld.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /entitycount} into the itemworld context's command surface. There is no command to tally nearby
 * entities by type ({@code /near} lists players, the purge family deletes them) yet admins use a count for
 * lag diagnosis before {@code /butcher} or {@code /killall}. This guard fails if the literal drops out of the
 * surface or wires under a node other than {@code uxmessentials.entitycount.use}.
 */
class EntityCountSurfaceDriftTest {

    private static CommandSpec itemworldSpec(String literal) {
        FeatureModule itemworld = new DefaultModuleRegistry()
                .byId(ModuleId.of("itemworld"))
                .orElseThrow(() -> new AssertionError("itemworld module must be registered"));
        return itemworld.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("itemworld surface must expose a /" + literal + " command"));
    }

    @Test
    void itemworldSurfaceExposesEntityCount() {
        assertThat(itemworldSpec("entitycount").literal()).isEqualTo("entitycount");
    }

    @Test
    void entityCountWiresUnderItsOwnPermission() {
        assertThat(itemworldSpec("entitycount").permission()).isEqualTo("uxmessentials.entitycount.use");
    }
}
