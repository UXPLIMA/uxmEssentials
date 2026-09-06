package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmlib.packet.display.DisplayTextPackets;
import org.junit.jupiter.api.Test;

/**
 * Pins the thread the renderer resolves a hologram's per-viewer placeholder text on:
 * {@link HologramRenderer#dispatchPerViewerText} must hop <em>each</em> viewer's resolve onto <em>that
 * viewer's own</em> entity thread (one {@code onEntity} hop per viewer, keyed to that viewer), never resolving
 * inline on the caller's thread. Resolving a player-relative {@code %papi%} token reads the viewer's live entity
 * state, which under Folia is only safe to touch from the entity's owning thread, the same rule the scoreboard
 * and tablist render loops follow. A regression that resolved every viewer inline on the hologram's region
 * thread (a cross-region read) would record zero entity hops and fail the first assertion here.
 *
 * <p>The dispatch is exercised against a recording {@link Scheduler} and a recording packet sink, with viewers
 * as lightweight proxies, no MockBukkit, no NMS, no PlaceholderAPI.
 */
class HologramRendererPerViewerDispatchTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final int ENTITY_ID = 77;

    @Test
    void eachViewersResolveIsHoppedOntoThatViewersEntityThread() {
        RecordingScheduler scheduler = new RecordingScheduler();
        RecordingPackets packets = new RecordingPackets();
        HologramTextOverrides overrides = overrides(
                packets,
                perViewer(Map.of(
                        ALICE, source -> source.replace("%name%", "Alice"),
                        BOB, source -> source.replace("%name%", "Bob"))));

        HologramRenderer.dispatchPerViewerText(
                scheduler, overrides, List.of(player(ALICE), player(BOB)), ENTITY_ID, hologram("hi %name%"));

        // Exactly one entity hop per viewer, each keyed to that viewer, never an inline resolve.
        assertThat(scheduler.entityHops).containsExactly(ALICE, BOB);
        // And the resolve that ran inside each hop produced that viewer's own text.
        assertThat(packets.textFor(ALICE)).isEqualTo("hi Alice");
        assertThat(packets.textFor(BOB)).isEqualTo("hi Bob");
    }

    @Test
    void anEmptyViewerSetHopsNothing() {
        RecordingScheduler scheduler = new RecordingScheduler();
        RecordingPackets packets = new RecordingPackets();

        HologramRenderer.dispatchPerViewerText(
                scheduler, overrides(packets, perViewer(Map.of())), List.of(), ENTITY_ID, hologram("hi %name%"));

        assertThat(scheduler.entityHops).isEmpty();
        assertThat(packets.sent).isEmpty();
    }

    // --- fixtures ---------------------------------------------------------------------------------------------

    private static HologramTextOverrides overrides(
            RecordingPackets packets, Function<UUID, UnaryOperator<String>> bridges) {
        return new HologramTextOverrides(
                packets,
                bridges,
                MiniMessage.miniMessage(),
                net.kyori.adventure.text.minimessage.tag.resolver.TagResolver::empty,
                new HologramPageState(),
                noOpLogger());
    }

    private static Function<UUID, UnaryOperator<String>> perViewer(Map<UUID, UnaryOperator<String>> byViewer) {
        return uuid -> byViewer.getOrDefault(uuid, UnaryOperator.identity());
    }

    private static Hologram hologram(String line) {
        Position at = new Position(new WorldRef(UUID.randomUUID(), "world"), 0.0, 64.0, 0.0, 0.0f, 0.0f);
        return Hologram.create(new HologramName("h"), at, List.of(new HologramLine(line)), Instant.EPOCH);
    }

    private static Player player(UUID uuid) {
        return (Player) java.lang.reflect.Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> {
                    if ("getUniqueId".equals(method.getName())) {
                        return uuid;
                    }
                    if ("getName".equals(method.getName())) {
                        return uuid.toString();
                    }
                    return null;
                });
    }

    private static Logger noOpLogger() {
        return new Logger() {
            @Override
            public void info(String message, Object... args) {}

            @Override
            public void warn(String message, Object... args) {}

            @Override
            public void error(String message, Throwable cause) {}

            @Override
            public void debug(String message, Object... args) {}
        };
    }

    /** Records each per-entity hop by viewer uuid and runs it inline so the resolve inside is exercised. */
    private static final class RecordingScheduler implements Scheduler {

        private final List<UUID> entityHops = new ArrayList<>();

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            entityHops.add(player.uuid());
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** Records the resolved override text per viewer uuid; no NMS. */
    private static final class RecordingPackets implements DisplayTextPackets {

        record TextOverride(int entityId, Component text) {}

        record Sent(UUID viewer, Component text) {}

        private final List<Sent> sent = new ArrayList<>();

        @Override
        public Object textOverride(int entityId, Component text) {
            return new TextOverride(entityId, text);
        }

        @Override
        public void send(Player viewer, Object packet) {
            sent.add(new Sent(viewer.getUniqueId(), ((TextOverride) packet).text()));
        }

        String textFor(UUID viewer) {
            return sent.stream()
                    .filter(s -> s.viewer().equals(viewer))
                    .map(s -> PlainTextComponentSerializer.plainText().serialize(s.text()))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
