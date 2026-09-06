package com.uxplima.uxmessentials.shared.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CatalogBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.command.CommandId;
import com.uxplima.uxmessentials.shared.application.command.EffectiveCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The catalog binding is what turns an operator's rename/alias/disable choices into the actual tree that
 * gets registered, so it has to rewrite only the root literal and alias list while leaving the children
 * and every executor untouched: the catalog configures naming, never execution. A command with no entry
 * passes through unchanged, and a disabled one drops out before registration. MockBukkit boots Paper's
 * Brigadier so {@link Commands#literal} is wired before the nodes are built.
 */
class CatalogBindingTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void renameAndAliasRebuildsRootLiteral() {
        EffectiveCommand effective = new EffectiveCommand(new CommandId("home"), "ev", List.of("e"), true, true);
        CatalogBinding binding = new CatalogBinding(Map.of("home", effective));

        List<CommandRegistration> result = binding.apply(List.of(new StubRegistration("home")));

        assertThat(result).hasSize(1);
        CommandRegistration wrapped = result.get(0);
        LiteralCommandNode<CommandSourceStack> built = wrapped.build();
        assertThat(built.getLiteral()).isEqualTo("ev");
        assertThat(built.getChild("set")).isNotNull();
        assertThat(built.getCommand()).isNotNull();
        assertThat(wrapped.aliases()).containsExactly("e");
    }

    @Test
    void disabledCommandDroppedFromOutput() {
        EffectiveCommand effective = new EffectiveCommand(new CommandId("home"), "home", List.of(), false, true);
        CatalogBinding binding = new CatalogBinding(Map.of("home", effective));

        List<CommandRegistration> result = binding.apply(List.of(new StubRegistration("home")));

        assertThat(result).isEmpty();
    }

    @Test
    void unknownCommandIdPassesThroughUnchanged() {
        CatalogBinding binding = new CatalogBinding(Map.of());
        CommandRegistration stub = new StubRegistration("foo");

        List<CommandRegistration> result = binding.apply(List.of(stub));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(stub);
        assertThat(result.get(0).build().getLiteral()).isEqualTo("foo");
        assertThat(result.get(0).aliases()).containsExactly("h");
    }

    @Test
    void emptyAliasOverrideClearsAliases() {
        EffectiveCommand effective = new EffectiveCommand(new CommandId("home"), "home", List.of(), true, true);
        CatalogBinding binding = new CatalogBinding(Map.of("home", effective));

        List<CommandRegistration> result = binding.apply(List.of(new StubRegistration("home")));

        assertThat(result).hasSize(1);
        CommandRegistration wrapped = result.get(0);
        assertThat(wrapped.aliases()).isEmpty();
        assertThat(wrapped.build().getLiteral()).isEqualTo("home");
    }

    @Test
    void localizedAliasesJoinTheGlobalPaperRegistrationSurface() {
        EffectiveCommand effective = new EffectiveCommand(
                new CommandId("home"),
                "home",
                List.of("h"),
                Map.of("tr", List.of("ev", "yuvam"), "de", List.of("zuhause")),
                true,
                true);
        CatalogBinding binding = new CatalogBinding(Map.of("home", effective));

        CommandRegistration wrapped =
                binding.apply(List.of(new StubRegistration("home"))).get(0);

        assertThat(wrapped.aliases()).containsExactlyInAnyOrder("h", "ev", "yuvam", "zuhause");
    }

    private record StubRegistration(String id) implements CommandRegistration {
        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            return Commands.literal(id)
                    .executes(c -> 1)
                    .then(Commands.literal("set").executes(c -> 1))
                    .build();
        }

        @Override
        public String description() {
            return "x";
        }

        @Override
        public List<String> aliases() {
            return List.of("h");
        }

        @Override
        public String commandId() {
            return id;
        }
    }
}
