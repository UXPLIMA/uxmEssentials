package com.uxplima.uxmessentials.playerstate.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /clearinventoryconfirmtoggle} into the playerstate context's command surface, the per-player
 * toggle that makes a self {@code /clearinventory} ask for a second confirmation before it clears. This guard
 * fails if the literal drops out of the surface or ever wires under a node other than its own
 * {@code uxmessentials.clearinventory.confirmtoggle}, which would otherwise drift the permissions reference and
 * the permission catalogue.
 */
class ClearInventoryConfirmToggleSurfaceDriftTest {

    private static CommandSpec playerstateSpec(String literal) {
        FeatureModule playerstate = new DefaultModuleRegistry()
                .byId(ModuleId.of("playerstate"))
                .orElseThrow(() -> new AssertionError("playerstate module must be registered"));
        return playerstate.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("playerstate surface must expose a /" + literal + " command"));
    }

    @Test
    void playerstateSurfaceExposesClearInventoryConfirmToggle() {
        assertThat(playerstateSpec("clearinventoryconfirmtoggle").literal()).isEqualTo("clearinventoryconfirmtoggle");
    }

    @Test
    void clearInventoryConfirmToggleWiresUnderItsOwnNode() {
        assertThat(playerstateSpec("clearinventoryconfirmtoggle").permission())
                .isEqualTo("uxmessentials.clearinventory.confirmtoggle");
    }
}
