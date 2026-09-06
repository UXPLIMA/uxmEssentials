package com.uxplima.uxmessentials.itemworld.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins the two grenade-style admin-fun verbs. {@code /antioch} (alias {@code /grenade}) and {@code /beezooka}
 * (alias {@code /beecannon}), into the itemworld command surface, the way {@code BigTreeSurfaceDriftTest}
 * pins {@code /bigtree}. Each is the abusable, audit-logged sibling of {@code /fireball} and {@code
 * /kittycannon}; this guard fails if either drops out of the surface or wires under a node other than its own
 * {@code uxmessentials.<verb>.use}, which would drift the permissions reference and the permission catalogue.
 */
class AdminFunGrenadeSurfaceDriftTest {

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
    void itemworldSurfaceExposesAntioch() {
        assertThat(itemworldSpec("antioch").permission()).isEqualTo("uxmessentials.antioch.use");
    }

    @Test
    void itemworldSurfaceExposesBeezooka() {
        assertThat(itemworldSpec("beezooka").permission()).isEqualTo("uxmessentials.beezooka.use");
    }
}
