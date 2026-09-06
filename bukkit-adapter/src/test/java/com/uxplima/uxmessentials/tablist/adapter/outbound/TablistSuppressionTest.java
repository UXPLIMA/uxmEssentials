package com.uxplima.uxmessentials.tablist.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.packet.tablist.TabEntry;
import com.uxplima.uxmlib.packet.tablist.TabListPackets;
import com.uxplima.uxmlib.pipeline.PacketListener;
import com.uxplima.uxmlib.pipeline.PacketListenerRegistry;
import com.uxplima.uxmlib.pipeline.PacketVerdict;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers the {@link TablistSuppression} TAB-C mechanism without the NMS dev bundle: the suppress predicate keeps fillers
 * and hides real players, the shared listener returns REWRITE for a player-info packet the {@link Rewriter} rewrites and
 * PASS for anything else (or any non-active viewer), the lifecycle registers the listener and relists real players
 * unlisted on enter and listed on exit (ejecting the interceptor), {@code forget} skips the relist to a closing channel,
 * and a rewriter that throws is folded to PASS so nothing escapes onto the Netty thread.
 */
class TablistSuppressionTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theSuppressPredicateHidesRealPlayersButKeepsFillers() {
        PlayerMock viewer = server.addPlayer();
        RecordingGate gate = new RecordingGate();
        TablistSuppression suppression = suppression(gate, new RecordingPackets(), passThroughRewriter());
        UUID fillerId = UUID.randomUUID();
        UUID realId = UUID.randomUUID();

        suppression.apply(viewer, true, Set.of(fillerId));

