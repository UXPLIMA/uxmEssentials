package com.uxplima.uxmessentials.communication.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.communication.adapter.CommunicationSettings;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitInfoSender;
import com.uxplima.uxmessentials.communication.adapter.outbound.ChatMeta;
import com.uxplima.uxmessentials.communication.adapter.outbound.ChatMetaSource;
import com.uxplima.uxmessentials.communication.application.ResolveConnectionMessage;
import com.uxplima.uxmessentials.communication.application.ResolveJoinMessage;
import com.uxplima.uxmessentials.communication.application.ResolveQuitMessage;
import com.uxplima.uxmessentials.communication.application.port.SequenceCounter;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * The join line the {@link ConnectionMessageListener} sets, driven by the joiner's rank and history over a real
 * {@link CommunicationSettings} authored with a per-group override and a first-join welcome. A brand-new player
 * ({@code !hasPlayedBefore()}) is greeted with the welcome; a returning player takes the ordinary line, chosen by
 * their primary group. The events and player are mocked so {@code hasPlayedBefore()} and the primary group can be
 * pinned deterministically.
 */
class ConnectionMessageListenerTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private CommunicationSettings settings;

    @BeforeEach
    void setUp(@TempDir Path dataDir) throws Exception {
        Path dir = dataDir.resolve("modules").resolve("communication");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("join-quit.conf"), """
                join {
                  mode = CUSTOM
                  ordering = SEQUENTIAL
                  templates = [ "join {player}" ]
                  groups { vip = [ "vip {player}" ] }
                }
                quit { mode = DEFAULT }
                death { mode = DEFAULT }
                first-join { mode = CUSTOM, templates = [ "welcome {player}" ] }
                """);
        settings = new CommunicationSettings(dir, new NoopLogger());
    }

    @Test
    void aFirstEverJoinIsGreetedWithTheWelcome() {
        Component set = onJoin(false, ChatMeta.empty());

        assertThat(PLAIN.serialize(set)).isEqualTo("welcome Alice");
    }

    @Test
    void aReturningPlayerTakesTheOrdinaryJoinLineNotTheWelcome() {
        Component set = onJoin(true, ChatMeta.empty());

        assertThat(PLAIN.serialize(set)).isEqualTo("join Alice");
    }

    @Test
    void aReturningPlayersLineIsChosenByTheirPrimaryGroup() {
        Component set = onJoin(true, new ChatMeta("", "", Optional.of("vip")));

        assertThat(PLAIN.serialize(set)).isEqualTo("vip Alice");
    }

    /** Fire a join for a player with the given first-join flag and meta, returning the join line the listener set. */
    private Component onJoin(boolean hasPlayedBefore, ChatMeta meta) {
        Player player = player(hasPlayedBefore);
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);

        listener(who -> meta).onJoin(event);

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(event).joinMessage(captor.capture());
        return captor.getValue();
    }

    private ConnectionMessageListener listener(ChatMetaSource metaSource) {
        ResolveConnectionMessage engine = new ResolveConnectionMessage(new CountingSequence(), bound -> 0);
        return new ConnectionMessageListener(
                new ResolveJoinMessage(engine, settings::joinPolicies, settings::firstJoinPolicy),
                new ResolveQuitMessage(engine, settings::quitPolicy),
                settings,
                new BukkitInfoSender(new NoopSink()),
                metaSource);
    }

    private static Player player(boolean hasPlayedBefore) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ID);
        when(player.getName()).thenReturn("Alice");
        when(player.displayName()).thenReturn(Component.text("Alice"));
        when(player.hasPlayedBefore()).thenReturn(hasPlayedBefore);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(player.getWorld()).thenReturn(world);
        Server server = mock(Server.class);
        doReturn(List.of()).when(server).getOnlinePlayers();
        when(player.getServer()).thenReturn(server);
        return player;
    }

    /** A {@link SequenceCounter} that hands out 0, 1, 2, … per channel, the production atomic counter, in-test. */
    private static final class CountingSequence implements SequenceCounter {
        private final Map<String, Integer> counters = new java.util.HashMap<>();

        @Override
        public int next(String channel) {
            int value = counters.getOrDefault(channel, 0);
            counters.put(channel, value + 1);
            return value;
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // no MOTD is authored in these tests, so nothing is delivered
        }
    }

    private static final class NoopLogger implements Logger {
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
