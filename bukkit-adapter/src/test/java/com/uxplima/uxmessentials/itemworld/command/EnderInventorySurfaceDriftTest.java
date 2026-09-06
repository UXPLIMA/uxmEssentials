package com.uxplima.uxmessentials.itemworld.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins the inventory/ender-chest staff verbs. {@code /enderclear} (alias {@code /clearec}), {@code /copyinv},
 * and {@code /endercopy}. Into the itemworld command surface. {@code /enderclear} is the ender-chest sibling
 * of {@code /clearinventory}; {@code /copyinv} and {@code /endercopy} clone a target's inventory or ender
 * chest into the caller's. This guard fails if any drops out of the surface or wires under a node other than
 * its own {@code uxmessentials.<verb>.use}, which would drift the permissions reference and the permission catalogue.
 */
class EnderInventorySurfaceDriftTest {

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
    void itemworldSurfaceExposesEnderClear() {
        assertThat(itemworldSpec("enderclear").permission()).isEqualTo("uxmessentials.enderclear.use");
    }

    @Test
    void itemworldSurfaceExposesCopyInv() {
        assertThat(itemworldSpec("copyinv").permission()).isEqualTo("uxmessentials.copyinv.use");
    }

    @Test
    void itemworldSurfaceExposesEnderCopy() {
        assertThat(itemworldSpec("endercopy").permission()).isEqualTo("uxmessentials.endercopy.use");
    }
}
