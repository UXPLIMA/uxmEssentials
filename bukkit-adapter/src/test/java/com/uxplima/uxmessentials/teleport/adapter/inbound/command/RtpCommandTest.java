package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.adapter.inbound.gui.RtpMenu;
import com.uxplima.uxmessentials.teleport.application.ResolveRtp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The {@code /rtp} Brigadier tree and its {@code <target>} disambiguation. The node keeps {@code biome} and
 * {@code gui} as explicit literals (matched first) with a fallback {@code target} argument, so an online player
 * name forces that player, a loaded world name random-teleports the sender there, and anything else is reported as
 * an unknown target. MockBukkit boots Paper's Brigadier for the node build and gives real players/worlds for the
 * routing; the resolver is a mock so the routed player/world is verifiable.
 */
class RtpCommandTest {

    private ServerMock server;
    private Plugin plugin;
    private TeleportServices services;
    private ResolveRtp resolveRtp;
    private FakeWorlds worlds;
    private CapturingSink sink;
    private RtpCommand command;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        services = mock(TeleportServices.class);
        resolveRtp = mock(ResolveRtp.class);
        worlds = new FakeWorlds();
        sink = new CapturingSink();
        when(services.resolveRtp()).thenReturn(resolveRtp);
        when(services.worlds()).thenReturn(worlds);
        when(services.notifier()).thenReturn(new Notifier(new KeyMessages(), sink));
        command = new RtpCommand(services, new KeyMessages(), mock(RtpMenu.class), true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theTreeExposesGuiBiomeAndAFallbackTargetArgument() {
        LiteralCommandNode<CommandSourceStack> node = command.build();

        assertThat(node.getChild("gui")).isNotNull();
        assertThat(node.getChild("biome")).isNotNull();
        assertThat(node.getChild("target")).isNotNull(); // the player-or-world fallback argument
    }

    @Test
    void anOnlinePlayerNameForcesThatPlayerThroughTheResolver() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, RtpCommand.OTHERS_PERMISSION, true);
        PlayerMock victim = server.addPlayer("Victim");

        command.route(staff, "Victim");

        // The target, never the issuer, is routed, within the target's own world.
        verify(resolveRtp)
                .background(new PlayerRef(victim.getUniqueId(), victim.getName()), BukkitRefs.toRef(victim.getWorld()));
        assertThat(sink.delivered).contains("teleport.rtp.searching");
    }

    @Test
    void aLoadedWorldNameRandomTeleportsTheSenderThere() {
        PlayerMock sender = server.addPlayer("Wanderer");
        WorldRef resource = new WorldRef(java.util.UUID.randomUUID(), "resource");
        worlds.put("resource", resource);

        command.route(sender, "resource");

        verify(resolveRtp).background(new PlayerRef(sender.getUniqueId(), sender.getName()), resource);
        assertThat(sink.delivered).contains("teleport.rtp.searching");
    }

    @Test
    void anUnknownTargetIsReportedAndNothingIsRouted() {
        PlayerMock sender = server.addPlayer("Wanderer");

        command.route(sender, "nonsense");

        verify(resolveRtp, never()).background(any(), any());
        assertThat(sink.delivered).contains("teleport.rtp.unknown-target");
    }

    @Test
    void aMatchingPlayerWithoutTheOthersPermissionFallsThroughToUnknown() {
        PlayerMock sender = server.addPlayer("Wanderer");
        server.addPlayer("Victim"); // present, but the sender may not force others

        command.route(sender, "Victim");

        verify(resolveRtp, never()).background(any(), any());
        assertThat(sink.delivered).contains("teleport.rtp.unknown-target");
    }

    @Test
    void aBareRtpOpensTheWorldPickerByDefault() {
        RtpMenu menu = mock(RtpMenu.class);
        RtpCommand cmd = new RtpCommand(services, new KeyMessages(), menu, true);
        PlayerMock sender = server.addPlayer("Wanderer");

        cmd.bare(sender);

        verify(menu).open(new PlayerRef(sender.getUniqueId(), sender.getName()));
        verify(resolveRtp, never()).background(any(), any()); // opening the GUI does not teleport on its own
    }

    @Test
    void aBareRtpTeleportsInPlaceWhenTheGuiToggleIsOff() {
        RtpMenu menu = mock(RtpMenu.class);
        RtpCommand cmd = new RtpCommand(services, new KeyMessages(), menu, false);
        PlayerMock sender = server.addPlayer("Wanderer");

        cmd.bare(sender);

        verify(resolveRtp)
                .background(new PlayerRef(sender.getUniqueId(), sender.getName()), BukkitRefs.toRef(sender.getWorld()));
        verify(menu, never()).open(any());
        assertThat(sink.delivered).contains("teleport.rtp.searching");
    }

    private static final class FakeWorlds implements WorldLookup {
        private final Map<String, WorldRef> byName = new HashMap<>();

        void put(String name, WorldRef ref) {
            byName.put(name, ref);
        }

        @Override
        public Optional<WorldRef> findByName(String name) {
            return Optional.ofNullable(byName.get(name));
        }

        @Override
        public Optional<WorldRef> findByUid(java.util.UUID uid) {
            return Optional.empty();
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class CapturingSink implements MessageSink {
        private final List<String> delivered = new java.util.ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }
}
