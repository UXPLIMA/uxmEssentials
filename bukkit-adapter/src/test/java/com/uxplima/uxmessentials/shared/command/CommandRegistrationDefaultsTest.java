package com.uxplima.uxmessentials.shared.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.LocaleBinding;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The {@code commandId}/{@code defaultName}/{@code defaultAliases} accessors are what the command catalog
 * keys config overrides against, so they must read straight off the code-side registration: the id and
 * default name come from the built node's root literal, the default aliases mirror {@link
 * CommandRegistration#aliases()}. These derivations also have to survive the {@link LocaleBinding} wrapper,
 * which rebuilds the tree at registration time. If the wrapper dropped the literal or the alias list the
 * catalog would key overrides against the wrong command. MockBukkit boots Paper's Brigadier so {@link
 * Commands#literal} is wired before the nodes are built.
 */
class CommandRegistrationDefaultsTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void defaultsDeriveFromBuiltNodeAndAliases() {
        CommandRegistration stub = new StubRegistration();

        assertThat(stub.commandId()).isEqualTo("foo");
        assertThat(stub.defaultName()).isEqualTo("foo");
        assertThat(stub.defaultAliases()).containsExactly("f", "fo");
    }

    @Test
    void localeWrapperPreservesIdAndDefaults() {
        LocaleBinding binding =
                new LocaleBinding(new NoOverrides(), Locale.ENGLISH, new FixedMessages(), new NoOpLog());
        CommandRegistration wrapped = binding.wrap(new StubRegistration());

        assertThat(wrapped.commandId()).isEqualTo("foo");
        assertThat(wrapped.defaultName()).isEqualTo("foo");
        assertThat(wrapped.defaultAliases()).containsExactly("f", "fo");
    }

    private static final class StubRegistration implements CommandRegistration {
        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            return Commands.literal("foo").build();
        }

        @Override
        public String description() {
            return "x";
        }

        @Override
        public List<String> aliases() {
            return List.of("f", "fo");
        }
    }

    private static final class NoOverrides implements LocaleStore {
        @Override
        public Optional<Locale> override(PlayerRef player) {
            return Optional.empty();
        }

        @Override
        public void setOverride(PlayerRef player, Locale locale) {}

        @Override
        public void clearOverride(PlayerRef player) {}
    }

    private static final class FixedMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoOpLog implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
