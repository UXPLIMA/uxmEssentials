package com.uxplima.uxmessentials.playerwarps.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins each player-warps command literal to its base permission node. The three self-service commands are the
 * whole top-level surface; this guard fails if a literal drops out or wires under a node other than its
 * documented one, keeping the kernel surface in lockstep with the permission catalogue. The
 * {@code visibility public|private <name>} toggles and {@code del <name>} are subcommands of {@code /pwarp}
 * (gated by {@code uxmessentials.pwarp.public} and {@code uxmessentials.pwarp.delete}) rather than command
 * literals, so they are not part of this table. {@code del} folded into {@code /pwarp} the way {@code /warp del}
 * sits under {@code /warp}.
 */
class PlayerWarpSurfaceDriftTest {

    private static CommandSpec spec(String literal) {
        FeatureModule playerwarps = new DefaultModuleRegistry()
                .byId(ModuleId.of("playerwarps"))
                .orElseThrow(() -> new AssertionError("playerwarps module must be registered"));
        return playerwarps.commands().stream()
                .filter(s -> s.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("playerwarps surface must expose a /" + literal + " command"));
    }

    @Test
    void pwarpWiresUnderItsUseNode() {
        assertThat(spec("pwarp").permission()).isEqualTo("uxmessentials.pwarp.use");
    }

    @Test
    void setPwarpWiresUnderItsSetNode() {
        assertThat(spec("setpwarp").permission()).isEqualTo("uxmessentials.pwarp.set");
    }

    @Test
    void pwarpDeletionIsFoldedIntoPwarpNotAStandaloneLiteral() {
        FeatureModule playerwarps = new DefaultModuleRegistry()
                .byId(ModuleId.of("playerwarps"))
                .orElseThrow(() -> new AssertionError("playerwarps module must be registered"));
        assertThat(playerwarps.commands().stream().map(CommandSpec::literal)).doesNotContain("delpwarp");
    }

    @Test
    void pwarpsWiresUnderItsListNode() {
        assertThat(spec("pwarps").permission()).isEqualTo("uxmessentials.pwarp.list");
    }
}
