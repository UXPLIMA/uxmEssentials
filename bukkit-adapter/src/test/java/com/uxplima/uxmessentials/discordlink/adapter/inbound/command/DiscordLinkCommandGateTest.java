package com.uxplima.uxmessentials.discordlink.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.discordlink.adapter.DiscordLinkServices;
import com.uxplima.uxmessentials.discordlink.adapter.inbound.gui.DiscordStatusView;
import com.uxplima.uxmessentials.discordlink.application.BeginLink;
import com.uxplima.uxmessentials.discordlink.application.ConfirmLink;
import com.uxplima.uxmessentials.discordlink.application.LinkStatus;
import com.uxplima.uxmessentials.discordlink.application.Unlink;
import com.uxplima.uxmessentials.discordlink.application.port.DiscordBridge;
import com.uxplima.uxmessentials.discordlink.application.port.DiscordLinkStore;
import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.DiscordId;
import com.uxplima.uxmessentials.discordlink.domain.LinkCode;
import com.uxplima.uxmessentials.discordlink.domain.PendingLink;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /discordlink} issuing gate. A connected bridge means a code can be redeemed,
 * so issuing mints and persists one; with no bridge a code would have nothing to redeem it, so the command
 * declines with {@code discordlink.not-configured} and runs {@link BeginLink} not at all: no row is written.
 * The gate guards only issuing; {@code /discordlink status} is unaffected.
 */
class DiscordLinkCommandGateTest {

    private static final String PERMISSION = "uxmessentials.discord.link";

    private ServerMock server;
    private FakeStore store;
    private BeginLink beginLink;
    private RecordingSink sink;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        store = new FakeStore();
        beginLink = new BeginLink(store, Clock.systemUTC(), new Random(1), Duration.ofMinutes(10));
        sink = new RecordingSink();
        player = server.addPlayer("Alice");
        player.addAttachment(MockBukkit.createMockPlugin(), PERMISSION, true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void issuesAndStoresACodeWhenTheBridgeIsAvailable() {
        execute(command(() -> true), "discordlink");

        assertThat(store.saves).isOne(); // BeginLink ran and persisted a pending code
        assertThat(store.pending).containsKey(player.getUniqueId());
        assertThat(sink.keys).contains("discordlink.code", "discordlink.howto");
        assertThat(sink.keys).doesNotContain("discordlink.not-configured");
    }

    @Test
    void declinesAndIssuesNoCodeWhenTheBridgeIsAbsent() {
        execute(command(() -> false), "discordlink");

        assertThat(store.saves).isZero(); // BeginLink never reached the store
        assertThat(store.pending).isEmpty(); // nothing was persisted
        assertThat(sink.keys).containsExactly("discordlink.not-configured");
    }

    private DiscordLinkCommand command(DiscordBridge bridge) {
        Notifier notifier = new Notifier(new KeyMessages(), sink);
        DiscordLinkServices services = new DiscordLinkServices(
                beginLink,
                new ConfirmLink(store, Clock.systemUTC()),
                new Unlink(store, event -> {}),
                new LinkStatus(store),
                notifier,
                bridge);
        // The gate test drives only /discordlink begin; the command holds a view it never opens, so a mock suffices.
        return new DiscordLinkCommand(services, org.mockito.Mockito.mock(DiscordStatusView.class));
    }

    private void execute(DiscordLinkCommand command, String input) {
        CommandSourceStack source = CommandSourceStackMock.from(player);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());
        try {
            dispatcher.execute(input, source);
        } catch (CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    /** A map-backed link store mirroring the jOOQ store's per-player invariant, counting writes. */
    private static final class FakeStore implements DiscordLinkStore {
        private final Map<UUID, PendingLink> pending = new HashMap<>();
        private final Map<UUID, ConfirmedLink> confirmed = new HashMap<>();
        private int saves;

        @Override
        public void savePending(PendingLink row) {
            saves++;
            pending.put(row.player(), row);
        }

        @Override
        public Optional<PendingLink> findPendingByCode(LinkCode code) {
            return pending.values().stream().filter(r -> r.code().equals(code)).findFirst();
        }

        @Override
        public void deletePending(UUID player) {
            pending.remove(player);
        }

        @Override
        public void confirm(ConfirmedLink link) {
            confirmed.put(link.player(), link);
            pending.remove(link.player());
        }

        @Override
        public Optional<ConfirmedLink> findByPlayer(UUID player) {
            return Optional.ofNullable(confirmed.get(player));
        }

        @Override
        public Optional<ConfirmedLink> findByDiscordId(DiscordId discordId) {
            return confirmed.values().stream()
                    .filter(l -> l.discordId().equals(discordId))
                    .findFirst();
        }

        @Override
        public boolean unlink(UUID player) {
            return confirmed.remove(player) != null;
        }
    }

    /** Records the resolved catalog keys so the gate's reply is assertable without rendering. */
    private static final class RecordingSink implements MessageSink {
        private final List<String> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            keys.add(renderedText);
        }
    }

    /** Echoes the catalog key so the recorded delivery reveals which message the gate chose. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
