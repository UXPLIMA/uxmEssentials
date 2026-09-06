package com.uxplima.uxmessentials.economy.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /sell} into the economy context's command surface. {@code /sell} is the natural companion to
 * {@code /worth} (it converts held items into currency at their configured worth) and is a standard
 * economy verb; this guard fails if the literal drops out of the surface or wires under a node
 * other than {@code uxmessentials.economy.sell}.
 */
class SellSurfaceDriftTest {

    private static CommandSpec economySpec(String literal) {
        FeatureModule economy = new DefaultModuleRegistry()
                .byId(ModuleId.of("economy"))
                .orElseThrow(() -> new AssertionError("economy module must be registered"));
        return economy.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("economy surface must expose a /" + literal + " command"));
    }

    @Test
    void economySurfaceExposesSell() {
        assertThat(economySpec("sell").literal()).isEqualTo("sell");
    }

    @Test
    void sellWiresUnderItsOwnPermission() {
        assertThat(economySpec("sell").permission()).isEqualTo("uxmessentials.economy.sell");
    }
}