        Predicate<UUID> suppress = suppression.suppressPredicate(viewer.getUniqueId());
        // A filler id stays listed (NOT suppressed); a real player's id is suppressed (hidden).
        assertThat(suppress.test(fillerId)).isFalse();
        assertThat(suppress.test(realId)).isTrue();
    }

    @Test
    void theListenerRewritesAPlayerInfoUpdateForAnActiveViewerAndPassesOtherPacketsAndViewers() {
        PlayerMock active = server.addPlayer();
        PlayerMock inactive = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        PacketListenerRegistry registry = new PacketListenerRegistry();
        // A rewriter that always "rewrites" to a sentinel, so a player-info packet for an active viewer yields REWRITE.
        Object sentinel = new Object();
        TablistSuppression suppression = new TablistSuppression(
                new RecordingGate(), registry, packets, server::getOnlinePlayers, new NoopLogger(), (p, s) -> sentinel);
        suppression.apply(active, true, Set.of());

        PacketListener listener = single(registry);
        Object packet = "a-player-info-update";

        // Active viewer + a packet the rewriter rewrites -> REWRITE carrying the replacement.
        PacketVerdict rewritten = listener.onSendVerdict(active.getUniqueId(), packet);
        assertThat(rewritten.replacement()).isSameAs(sentinel);

        // A viewer NOT in suppress mode -> PASS (no rewrite), even for the same packet.
        assertThat(listener.onSendVerdict(inactive.getUniqueId(), packet).replacement())
                .isNull();
        assertThat(listener.onSendVerdict(inactive.getUniqueId(), packet).cancels())
                .isFalse();

        // A null owner (pre-login) -> PASS.
        assertThat(listener.onSendVerdict(null, packet).replacement()).isNull();
    }

    @Test
    void theListenerPassesWhenTheRewriterReturnsNull() {
        PlayerMock viewer = server.addPlayer();
        PacketListenerRegistry registry = new PacketListenerRegistry();
        // A non-player-info packet (or one whose actions do not affect listing) -> rewriter returns null -> PASS.
        TablistSuppression suppression = new TablistSuppression(
                new RecordingGate(),
                registry,
                new RecordingPackets(),
                server::getOnlinePlayers,
                new NoopLogger(),
                (p, s) -> null);
        suppression.apply(viewer, true, Set.of());

        PacketVerdict verdict = single(registry).onSendVerdict(viewer.getUniqueId(), "not-a-player-info");

        assertThat(verdict.replacement()).isNull();
        assertThat(verdict.cancels()).isFalse();
    }

    @Test
    void enteringSuppressModeRegistersInjectsAndRelistsRealPlayersUnlisted() {
        PlayerMock viewer = server.addPlayer();
        PlayerMock other = server.addPlayer();
        RecordingGate gate = new RecordingGate();
        RecordingPackets packets = new RecordingPackets();
        PacketListenerRegistry registry = new PacketListenerRegistry();
        TablistSuppression suppression = new TablistSuppression(
                gate, registry, packets, server::getOnlinePlayers, new NoopLogger(), passThroughRewriter());

        suppression.apply(viewer, true, Set.of());

        // The single shared listener is registered once at construction.
        assertThat(registry.snapshot()).hasSize(1);
        // Entering suppress mode injects the interceptor into the viewer's pipeline.
        assertThat(gate.injected).containsExactly(viewer.getUniqueId());
        // The real players are relisted UNLISTED for the viewer so the already-sent rows are hidden at once.
        Relist relist = packets.lastRelistTo(viewer.getUniqueId());
        assertThat(relist.listed()).isFalse();
        assertThat(relist.ids()).contains(viewer.getUniqueId(), other.getUniqueId());
    }

    @Test
    void aPlayerASeatKeepsListedIsNotHiddenWhenSuppressModeStarts() {
        PlayerMock viewer = server.addPlayer();
        PlayerMock seated = server.addPlayer();
        RecordingGate gate = new RecordingGate();
        RecordingPackets packets = new RecordingPackets();
        TablistSuppression suppression = suppression(gate, packets, passThroughRewriter());

        suppression.apply(viewer, true, Set.of(seated.getUniqueId()));

        // A player a roster group seated is drawn by their own entry, so the entering edge must leave that row listed.
        Relist relist = packets.lastRelistTo(viewer.getUniqueId());
        assertThat(relist.listed()).isFalse();
        assertThat(relist.ids()).contains(viewer.getUniqueId());
        assertThat(relist.ids()).doesNotContain(seated.getUniqueId());
    }

    @Test
    void aPlayerSeatedLaterIsListedAgainAndOneReleasedIsHidden() {
        PlayerMock viewer = server.addPlayer();
        PlayerMock seated = server.addPlayer();
        RecordingGate gate = new RecordingGate();
        RecordingPackets packets = new RecordingPackets();
        TablistSuppression suppression = suppression(gate, packets, passThroughRewriter());

        suppression.apply(viewer, true, Set.of());
        suppression.apply(viewer, true, Set.of(seated.getUniqueId()));

        // The group seated the player after the viewer was already suppressed, so their row is listed again.
        Relist listedAgain = packets.lastRelistTo(viewer.getUniqueId());
        assertThat(listedAgain.listed()).isTrue();
        assertThat(listedAgain.ids()).containsExactly(seated.getUniqueId());

        suppression.apply(viewer, true, Set.of());

        // The group released the cell, so the row is hidden again.
        Relist hiddenAgain = packets.lastRelistTo(viewer.getUniqueId());
        assertThat(hiddenAgain.listed()).isFalse();
        assertThat(hiddenAgain.ids()).containsExactly(seated.getUniqueId());
    }

    @Test
    void aSecondSuppressTickDoesNotReInjectOrReRelist() {
        PlayerMock viewer = server.addPlayer();
        RecordingGate gate = new RecordingGate();
        RecordingPackets packets = new RecordingPackets();
        TablistSuppression suppression = suppression(gate, packets, passThroughRewriter());

        suppression.apply(viewer, true, Set.of());
        int relistsAfterEnter = packets.relists.size();
        suppression.apply(viewer, true, Set.of(UUID.randomUUID()));

        // The enter edge fired once; a steady suppress tick only refreshes the filler snapshot, no re-inject/relist.
        assertThat(gate.injected).containsExactly(viewer.getUniqueId());
        assertThat(packets.relists).hasSize(relistsAfterEnter);
    }

    @Test
    void leavingSuppressModeEjectsAndRelistsRealPlayersListed() {
        PlayerMock viewer = server.addPlayer();
        RecordingGate gate = new RecordingGate();
        RecordingPackets packets = new RecordingPackets();
        TablistSuppression suppression = suppression(gate, packets, passThroughRewriter());

        suppression.apply(viewer, true, Set.of());
        suppression.disable(viewer);

        // Exiting suppress mode ejects the interceptor and relists the real players back (listed = true) to restore.
        assertThat(gate.ejected).containsExactly(viewer.getUniqueId());
        Relist restore = packets.lastRelistTo(viewer.getUniqueId());
        assertThat(restore.listed()).isTrue();
        // The listener no longer rewrites for the viewer (they are no longer active).
        assertThat(packets.relists).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void disableIsANoOpForAViewerWhoWasNeverSuppressed() {
        PlayerMock viewer = server.addPlayer();
        RecordingGate gate = new RecordingGate();
        RecordingPackets packets = new RecordingPackets();
        TablistSuppression suppression = suppression(gate, packets, passThroughRewriter());

        suppression.disable(viewer);

        // No suppress mode was ever entered, so nothing is ejected and no restore relist is sent.
        assertThat(gate.ejected).isEmpty();
        assertThat(packets.relists).isEmpty();
    }

    @Test
    void switchingTheSuppressFlagOffWithinApplyRestoresTheViewer() {
        PlayerMock viewer = server.addPlayer();
        RecordingGate gate = new RecordingGate();
        RecordingPackets packets = new RecordingPackets();
        TablistSuppression suppression = suppression(gate, packets, passThroughRewriter());

        suppression.apply(viewer, true, Set.of());
        suppression.apply(viewer, false, Set.of());

        assertThat(gate.ejected).containsExactly(viewer.getUniqueId());
        assertThat(packets.lastRelistTo(viewer.getUniqueId()).listed()).isTrue();
    }

    @Test
    void forgetEjectsWithoutSendingARelistToAClosingChannel() {
        PlayerMock viewer = server.addPlayer();
        RecordingGate gate = new RecordingGate();
        RecordingPackets packets = new RecordingPackets();
        TablistSuppression suppression = suppression(gate, packets, passThroughRewriter());

        suppression.apply(viewer, true, Set.of());
        int relistsAfterEnter = packets.relists.size();
        suppression.forget(viewer);

        // forget drops the tracking and ejects the interceptor, but sends NO relist packet to the closing channel.
        assertThat(gate.ejected).containsExactly(viewer.getUniqueId());
        assertThat(packets.relists).hasSize(relistsAfterEnter);
    }

    @Test
    void aRewriterThrowIsFoldedToPassAndNeverEscapes() {
        PlayerMock viewer = server.addPlayer();
        PacketListenerRegistry registry = new PacketListenerRegistry();
        RecordingLogger logger = new RecordingLogger();
        TablistSuppression suppression = new TablistSuppression(
                new RecordingGate(), registry, new RecordingPackets(), server::getOnlinePlayers, logger, (p, s) -> {
                    throw new IllegalStateException("boom");
                });
        suppression.apply(viewer, true, Set.of());

        PacketListener listener = single(registry);
        PacketVerdict[] verdict = new PacketVerdict[1];
        assertThatCode(() -> verdict[0] = listener.onSendVerdict(viewer.getUniqueId(), "player-info"))
                .doesNotThrowAnyException();

        // The throw is fail-soft: the verdict passes the original packet, and the fault is logged off-thread.
        assertThat(verdict[0].replacement()).isNull();
        assertThat(verdict[0].cancels()).isFalse();
        assertThat(logger.errors).isNotEmpty();
    }

    private TablistSuppression suppression(
            RecordingGate gate, RecordingPackets packets, TablistSuppression.Rewriter r) {
        return new TablistSuppression(
                gate, new PacketListenerRegistry(), packets, server::getOnlinePlayers, new NoopLogger(), r);
    }

    private static TablistSuppression.Rewriter passThroughRewriter() {
        // A rewriter that never rewrites (always forwards the original): used where only the lifecycle matters.
        return (packet, suppress) -> null;
    }

    /** The single listener the suppression registers at construction: the shared per-connection interceptor. */
    private static PacketListener single(PacketListenerRegistry registry) {
        assertThat(registry.snapshot()).hasSize(1);
        return registry.snapshot().get(0);
    }

    /** A connection gate that records which viewers were injected/ejected. */
    private static final class RecordingGate implements TablistSuppression.ConnectionGate {
        private final List<UUID> injected = new ArrayList<>();
        private final List<UUID> ejected = new ArrayList<>();

        @Override
        public boolean inject(org.bukkit.entity.Player viewer) {
            injected.add(viewer.getUniqueId());
            return true;
        }

        @Override
        public boolean eject(org.bukkit.entity.Player viewer) {
            ejected.add(viewer.getUniqueId());
            return true;
        }
    }

    /** A fake {@link TabListPackets} that records relist (ids, listed) calls per viewer. */
    private static final class RecordingPackets implements TabListPackets {
        private final List<Sent> relists = new ArrayList<>();

        @Override
        public Object addOrUpdate(TabEntry entry) {
            return entry;
        }

        @Override
        public Object displayName(UUID id, Component name) {
            return name;
        }

        @Override
        public Object listOrder(UUID id, int order) {
            return order;
        }

        @Override
        public Object remove(List<UUID> ids) {
            return ids;
        }

        @Override
        public Object relist(List<UUID> ids, boolean listed) {
            return new Relist(List.copyOf(ids), listed);
        }

        @Override
        public void send(org.bukkit.entity.Player viewer, Object packet) {
            if (packet instanceof Relist relist) {
                relists.add(new Sent(viewer.getUniqueId(), relist));
            }
        }

        Relist lastRelistTo(UUID viewer) {
            for (int i = relists.size() - 1; i >= 0; i--) {
                if (relists.get(i).viewer().equals(viewer)) {
                    return relists.get(i).relist();
                }
            }
            throw new AssertionError("no relist sent to " + viewer);
        }

        private record Sent(UUID viewer, Relist relist) {}
    }

    private record Relist(List<UUID> ids, boolean listed) {}

    private static class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** A logger that captures error lines so the fail-soft test can assert the fault was logged. */
    private static final class RecordingLogger extends NoopLogger {
        private final List<String> errors = new ArrayList<>();

        @Override
        public void error(String message, @Nullable Throwable cause) {
            errors.add(message);
        }
    }
}
