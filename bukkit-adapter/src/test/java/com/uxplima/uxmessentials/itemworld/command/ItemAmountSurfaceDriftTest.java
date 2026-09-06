package com.uxplima.uxmessentials.itemworld.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /itemamount} (alias {@code /amount}) into the itemworld context's command surface, the held-item
 * editor that sets a stack's amount, clamped to the give cap, the companion of {@code /more}. This guard fails
 * if the literal drops out of the surface or wires under a node other than {@code uxmessentials.itemamount.use}.
 */
class ItemAmountSurfaceDriftTest {

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
    void itemworldSurfaceExposesItemAmount() {
        assertThat(itemworldSpec("itemamount").literal()).isEqualTo("itemamount");
    }

    @Test
    void itemAmountWiresUnderItsOwnPermission() {
        assertThat(itemworldSpec("itemamount").permission()).isEqualTo("uxmessentials.itemamount.use");
    }
}
