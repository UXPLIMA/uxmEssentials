package com.uxplima.uxmessentials.presence.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /nick} into the presence context's command surface. {@code /nick} sets or clears a player's
 * display name, the same display name {@code /list}, {@code /realname} and the connection {@code displayname}
 * placeholder already read, and is a staple display-name feature; this guard fails if the literal drops out of
 * the surface or wires under a node other than {@code uxmessentials.nick.use}.
 */
class NickSurfaceDriftTest {

    private static CommandSpec presenceSpec(String literal) {
        FeatureModule presence = new DefaultModuleRegistry()
                .byId(ModuleId.of("presence"))
                .orElseThrow(() -> new AssertionError("presence module must be registered"));
        return presence.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("presence surface must expose a /" + literal + " command"));
    }

    @Test
    void presenceSurfaceExposesNick() {
        assertThat(presenceSpec("nick").literal()).isEqualTo("nick");
    }

    @Test
    void nickWiresUnderItsUseNode() {
        assertThat(presenceSpec("nick").permission()).isEqualTo("uxmessentials.nick.use");
    }
}
