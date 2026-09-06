package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmlib.packet.display.DisplayTextPackets;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link HologramTextOverrides}. The collaborator that resolves a hologram's lines per viewer and sends a
 * text-override packet for one viewer. A line with a {@code %...%} token marks the hologram as per-viewer; a
 * static hologram gets no override at all. Each viewer's packet carries <em>their</em> resolved text (the
 * injected bridge factory returns a viewer-specific transform), and one viewer's resolve throwing must not stop
 * the renderer's loop over the others. Exercised here by sending each viewer in turn through the per-viewer
 * {@code sendOverride}, exactly as the renderer's per-viewer entity hop does. The packet sink and bridge factory
 * are fakes, so no NMS and no PlaceholderAPI is needed.
 */
class HologramTextOverridesTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final int ENTITY_ID = 99;

    @Test
    void detectsPerViewerTextOnlyWhenALineHasAPlaceholder() {
        HologramTextOverrides overrides = overrides(perViewer(Map.of()));

        assertThat(overrides.hasPerViewerText(text("plain", "no tokens here"))).isFalse();
        assertThat(overrides.hasPerViewerText(text("dyn", "hi %player_name%"))).isTrue();
    }

    @Test
    void itemAndBlockHologramsAreNeverPerViewerEvenIfALabelHasAToken() {
        // An item/block hologram renders the model only (v1), so a label line is not a per-viewer text target.
        HologramTextOverrides overrides = overrides(perViewer(Map.of()));
        Hologram item = Hologram.createItem(name("torch"), at(), "TORCH", Instant.EPOCH);

        assertThat(overrides.hasPerViewerText(item)).isFalse();
    }

    @Test
    void eachEligibleViewerGetsAPacketWithTheirOwnResolvedText() {
        FakeDisplayTextPackets packets = new FakeDisplayTextPackets();
        HologramTextOverrides overrides = overrides(
                packets,
                perViewer(Map.of(
                        ALICE, source -> source.replace("%name%", "Alice"),
                        BOB, source -> source.replace("%name%", "Bob"))));
        Hologram hologram = text("welcome", "hello %name%");

        overrides.sendOverride(player(ALICE), ENTITY_ID, hologram);
        overrides.sendOverride(player(BOB), ENTITY_ID, hologram);

        assertThat(packets.sends).hasSize(2);
        assertThat(plain(packets.textFor(ALICE))).isEqualTo("hello Alice");
        assertThat(plain(packets.textFor(BOB))).isEqualTo("hello Bob");
        assertThat(packets.sends).allSatisfy(sent -> assertThat(sent.entityId()).isEqualTo(ENTITY_ID));
    }

    @Test
    void resolvesGlobalMiniPlaceholdersTagsInThePerViewerText() {
        // The global MiniPlaceholders resolver (a <tag> source) is applied during deserialize, alongside the
        // per-viewer %token% bridge, so a per-viewer hologram renders both for the viewer.
        FakeDisplayTextPackets packets = new FakeDisplayTextPackets();
        Supplier<TagResolver> greeting = () -> Placeholder.parsed("greeting", "Howdy");
        HologramTextOverrides overrides =
                overrides(packets, perViewer(Map.of(ALICE, source -> source.replace("%name%", "Alice"))), greeting);
        Hologram hologram = text("welcome", "<greeting> %name%");

        overrides.sendOverride(player(ALICE), ENTITY_ID, hologram);

        assertThat(plain(packets.textFor(ALICE))).isEqualTo("Howdy Alice");
    }

    @Test
    void multipleLinesAreJoinedWithNewlinesAsTheSharedEntityRendersThem() {
        FakeDisplayTextPackets packets = new FakeDisplayTextPackets();
        HologramTextOverrides overrides =
                overrides(packets, perViewer(Map.of(ALICE, source -> source.replace("%name%", "Alice"))));
        Hologram hologram = new Hologram(
                name("two"),
                at(),
                com.uxplima.uxmessentials.holograms.domain.HologramType.TEXT,
                List.of(new HologramLine("top %name%"), new HologramLine("bottom")),
                null,
                null,
                null,
                null,
                com.uxplima.uxmessentials.holograms.domain.Appearance.defaults(),
                com.uxplima.uxmessentials.holograms.domain.Visibility.everyone(),
                com.uxplima.uxmessentials.holograms.domain.Rotation.NONE,
                Hologram.STATIC,
                Instant.EPOCH);

        overrides.sendOverride(player(ALICE), ENTITY_ID, hologram);

        assertThat(plain(packets.textFor(ALICE))).isEqualTo("top Alice\nbottom");
    }

    @Test
    void aMultiPageHologramRendersEachViewersCurrentPage() {
        FakeDisplayTextPackets packets = new FakeDisplayTextPackets();
        HologramPageState pages = new HologramPageState();
        HologramTextOverrides overrides = new HologramTextOverrides(
                packets, perViewer(Map.of()), MiniMessage.miniMessage(), TagResolver::empty, pages, noOpLogger());
        Hologram paged = text("guide", "page one")
                .withPageAppended(com.uxplima.uxmessentials.holograms.domain.HologramPage.of(
                        List.of(new HologramLine("page two"))));

        // Alice is advanced to the next page, Bob is left on the default page 0; each is sent once, so each
        // viewer's override carries their own current page over the one shared display.
        pages.advance("guide", ALICE, paged.pageCount());
        overrides.sendOverride(player(ALICE), ENTITY_ID, paged);
        overrides.sendOverride(player(BOB), ENTITY_ID, paged);

        assertThat(plain(packets.textFor(ALICE))).isEqualTo("page two");
        assertThat(plain(packets.textFor(BOB))).isEqualTo("page one");
    }

    @Test
    void resolvesTheBuiltInPlayerAndPageTokensPerViewer() {
        FakeDisplayTextPackets packets = new FakeDisplayTextPackets();
        HologramPageState pages = new HologramPageState();
        HologramTextOverrides overrides = new HologramTextOverrides(
                packets, perViewer(Map.of()), MiniMessage.miniMessage(), TagResolver::empty, pages, noOpLogger());
        Hologram paged = text("guide", "{player} - {page}/{pages}")
                .withPageAppended(com.uxplima.uxmessentials.holograms.domain.HologramPage.of(
                        List.of(new HologramLine("second"))));

        // Alice stays on page 0 (the token line); it renders her name and her 1-based page over the 2-page total
        // with no PlaceholderAPI involved.
        overrides.sendOverride(player(ALICE, "Steve"), ENTITY_ID, paged);

        assertThat(plain(packets.textFor(ALICE))).isEqualTo("Steve - 1/2");
    }

    @Test
    void detectsTheBuiltInTokensAsPerViewerText() {
        HologramTextOverrides overrides = overrides(perViewer(Map.of()));

        assertThat(overrides.hasPerViewerText(text("welcome", "hi {player}"))).isTrue();
        assertThat(overrides.hasPerViewerText(text("plain", "no tokens"))).isFalse();
    }

    @Test
    void oneViewersResolveThrowingIsSwallowedSoTheRendererLoopContinues() {
        FakeDisplayTextPackets packets = new FakeDisplayTextPackets();
        UnaryOperator<String> blowsUp = source -> {
            throw new IllegalStateException("boom");
        };
        HologramTextOverrides overrides =
                overrides(packets, perViewer(Map.of(ALICE, blowsUp, BOB, source -> source.replace("%name%", "Bob"))));
        Hologram hologram = text("welcome", "hello %name%");

        // The renderer hops each viewer onto their own thread and calls sendOverride per viewer; Alice's resolve
        // throws and is swallowed (no send), so the next viewer's call still goes through.
        overrides.sendOverride(player(ALICE), ENTITY_ID, hologram);
        overrides.sendOverride(player(BOB), ENTITY_ID, hologram);

        assertThat(packets.sends).hasSize(1);
        assertThat(plain(packets.textFor(BOB))).isEqualTo("hello Bob");
    }

    @Test
    void sendsNothingWhenNoViewerIsResolved() {
        FakeDisplayTextPackets packets = new FakeDisplayTextPackets();
        overrides(packets, perViewer(Map.of()));

        // With no eligible viewer the renderer never calls sendOverride, so nothing leaves the sink.
        assertThat(packets.sends).isEmpty();
    }

    // --- fixtures -------------------------------------------------------------------------------------------

    private static HologramTextOverrides overrides(Function<UUID, UnaryOperator<String>> bridges) {
        return overrides(new FakeDisplayTextPackets(), bridges);
    }

    private static HologramTextOverrides overrides(
            FakeDisplayTextPackets packets, Function<UUID, UnaryOperator<String>> bridges) {
        return overrides(packets, bridges, TagResolver::empty);
    }

    private static HologramTextOverrides overrides(
            FakeDisplayTextPackets packets,
            Function<UUID, UnaryOperator<String>> bridges,
            Supplier<TagResolver> globalTags) {
        return new HologramTextOverrides(
                packets, bridges, MiniMessage.miniMessage(), globalTags, new HologramPageState(), noOpLogger());
    }

    /** A bridge factory returning the per-viewer transform from {@code byViewer}, identity for an unknown uuid. */
    private static Function<UUID, UnaryOperator<String>> perViewer(Map<UUID, UnaryOperator<String>> byViewer) {
        return uuid -> byViewer.getOrDefault(uuid, UnaryOperator.identity());
    }

    private static Hologram text(String name, String line) {
        return Hologram.create(name(name), at(), List.of(new HologramLine(line)), Instant.EPOCH);
    }

    private static HologramName name(String value) {
        return new HologramName(value);
    }

    private static Position at() {
        return new Position(new WorldRef(UUID.randomUUID(), "world"), 0.0, 64.0, 0.0, 0.0f, 0.0f);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static Player player(UUID uuid) {
        return player(uuid, "Viewer");
    }

    private static Player player(UUID uuid, String name) {
        return (Player) java.lang.reflect.Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> {
                    if ("getUniqueId".equals(method.getName())) {
                        return uuid;
                    }
                    if ("getName".equals(method.getName())) {
                        return name;
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

    /** A recording fake of the lib text-override port: an override packet is a sentinel carrying id and text. */
    private static final class FakeDisplayTextPackets implements DisplayTextPackets {

        record TextOverride(int entityId, Component text) {}

        record Sent(Player viewer, int entityId, Component text) {}

        private final List<Sent> sends = new ArrayList<>();

        @Override
        public Object textOverride(int entityId, Component text) {
            return new TextOverride(entityId, text);
        }

        @Override
        public void send(Player viewer, Object packet) {
            TextOverride built = (TextOverride) packet;
            sends.add(new Sent(viewer, built.entityId(), built.text()));
        }

        Component textFor(UUID viewer) {
            return sends.stream()
                    .filter(sent -> viewer.equals(sent.viewer().getUniqueId()))
                    .map(Sent::text)
                    .findFirst()
                    .orElseThrow();
        }
    }
}
