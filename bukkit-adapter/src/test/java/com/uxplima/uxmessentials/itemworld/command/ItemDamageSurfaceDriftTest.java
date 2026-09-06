package com.uxplima.uxmessentials.itemworld.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /itemdamage} (alias {@code /durability}) into the itemworld context's command surface, the
 * held-item editor that sets durability damage, the inverse of {@code /repair}. This guard fails if the
 * literal drops out of the surface or wires under a node other than {@code uxmessentials.itemdamage.use}.
 */
class ItemDamageSurfaceDriftTest {

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
    void itemworldSurfaceExposesItemDamage() {
        assertThat(itemworldSpec("itemdamage").literal()).isEqualTo("itemdamage");
    }

    @Test
    void itemDamageWiresUnderItsOwnPermission() {
        assertThat(itemworldSpec("itemdamage").permission()).isEqualTo("uxmessentials.itemdamage.use");
    }
}
