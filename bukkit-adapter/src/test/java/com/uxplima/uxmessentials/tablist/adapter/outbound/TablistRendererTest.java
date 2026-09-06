package com.uxplima.uxmessentials.tablist.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationDef;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.AnimationSpec;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmessentials.tablist.domain.TablistContent;
import com.uxplima.uxmessentials.tablist.domain.TablistFiller;
import com.uxplima.uxmessentials.tablist.domain.TablistFormat;
import com.uxplima.uxmessentials.tablist.domain.TablistFormatConfig;
import com.uxplima.uxmessentials.tablist.domain.TablistLayout;
import com.uxplima.uxmessentials.tablist.domain.TablistRosterGroup;
import com.uxplima.uxmessentials.tablist.domain.TablistSkinSource;
import com.uxplima.uxmessentials.tablist.domain.TablistSlotRange;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoEntry;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoGameMode;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoPackets;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoValue;
import com.uxplima.uxmlib.packet.tablist.TabEntry;
import com.uxplima.uxmlib.packet.tablist.TabListPackets;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers the {@link TablistRenderer} render path under MockBukkit: a selected format applies its name format and sort
 * order, condition-driven selection picks the right format per viewer (a staff player gets the staff name/order, a
 * non-staff the default with neither), no matching format resets name/order to vanilla, the {@code {player}} token is
 * substituted with the viewer's name, the apply-only-on-change tracking does not re-send an unchanged name/order, and a
 * blacklisted world tears the selected format down. The skin path is covered too: a format with a {@code texture:} skin
 * delivers the row through a captured {@link TabListPackets} (with that {@link TabSkin}) rather than the native setters,
 * a no-skin format takes the native path and sends no packet, a {@code player:} skin resolves an online player's
 * texture, and the offline skin fetch is async with a no-skin fallback while in flight.
 *
 * <p>MockBukkit 4.108 implements {@code playerListName(Component)} and {@code setPlayerListOrder(int)} with real backing
 * state (the latter rejects a negative argument), so the applied values are read back directly rather than through the
 * unimplemented-setter technique the scoreboard number-format test uses. On real Paper 1.21.11 these are the same
 * setters that drive the client tab list.
 */
class TablistRendererTest {

    private static final String STAFF_NODE = "uxmessentials.staff";

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
    void aStaffPlayerGetsTheStaffNameAndOrderAndOthersGetTheDefault() {
        PlayerMock staff = server.addPlayer();
        staff.addAttachment(MockBukkit.createMockPlugin(), STAFF_NODE, true);
        PlayerMock regular = server.addPlayer();
        TablistFormatConfig config = new TablistFormatConfig(List.of(
                format("staff", new DisplayCondition.Permission(STAFF_NODE), 10, "<red>[Staff] {player}", 100),
                format("default", DisplayCondition.always(), 0, null, null)));
        TablistRenderer renderer = renderer(config);

        renderer.renderFor(staff);
        renderer.renderFor(regular);

        assertThat(plain(staff.playerListName())).isEqualTo("[Staff] " + staff.getName());
        assertThat(staff.getPlayerListOrder()).isEqualTo(100);
        // The default format set no name/order, so the regular player keeps the vanilla list name and order 0.
        assertThat(plain(regular.playerListName())).isEqualTo(regular.getName());
        assertThat(regular.getPlayerListOrder()).isZero();
    }

    @Test
    void noMatchingFormatResetsNameAndOrder() {
        PlayerMock player = server.addPlayer();
        AtomicReference<TablistFormatConfig> ref = new AtomicReference<>(new TablistFormatConfig(
                List.of(format("default", DisplayCondition.always(), 0, "<gold>{player}", 50))));
        TablistRenderer renderer = rendererOf(ref::get, new AnimationRegistry(List.of()), new RecordingPackets());

        renderer.renderFor(player);
        assertThat(plain(player.playerListName())).isEqualTo(player.getName());
        assertThat(player.getPlayerListOrder()).isEqualTo(50);

        // Swap to a config no format matches: the applied name/order must be reset to vanilla.
        ref.set(new TablistFormatConfig(
                List.of(format("staff", new DisplayCondition.Permission(STAFF_NODE), 0, "<red>x", 99))));
        renderer.renderFor(player);

        assertThat(plain(player.playerListName())).isEqualTo(player.getName());
        assertThat(player.getPlayerListOrder()).isZero();
    }

    @Test
    void doesNotReApplyTheNameOrOrderOnASteadyStateTick() {
        // No format switch -> the name/order are applied once and not re-sent. We cannot directly observe a missing
        // re-send through MockBukkit's plain setters, so we assert the value is stable across two identical paints —
        // the
        // tracking maps keep it consistent and the second paint must not throw.
        PlayerMock player = server.addPlayer();
        TablistRenderer renderer = renderer(
                new TablistFormatConfig(List.of(format("default", DisplayCondition.always(), 0, "<gold>{player}", 7))));

        renderer.renderFor(player);
        assertThatCode(() -> renderer.renderFor(player)).doesNotThrowAnyException();

        assertThat(plain(player.playerListName())).isEqualTo(player.getName());
        assertThat(player.getPlayerListOrder()).isEqualTo(7);
    }

    @Test
    void tearsDownAndResetsInABlacklistedWorld() {
        PlayerMock player = server.addPlayer();
        String world = player.getWorld().getName();
        TablistContent blacklisted =
                new TablistContent(List.of("<gold>Welcome"), List.of(), Duration.ofSeconds(1L), Set.of(world));
        TablistRenderer renderer = renderer(new TablistFormatConfig(List.of(new TablistFormat(
                "default",
                DisplayCondition.always(),
                0,
                blacklisted,
                Optional.of("<gold>{player}"),
                OptionalInt.of(5)))));

        renderer.renderFor(player);

        // A blacklisted world clears the tablist and leaves the player on the vanilla name/order.
        assertThat(plain(player.playerListName())).isEqualTo(player.getName());
        assertThat(player.getPlayerListOrder()).isZero();
    }

    @Test
    void substitutesThePlayerToken() {
        PlayerMock player = server.addPlayer();
        TablistRenderer renderer = renderer(new TablistFormatConfig(
                List.of(format("default", DisplayCondition.always(), 0, "<gold>Welcome {player}!", null))));

        renderer.renderFor(player);

        assertThat(plain(player.playerListName())).isEqualTo("Welcome " + player.getName() + "!");
    }

    @Test
    void anAnimationTokenInTheHeaderAndFooterRendersTheCurrentFrame() {
        // The header/footer are delivered through player.sendPlayerListHeaderAndFooter, which the stock PlayerMock
        // leaves
        // a no-op (so playerListHeader()/Footer() never update). A capturing PlayerMock records the components uxmLib's
        // Tablist hands the player, letting us assert the %anim_<name>% token resolved to the current frame in both.
        CapturingPlayerMock player = new CapturingPlayerMock(server, "anim");
        server.addPlayer(player);
        AnimationRegistry registry = new AnimationRegistry(List.of(AnimationDef.frames(
                new AnimationSpec("blink", AnimationSpec.AnimationType.FRAMES, List.of("ON", "OFF"), 1))));
        TablistContent content = new TablistContent(
                List.of("<gray>State: %anim_blink%"),
                List.of("<gray>Foot: %anim_blink%"),
                Duration.ofSeconds(1L),
                Set.of());
        TablistFormatConfig config = new TablistFormatConfig(List.of(new TablistFormat(
                "default", DisplayCondition.always(), 0, content, Optional.empty(), OptionalInt.empty())));
        TablistRenderer renderer = rendererOf(new AtomicReference<>(config)::get, registry, new RecordingPackets());

        // tick 0 -> frame index 0 ("ON"); the rendered header and footer both carry the current frame.
        renderer.renderFor(player);
        assertThat(plain(player.header())).isEqualTo("State: ON");
        assertThat(plain(player.footer())).isEqualTo("Foot: ON");

        // advance once -> frame index 1 ("OFF"); both follow the shared global clock.
        registry.advance();
        renderer.renderFor(player);
        assertThat(plain(player.header())).isEqualTo("State: OFF");
        assertThat(plain(player.footer())).isEqualTo("Foot: OFF");
    }

    @Test
    void aBuiltInTokenInTheHeaderAndFooterRendersTheLiveValue() {
        // The built-in {online}/{world} tokens resolve off the live player/server with no PlaceholderAPI present, so
        // the
        // shipped header/footer show real values out of the box.
        CapturingPlayerMock player = new CapturingPlayerMock(server, "builtin");
        server.addPlayer(player);
        TablistContent content = new TablistContent(
                List.of("<gray>Online {online}"), List.of("<gray>World {world}"), Duration.ofSeconds(1L), Set.of());
        TablistFormatConfig config = new TablistFormatConfig(List.of(new TablistFormat(
                "default", DisplayCondition.always(), 0, content, Optional.empty(), OptionalInt.empty())));
        TablistRenderer renderer = rendererOf(
                new AtomicReference<>(config)::get, new AnimationRegistry(List.of()), new RecordingPackets());

        renderer.renderFor(player);

        assertThat(plain(player.header())).isEqualTo("Online 1");
        assertThat(plain(player.footer()))
                .isEqualTo("World " + player.getWorld().getName());
    }

    @Test
    void aNameOnlyFormatDoesNotBlankTheHeaderOrFooterButAppliesTheName() {
        // A format with an EMPTY header AND footer must NOT call sendPlayerListHeaderAndFooter at all — uxmLib's
        // Tablist.set sends both together, so sending an empty pair would wipe whatever vanilla or another plugin set.
        // The send count being zero is the observable proof the tab header/footer was left untouched.
        CapturingPlayerMock player = new CapturingPlayerMock(server, "nameonly");
        server.addPlayer(player);
        TablistRenderer renderer =
                renderer(new TablistFormatConfig(List.of(nameOnlyFormat("default", "<gold>{player}", 42))));

        renderer.renderFor(player);

        assertThat(player.sendCount()).isZero();
        // The name and order still apply — a name-only/order-only format is fully functional.
        assertThat(plain(player.playerListName())).isEqualTo(player.getName());
        assertThat(player.getPlayerListOrder()).isEqualTo(42);
    }

    @Test
    void switchingFromAHeaderFormatToANameOnlyFormatClearsTheRenderersHeaderFooter() {
        // A player who had a header-having format and then switches to a name-only one must have THIS renderer's
        // header/footer cleared (an empty pair) rather than left stale — the second send is the clear.
        CapturingPlayerMock player = new CapturingPlayerMock(server, "switcher");
        server.addPlayer(player);
        AtomicReference<TablistFormatConfig> ref =
                new AtomicReference<>(new TablistFormatConfig(List.of(new TablistFormat(
                        "header",
                        DisplayCondition.always(),
                        0,
                        new TablistContent(
                                List.of("<gold>Welcome"), List.of("<gray>footer"), Duration.ofSeconds(1L), Set.of()),
                        Optional.of("<gold>{player}"),
                        OptionalInt.of(5)))));
        TablistRenderer renderer = rendererOf(ref::get, new AnimationRegistry(List.of()), new RecordingPackets());

        renderer.renderFor(player);
        assertThat(player.sendCount()).isEqualTo(1);
        assertThat(plain(player.header())).isEqualTo("Welcome");

        // Switch to a name-only format: the renderer clears its own header/footer (sends an empty pair).
        ref.set(new TablistFormatConfig(List.of(nameOnlyFormat("default", "<aqua>{player}", 9))));
        renderer.renderFor(player);

        assertThat(player.sendCount()).isEqualTo(2);
        assertThat(plain(player.header())).isEmpty();
        assertThat(plain(player.footer())).isEmpty();
    }

    @Test
    void aNameOnlyFormatLeavesAFreshPlayerUntouchedAcrossSteadyStateTicks() {
        // A player who never had a header/footer from this renderer keeps zero sends across repeated paints of a
        // name-only format — the renderer never blanks a tab it did not author.
        CapturingPlayerMock player = new CapturingPlayerMock(server, "steady");
        server.addPlayer(player);
        TablistRenderer renderer =
                renderer(new TablistFormatConfig(List.of(nameOnlyFormat("default", "<gold>{player}", 3))));

        renderer.renderFor(player);
        renderer.renderFor(player);

        assertThat(player.sendCount()).isZero();
    }

    @Test
    void aTextureSkinFormatPaintsTheRowThroughAPacketWithThatSkin() {
        PlayerMock player = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistSkinSource skin = new TablistSkinSource.Texture("dmFsdWU=", Optional.of("sig"));
        TablistRenderer renderer = rendererWith(packets, skinFormat("default", "<gold>{player}", 8, skin));

        renderer.renderFor(player);

        // The row is delivered through a packet carrying the texture, NOT the native list-name setter.
        assertThat(packets.added).hasSize(1);
        TabEntry entry = packets.added.get(0);
        assertThat(entry.id()).isEqualTo(player.getUniqueId());
        assertThat(entry.name()).isEqualTo(player.getName());
        assertThat(entry.listOrder()).isEqualTo(8);
        assertThat(plain(entry.displayName())).isEqualTo(player.getName());
        TabSkin painted = skinOf(entry);
        assertThat(painted.textureValue()).isEqualTo("dmFsdWU=");
        assertThat(painted.signature()).isEqualTo("sig");
        // The packet was broadcast to the one online viewer.
        assertThat(packets.sendCount()).isEqualTo(1);
        // The native list name was not touched for the skinned row.
        assertThat(plain(player.playerListName())).isEqualTo(player.getName());
    }

    @Test
    void aNoSkinFormatTakesTheNativePathAndSendsNoPacket() {
        PlayerMock player = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistRenderer renderer =
                rendererWith(packets, format("default", DisplayCondition.always(), 0, "<gold>{player}", 5));

        renderer.renderFor(player);

        assertThat(packets.added).isEmpty();
        assertThat(packets.sends).isZero();
        // The native path applied the name and order.
        assertThat(plain(player.playerListName())).isEqualTo(player.getName());
        assertThat(player.getPlayerListOrder()).isEqualTo(5);
    }

    @Test
    void doesNotReSendTheSkinPacketOnASteadyStateTick() {
        // Re-adding a real online player's entry every refresh tick would flicker. The packet is apply-on-change, so a
        // second identical paint sends nothing more.
        PlayerMock player = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistSkinSource skin = new TablistSkinSource.Texture("dmFsdWU=", Optional.empty());
        TablistRenderer renderer = rendererWith(packets, skinFormat("default", "<gold>{player}", 4, skin));

        renderer.renderFor(player);
        renderer.renderFor(player);

        assertThat(packets.added).hasSize(1);
        assertThat(packets.sendCount()).isEqualTo(1);
    }

    @Test
    void aPlayerNameSkinResolvesTheOnlinePlayersTexture() {
        PlayerMock player = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        FakeProfiles profiles = new FakeProfiles();
        profiles.online.put("Target", new TabSkin("targettex", "targetsig"));
        TablistSkinResolver resolver = new TablistSkinResolver(profiles, new InlineScheduler());
        TablistSkinSource skin = new TablistSkinSource.PlayerName("Target");
        TablistRenderer renderer = new TablistRenderer(
                new AtomicReference<>(
                        new TablistFormatConfig(List.of(skinFormat("default", "<gold>{player}", 1, skin))))::get,
                new AnimationRegistry(List.of()),
                packets,
                resolver,
                server::getOnlinePlayers,
                new InlineScheduler());

        renderer.renderFor(player);

        assertThat(packets.added).hasSize(1);
        assertThat(skinOf(packets.added.get(0)).textureValue()).isEqualTo("targettex");
    }

    @Test
    void anOfflineSkinFetchIsAsyncAndFallsBackToTheNativePathWhileInFlight() {
        PlayerMock player = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        FakeProfiles profiles = new FakeProfiles();
        profiles.fetchable.put("notch", new TabSkin("notchtex", null));
        DeferredScheduler scheduler = new DeferredScheduler();
        TablistSkinResolver resolver = new TablistSkinResolver(profiles, scheduler);
        TablistSkinSource skin = new TablistSkinSource.PlayerName("Notch");
        TablistRenderer renderer = new TablistRenderer(
                new AtomicReference<>(
                        new TablistFormatConfig(List.of(skinFormat("default", "<gold>{player}", 2, skin))))::get,
                new AnimationRegistry(List.of()),
                packets,
                resolver,
                server::getOnlinePlayers,
                new InlineScheduler());

        // First paint: the offline name is not cached yet, so the row falls back to the native path (no packet) and an
        // async fetch is scheduled but not yet run.
        renderer.renderFor(player);
        assertThat(packets.added).isEmpty();
        assertThat(plain(player.playerListName())).isEqualTo(player.getName());
        assertThat(scheduler.pending).hasSize(1);

        // Run the async fetch (off the tick thread); a later paint now finds the cached texture and paints the packet.
        scheduler.runAll();
        renderer.renderFor(player);

        assertThat(packets.added).hasSize(1);
        assertThat(skinOf(packets.added.get(0)).textureValue()).isEqualTo("notchtex");
    }

    @Test
    void aFailedOfflineFetchFallsBackToNoSkinAndNeverThrows() {
        PlayerMock player = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        // The fake has no fetchable entry for the name, so the resolver caches an empty result.
        FakeProfiles profiles = new FakeProfiles();
        DeferredScheduler scheduler = new DeferredScheduler();
        TablistSkinResolver resolver = new TablistSkinResolver(profiles, scheduler);
        TablistSkinSource skin = new TablistSkinSource.PlayerName("Ghost");
        TablistRenderer renderer = new TablistRenderer(
                new AtomicReference<>(
                        new TablistFormatConfig(List.of(skinFormat("default", "<gold>{player}", 1, skin))))::get,
                new AnimationRegistry(List.of()),
                packets,
                resolver,
                server::getOnlinePlayers,
                new InlineScheduler());

        renderer.renderFor(player);
        scheduler.runAll();
        // A second paint still finds no texture; the native path stands and nothing throws.
        assertThatCode(() -> renderer.renderFor(player)).doesNotThrowAnyException();

        assertThat(packets.added).isEmpty();
        assertThat(plain(player.playerListName())).isEqualTo(player.getName());
    }

    @Test
    void repaintsASkinnedPlayersEntryToALateJoiner() {
        // A is skinned while alone, then B joins later. Native name/order replicate to B, but the custom-skin packet
        // does not — so the join-time repaint must re-send A's custom-skin entry to B (and only B).
        PlayerMock a = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistSkinSource skin = new TablistSkinSource.Texture("YS10ZXg=", Optional.of("asig"));
        TablistRenderer renderer = rendererWith(packets, skinFormat("default", "<gold>{player}", 6, skin));

        renderer.renderFor(a);
        // B joins after A was skinned: the steady tick would early-return for A (unchanged tuple), so without the
        // repaint B never receives A's custom skin.
        PlayerMock b = server.addPlayer();
        renderer.repaintSkinsFor(b);

        List<TabEntry> toB = packets.entriesSentTo(b.getUniqueId());
        assertThat(toB).hasSize(1);
        TabEntry entry = toB.get(0);
        assertThat(entry.id()).isEqualTo(a.getUniqueId());
        assertThat(entry.listOrder()).isEqualTo(6);
        // The name format renders against A (the target), with the {player} token resolved to A's name.
        assertThat(plain(entry.displayName())).isEqualTo(a.getName());
        TabSkin painted = skinOf(entry);
        assertThat(painted.textureValue()).isEqualTo("YS10ZXg=");
        assertThat(painted.signature()).isEqualTo("asig");
    }

    @Test
    void aLateJoinerGetsTheirOwnSkinnedEntry() {
        // The joining viewer's own format carries a skin: renderFor paints it (broadcast to all viewers incl. self) and
        // the repaint re-sends it to self too, so a late joiner always sees their own custom skin.
        PlayerMock joiner = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistSkinSource skin = new TablistSkinSource.Texture("c2VsZg==", Optional.empty());
        TablistRenderer renderer = rendererWith(packets, skinFormat("default", "<gold>{player}", 3, skin));

        renderer.renderFor(joiner);
        renderer.repaintSkinsFor(joiner);

        List<TabEntry> toSelf = packets.entriesSentTo(joiner.getUniqueId());
        assertThat(toSelf).isNotEmpty();
        TabEntry own = toSelf.get(toSelf.size() - 1);
        assertThat(own.id()).isEqualTo(joiner.getUniqueId());
        assertThat(skinOf(own).textureValue()).isEqualTo("c2VsZg==");
    }

    @Test
    void doesNotRepaintAnUnskinnedPlayerToALateJoiner() {
        // A is on the native (no-skin) path, so the repaint must send nothing to a late joiner — only skinned targets
        // are repainted.
        PlayerMock a = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistRenderer renderer =
                rendererWith(packets, format("default", DisplayCondition.always(), 0, "<gold>{player}", 5));

        renderer.renderFor(a);
        PlayerMock b = server.addPlayer();
        renderer.repaintSkinsFor(b);

        assertThat(packets.entriesSentTo(b.getUniqueId())).isEmpty();
    }

    @Test
    void doesNotRepaintAnOfflineSkinnedTargetToALateJoiner() {
        // A is skinned, then quits (forget drops their tracking). A late joiner must not be repainted A's entry — only
        // currently-online skinned targets are repainted.
        PlayerMock a = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistSkinSource skin = new TablistSkinSource.Texture("Z29uZQ==", Optional.empty());
        TablistRenderer renderer = rendererWith(packets, skinFormat("default", "<gold>{player}", 1, skin));

        renderer.renderFor(a);
        renderer.forget(a);
        a.disconnect();
        PlayerMock b = server.addPlayer();
        renderer.repaintSkinsFor(b);

        assertThat(packets.entriesSentTo(b.getUniqueId())).isEmpty();
    }

    @Test
    void paintsAFillerEntryWithTheRightOrderTextAndSkin() {
        PlayerMock viewer = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistSkinSource skin = new TablistSkinSource.Texture("ZmlsbA==", Optional.of("fsig"));
        TablistFiller filler = new TablistFiller(5, "<gold>play.example.net", Optional.of(skin));
        TablistRenderer renderer = rendererWith(packets, fillerFormat("default", layoutOf(filler)));

        renderer.renderFor(viewer);

        List<TabEntry> toViewer = packets.entriesSentTo(viewer.getUniqueId());
        // One filler entry was sent to the viewer at slot 5's list order, carrying its text and skin.
        TabEntry entry = toViewer.stream()
                .filter(e -> e.id().equals(fillerId(viewer.getUniqueId(), 5)))
                .findFirst()
                .orElseThrow();
        assertThat(entry.listOrder()).isEqualTo(TablistLayout.slotToListOrder(5, TablistLayout.Direction.COLUMNS, 20));
        assertThat(plain(entry.displayName())).isEqualTo("play.example.net");
        assertThat(entry.name()).isEmpty();
        assertThat(skinOf(entry).textureValue()).isEqualTo("ZmlsbA==");
        assertThat(skinOf(entry).signature()).isEqualTo("fsig");
    }

    @Test
    void exactLayoutMaterializesEveryCellIncludingMissingAndBlankOnes() {
        PlayerMock viewer = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistLayout exact = new TablistLayout(
                List.of(
                        new TablistFiller(1, "<gold>Title", Optional.empty()),
                        new TablistFiller(2, " ", Optional.empty())),
                TablistLayout.Direction.COLUMNS,
                20,
                true);
        TablistRenderer renderer = rendererWith(packets, suppressFillerFormat("default", exact));

        renderer.renderFor(viewer);

        List<TabEntry> entries = packets.entriesSentTo(viewer.getUniqueId());
        assertThat(entries).hasSize(80);
        assertThat(plain(entries.get(0).displayName())).isEqualTo("Title");
        assertThat(plain(entries.get(1).displayName())).isBlank();
        assertThat(plain(entries.get(79).displayName())).isEmpty();
        assertThat(entries.get(79).id()).isEqualTo(fillerId(viewer.getUniqueId(), 80));
        assertThat(entries).extracting(TabEntry::name).doesNotContain("");
    }

    @Test
    void aRosterGroupSeatsThePlayersInTheCellsItOwns() {
        PlayerMock viewer = server.addPlayer("Alexia");
        PlayerMock other = server.addPlayer("Zed");
        RecordingPackets packets = new RecordingPackets();
        TablistRosterGroup group = new TablistRosterGroup(
                "players", List.of(new TablistSlotRange(21, 24)), DisplayCondition.always(), "<white>{player}");
        TablistLayout exact = new TablistLayout(
                List.of(new TablistFiller(1, "<gold>Players", Optional.empty())),
                List.of(group),
                TablistLayout.Direction.COLUMNS,
                20,
                true);
        TablistRenderer renderer = rendererWith(packets, suppressFillerFormat("default", exact));

        renderer.renderFor(viewer);
        renderer.renderFor(other);

        // The group owns cells 21 to 24; the two online players take the first two of them, by name, through their own
        // tab entries: the list name is the group's text and the list order is the cell's.
        assertThat(plain(viewer.playerListName())).isEqualTo("Alexia");
        assertThat(viewer.getPlayerListOrder())
                .isEqualTo(TablistLayout.slotToListOrder(21, TablistLayout.Direction.COLUMNS, 20));
        assertThat(plain(other.playerListName())).isEqualTo("Zed");
        assertThat(other.getPlayerListOrder())
                .isEqualTo(TablistLayout.slotToListOrder(22, TablistLayout.Direction.COLUMNS, 20));
        // A seated cell is drawn by the player, so the grid paints no synthetic cell over them.
        List<TabEntry> entries = packets.entriesSentTo(viewer.getUniqueId());
        assertThat(entries).hasSize(78);
        assertThat(entries).extracting(TabEntry::id).doesNotContain(fillerId(viewer.getUniqueId(), 21));
        assertThat(entries).extracting(TabEntry::id).doesNotContain(fillerId(viewer.getUniqueId(), 22));
        assertThat(plain(entries.get(0).displayName())).isEqualTo("Players");
    }

    @Test
    void aRosterGroupSeatsOnlyThePlayersItsConditionMatches() {
        PlayerMock viewer = server.addPlayer("Alexia");
        PlayerMock staff = server.addPlayer("Septy");
        staff.addAttachment(MockBukkit.createMockPlugin(), STAFF_NODE, true);
        RecordingPackets packets = new RecordingPackets();
        TablistRosterGroup staffGroup = new TablistRosterGroup(
                "staff",
                List.of(new TablistSlotRange(21, 22)),
                new DisplayCondition.Permission(STAFF_NODE),
                "<red>{player}");
        TablistRosterGroup everybody = new TablistRosterGroup(
                "players", List.of(new TablistSlotRange(23, 24)), DisplayCondition.always(), "<white>{player}");
        TablistLayout exact =
                new TablistLayout(List.of(), List.of(staffGroup, everybody), TablistLayout.Direction.COLUMNS, 20, true);
        TablistRenderer renderer = rendererWith(packets, suppressFillerFormat("default", exact));

        renderer.renderFor(viewer);
        renderer.renderFor(staff);

        // The staff group seats the one player it matches; a player it seated is not seated again by the group below.
        assertThat(staff.getPlayerListOrder())
                .isEqualTo(TablistLayout.slotToListOrder(21, TablistLayout.Direction.COLUMNS, 20));
        assertThat(viewer.getPlayerListOrder())
                .isEqualTo(TablistLayout.slotToListOrder(23, TablistLayout.Direction.COLUMNS, 20));
    }

    @Test
    void aRosterRowReadsThePlayerItIsAboutRatherThanTheViewer() {
        PlayerMock viewer = server.addPlayer("Alexia");
        PlayerMock other = server.addPlayer("Zed");
        RecordingPackets packets = new RecordingPackets();
        TablistRosterGroup group = new TablistRosterGroup(
                "players", List.of(new TablistSlotRange(21, 22)), DisplayCondition.always(), "<white>{player}");
        TablistLayout exact = new TablistLayout(List.of(), List.of(group), TablistLayout.Direction.COLUMNS, 20, true);
        TablistRenderer renderer = rendererWith(packets, suppressFillerFormat("default", exact));

        renderer.renderFor(viewer);
        renderer.renderFor(other);

        // Each seated row renders for its own player, so neither row carries the other player's name.
        assertThat(plain(viewer.playerListName())).isEqualTo("Alexia");
        assertThat(plain(other.playerListName())).isEqualTo("Zed");
    }

    @Test
    void theLayoutSkinDressesEveryCellTheGridPaints() {
        PlayerMock viewer = server.addPlayer("Alexia");
        RecordingPackets packets = new RecordingPackets();
        FakeProfiles profiles = new FakeProfiles();
        profiles.online.put("Alexia", new TabSkin("alexiatex", "alexiasig"));
        TablistRosterGroup group = new TablistRosterGroup(
                "players", List.of(new TablistSlotRange(21, 22)), DisplayCondition.always(), "<white>{player}");
        TablistLayout exact = new TablistLayout(
                List.of(new TablistFiller(1, "<gold>Players", Optional.empty())),
                List.of(group),
                Optional.of(new TablistSkinSource.Texture("blanktex", Optional.of("blanksig"))),
                TablistLayout.Direction.COLUMNS,
                20,
                true);
        TablistRenderer renderer = new TablistRenderer(
                new AtomicReference<>(new TablistFormatConfig(List.of(suppressFillerFormat("default", exact))))::get,
                new AnimationRegistry(List.of()),
                packets,
                new TablistSkinResolver(profiles, new InlineScheduler()),
                server::getOnlinePlayers,
                new InlineScheduler());

        renderer.renderFor(viewer);

        List<TabEntry> entries = packets.entriesSentTo(viewer.getUniqueId());
        // A written cell wears the layout's skin, and so does a cell nobody claimed, so no cell shows a default head.
        assertThat(skinOf(entries.get(0)).textureValue()).isEqualTo("blanktex");
        assertThat(skinOf(entries.get(entries.size() - 1)).textureValue()).isEqualTo("blanktex");
        // The one seated cell is the player's own entry, so the grid paints one cell fewer and never dresses it.
        assertThat(entries).hasSize(79);
        assertThat(entries).extracting(TabEntry::id).doesNotContain(fillerId(viewer.getUniqueId(), 21));
    }

    @Test
    void completePacketPortBatchesAnExactInitialPaintIntoOneAddPacket() {
        PlayerMock viewer = server.addPlayer();
        BatchRecordingPackets packets = new BatchRecordingPackets();
        TablistLayout exact = new TablistLayout(List.of(), TablistLayout.Direction.COLUMNS, 20, true);
        TablistRenderer renderer = rendererWith(packets, suppressFillerFormat("default", exact));

        renderer.renderFor(viewer);

        assertThat(packets.addBatches).hasSize(1);
        assertThat(packets.addBatches.get(0)).hasSize(80);
        assertThat(packets.sendCount()).isEqualTo(1);
    }

    @Test
    void realPlayerSortsAboveTheFillersIntoTheEarlySlot() {
        // With a filler grid and no explicit sort-order the viewer gets the real-player order (above every filler) so
        // they occupy an early slot rather than being interleaved with the fillers.
        PlayerMock viewer = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistFiller filler = new TablistFiller(1, "<gold>filler", Optional.empty());
        TablistRenderer renderer = rendererWith(packets, fillerFormat("default", layoutOf(filler)));

        renderer.renderFor(viewer);

        assertThat(viewer.getPlayerListOrder()).isEqualTo(TablistLayout.REAL_PLAYER_ORDER);
        int fillerOrder = TablistLayout.slotToListOrder(1, TablistLayout.Direction.COLUMNS, 20);
        assertThat(viewer.getPlayerListOrder()).isGreaterThan(fillerOrder);
    }

    @Test
    void usesAStableEntryIdAcrossUpdatesAndNeverLeaksAFreshRowPerTick() {
        // The same (viewer, slot) cell must keep the SAME entry id across changing text, so an update targets one entry
        // rather than leaking a new fake row each tick.
        PlayerMock viewer = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        AtomicReference<TablistFormatConfig> ref = new AtomicReference<>(new TablistFormatConfig(
                List.of(fillerFormat("default", layoutOf(new TablistFiller(2, "<gold>first", Optional.empty()))))));
        TablistRenderer renderer = new TablistRenderer(
                ref::get,
                new AnimationRegistry(List.of()),
                packets,
                new TablistSkinResolver(new FakeProfiles(), new InlineScheduler()),
                server::getOnlinePlayers,
                new InlineScheduler());

        renderer.renderFor(viewer);
        // Change the filler text at the same slot: the entry id must be identical, so it is an update, not a new row.
        ref.set(new TablistFormatConfig(
                List.of(fillerFormat("default", layoutOf(new TablistFiller(2, "<gold>second", Optional.empty()))))));
        renderer.renderFor(viewer);

        List<TabEntry> sent = packets.entriesSentTo(viewer.getUniqueId());
        UUID id = fillerId(viewer.getUniqueId(), 2);
        List<TabEntry> forSlot = sent.stream().filter(e -> e.id().equals(id)).toList();
        // Two paints, two updates to the SAME id (not two different random ids).
        assertThat(forSlot).hasSize(2);
        assertThat(forSlot).extracting(TabEntry::id).containsOnly(id);
        assertThat(plain(forSlot.get(0).displayName())).isEqualTo("first");
        assertThat(plain(forSlot.get(1).displayName())).isEqualTo("second");
    }

    @Test
    void doesNotReSendAnUnchangedFillerOnASteadyStateTick() {
        PlayerMock viewer = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistFiller filler = new TablistFiller(3, "<gold>static", Optional.empty());
        TablistRenderer renderer = rendererWith(packets, fillerFormat("default", layoutOf(filler)));

        renderer.renderFor(viewer);
        int after = packets.entriesSentTo(viewer.getUniqueId()).size();
        renderer.renderFor(viewer);

        // The second identical paint re-sends nothing for the unchanged filler cell.
        assertThat(packets.entriesSentTo(viewer.getUniqueId())).hasSize(after);
        assertThat(after).isEqualTo(1);
    }

    @Test
    void removesAFillerWhenSwitchingToAFormatThatDropsIt() {
        PlayerMock viewer = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        AtomicReference<TablistFormatConfig> ref = new AtomicReference<>(new TablistFormatConfig(
                List.of(fillerFormat("default", layoutOf(new TablistFiller(4, "<gold>filler", Optional.empty()))))));
        TablistRenderer renderer = new TablistRenderer(
                ref::get,
                new AnimationRegistry(List.of()),
                packets,
                new TablistSkinResolver(new FakeProfiles(), new InlineScheduler()),
                server::getOnlinePlayers,
                new InlineScheduler());

        renderer.renderFor(viewer);
        // Switch to a format with no fillers: the previously-painted filler entry must be removed by its id.
        ref.set(new TablistFormatConfig(List.of(format("default", DisplayCondition.always(), 0, "<gold>{player}", 5))));
        renderer.renderFor(viewer);

        assertThat(packets.removedFrom(viewer.getUniqueId())).contains(fillerId(viewer.getUniqueId(), 4));
    }

    @Test
    void removesOnlyTheFillerCellsThatFellAwayOnALayoutChange() {
        // Slot 1 stays, slot 2 is dropped: only slot 2's entry is removed, slot 1's stays painted.
        PlayerMock viewer = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        AtomicReference<TablistFormatConfig> ref = new AtomicReference<>(new TablistFormatConfig(List.of(fillerFormat(
                "default",
                layoutOf(
                        new TablistFiller(1, "<gold>one", Optional.empty()),
                        new TablistFiller(2, "<gold>two", Optional.empty()))))));
        TablistRenderer renderer = new TablistRenderer(
                ref::get,
                new AnimationRegistry(List.of()),
                packets,
                new TablistSkinResolver(new FakeProfiles(), new InlineScheduler()),
                server::getOnlinePlayers,
                new InlineScheduler());

        renderer.renderFor(viewer);
        ref.set(new TablistFormatConfig(
                List.of(fillerFormat("default", layoutOf(new TablistFiller(1, "<gold>one", Optional.empty()))))));
        renderer.renderFor(viewer);

        List<UUID> removed = packets.removedFrom(viewer.getUniqueId());
        assertThat(removed).containsExactly(fillerId(viewer.getUniqueId(), 2));
        assertThat(removed).doesNotContain(fillerId(viewer.getUniqueId(), 1));
    }

    @Test
    void forgetDropsFillerTrackingWithoutASpuriousRemovePacket() {
        // On quit the viewer's connection is closing, so — like the skin revert — forget drops the tracking without
        // sending a remove packet to a dead channel. A relog then re-paints the grid from scratch. The next paint after
        // a forget must therefore re-send the filler (the tracking was cleared), proving forget reset the state.
        PlayerMock viewer = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistRenderer renderer = rendererWith(
                packets, fillerFormat("default", layoutOf(new TablistFiller(6, "<gold>x", Optional.empty()))));

        renderer.renderFor(viewer);
        int beforeForget = packets.entriesSentTo(viewer.getUniqueId()).size();
        renderer.forget(viewer);
        // forget sends no remove packet (the channel is closing); it only drops the tracking.
        assertThat(packets.removedFrom(viewer.getUniqueId())).isEmpty();

        // Re-paint after forget: the filler is sent again because the tracking was cleared (a steady tick would not).
        renderer.renderFor(viewer);
        assertThat(packets.entriesSentTo(viewer.getUniqueId())).hasSize(beforeForget + 1);
    }

    @Test
    void clearRemovesTheFillerEntries() {
        PlayerMock viewer = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistRenderer renderer = rendererWith(
                packets, fillerFormat("default", layoutOf(new TablistFiller(8, "<gold>x", Optional.empty()))));

        renderer.renderFor(viewer);
        renderer.clear(viewer);

        assertThat(packets.removedFrom(viewer.getUniqueId())).contains(fillerId(viewer.getUniqueId(), 8));
    }

    @Test
    void aDeferredFillerSkinPaintsSkinlessWhilePendingThenRepaintsWithTheSkinOnceResolved() {
        // FINDING-1 mirror of the offline-skin row test: an offline player: filler is painted skinless while the fetch
        // is in flight, but is NOT recorded as the steady state, so a later tick (the texture now cached) re-sends the
        // entry WITH the skin rather than leaving the filler Steve forever.
        PlayerMock viewer = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        FakeProfiles profiles = new FakeProfiles();
        profiles.fetchable.put("notch", new TabSkin("notchtex", null));
        DeferredScheduler scheduler = new DeferredScheduler();
        TablistSkinResolver resolver = new TablistSkinResolver(profiles, scheduler);
        TablistSkinSource skin = new TablistSkinSource.PlayerName("Notch");
        TablistRenderer renderer = new TablistRenderer(
                new AtomicReference<>(new TablistFormatConfig(List.of(fillerFormat(
                        "default", layoutOf(new TablistFiller(5, "<gold>play.example.net", Optional.of(skin)))))))::get,
                new AnimationRegistry(List.of()),
                packets,
                resolver,
                server::getOnlinePlayers,
                new InlineScheduler());

        // First paint: the offline name is not cached yet, so the filler is painted skinless and an async fetch queued.
        renderer.renderFor(viewer);
        UUID id = fillerId(viewer.getUniqueId(), 5);
        List<TabEntry> firstPaint = packets.entriesSentTo(viewer.getUniqueId()).stream()
                .filter(e -> e.id().equals(id))
                .toList();
        assertThat(firstPaint).hasSize(1);
        assertThat(firstPaint.get(0).skin()).isNull();
        assertThat(scheduler.pending).hasSize(1);

        // Run the async fetch off the tick thread; a later paint now finds the cached texture and repaints WITH it.
        scheduler.runAll();
        renderer.renderFor(viewer);

        List<TabEntry> all = packets.entriesSentTo(viewer.getUniqueId()).stream()
                .filter(e -> e.id().equals(id))
                .toList();
        // Two paints to the same cell id: the first skinless, the second carrying the resolved texture.
        assertThat(all).hasSize(2);
        assertThat(skinOf(all.get(1)).textureValue()).isEqualTo("notchtex");
        assertThat(packets.removedFrom(viewer.getUniqueId())).contains(id);
    }

    @Test
    void aSuppressFormatEntersSuppressModeAndASwitchAwayRestoresRealPlayers() {
        // The renderer drives the TAB-C suppression off the selected format's flag: a suppress=true format enters
        // suppress mode (inject + relist real players unlisted), and a switch to a non-suppress format restores them.
        PlayerMock viewer = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        RecordingGate gate = new RecordingGate();
        TablistSuppression suppression = new TablistSuppression(
                gate,
                new com.uxplima.uxmlib.pipeline.PacketListenerRegistry(),
                packets,
                server::getOnlinePlayers,
                new TestLogger(),
                (p, s) -> null);
        AtomicReference<TablistFormatConfig> ref =
                new AtomicReference<>(new TablistFormatConfig(List.of(suppressFormat("synthetic"))));
        TablistRenderer renderer = new TablistRenderer(
                ref::get,
                new AnimationRegistry(List.of()),
                packets,
                new TablistSkinResolver(new FakeProfiles(), new InlineScheduler()),
                server::getOnlinePlayers,
                new InlineScheduler(),
                suppression);

        renderer.renderFor(viewer);
        assertThat(gate.injected).contains(viewer.getUniqueId());

        // Switch to a plain format with no suppression: the renderer takes the viewer back out of suppress mode.
        ref.set(new TablistFormatConfig(List.of(format("default", DisplayCondition.always(), 0, "<gold>{player}", 5))));
        renderer.renderFor(viewer);
        assertThat(gate.ejected).contains(viewer.getUniqueId());
    }

    @Test
    void clearTakesAViewerOutOfSuppressMode() {
        PlayerMock viewer = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        RecordingGate gate = new RecordingGate();
        TablistSuppression suppression = new TablistSuppression(
                gate,
                new com.uxplima.uxmlib.pipeline.PacketListenerRegistry(),
                packets,
                server::getOnlinePlayers,
                new TestLogger(),
                (p, s) -> null);
        TablistRenderer renderer = new TablistRenderer(
                new AtomicReference<>(new TablistFormatConfig(List.of(suppressFormat("synthetic"))))::get,
                new AnimationRegistry(List.of()),
                packets,
                new TablistSkinResolver(new FakeProfiles(), new InlineScheduler()),
                server::getOnlinePlayers,
                new InlineScheduler(),
                suppression);

        renderer.renderFor(viewer);
        renderer.clear(viewer);

        assertThat(gate.ejected).contains(viewer.getUniqueId());
    }

    @Test
    void aFillerPaintedWhileAlreadySuppressedIsProtectedAtSendTimeAndNotForcedUnlisted() {
        // Regression: a filler added to the layout while the viewer is ALREADY suppressed must be in the suppress
        // snapshot at the instant its ADD_PLAYER crosses the live interceptor. Otherwise that first paint is force-
        // unlisted and the per-cell flicker guard never repaints it, leaving the new filler permanently hidden. The
        // gate's interceptor is modelled by consulting the live suppress predicate at packet-send time for every filler
        // entry that goes out — exactly what NmsPlayerInfoUpdates.forceUnlisted does on the wire.
        PlayerMock viewer = server.addPlayer();
        SnapshotProbingPackets packets = new SnapshotProbingPackets();
        TablistSuppression suppression = new TablistSuppression(
                new RecordingGate(),
                new com.uxplima.uxmlib.pipeline.PacketListenerRegistry(),
                packets,
                server::getOnlinePlayers,
                new TestLogger(),
                (p, s) -> null);
        packets.bind(suppression);
        AtomicReference<TablistFormatConfig> ref =
                new AtomicReference<>(new TablistFormatConfig(List.of(suppressFillerFormat("synthetic", layoutOf()))));
        TablistRenderer renderer = new TablistRenderer(
                ref::get,
                new AnimationRegistry(List.of()),
                packets,
                new TablistSkinResolver(new FakeProfiles(), new InlineScheduler()),
                server::getOnlinePlayers,
                new InlineScheduler(),
                suppression);

        // Enter suppress mode with no fillers, then grow the layout to add a filler while still suppressed.
        renderer.renderFor(viewer);
        ref.set(new TablistFormatConfig(List.of(suppressFillerFormat(
                "synthetic", layoutOf(new TablistFiller(5, "<gold>play.example.net", Optional.empty()))))));
        renderer.renderFor(viewer);

        UUID fillerId = fillerId(viewer.getUniqueId(), 5);
        // The filler entry was actually sent, and at the moment it crossed the (modelled) interceptor the suppress
        // predicate kept it LISTED — proving the snapshot already protected it before the packet went out.
        assertThat(packets.sentFillerIds).contains(fillerId);
        assertThat(packets.fillerIdsThatWouldBeHidden).doesNotContain(fillerId);
    }

    @Test
    void aNoFillerFormatPaintsNoFillerEntriesAndRemovesNothing() {
        PlayerMock viewer = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistRenderer renderer =
                rendererWith(packets, format("default", DisplayCondition.always(), 0, "<gold>{player}", 5));

        renderer.renderFor(viewer);

        assertThat(packets.added).isEmpty();
        assertThat(packets.removedFrom(viewer.getUniqueId())).isEmpty();
        // The no-filler format leaves the native name/order path untouched.
        assertThat(plain(viewer.playerListName())).isEqualTo(viewer.getName());
        assertThat(viewer.getPlayerListOrder()).isEqualTo(5);
    }

    /**
     * A PlayerMock that records the header/footer components handed to {@code sendPlayerListHeaderAndFooter} and counts
     * the sends. The stock PlayerMock leaves that call a no-op, so this is the only way to observe whether the renderer
     * sent a header/footer at all — the send count distinguishes "never touched" from "cleared to empty".
     */
    private static final class CapturingPlayerMock extends PlayerMock {
        private @Nullable Component lastHeader;
        private @Nullable Component lastFooter;
        private int sendCount;

        CapturingPlayerMock(ServerMock server, String name) {
            super(server, name);
        }

        @Override
        public void sendPlayerListHeaderAndFooter(Component header, Component footer) {
            this.lastHeader = header;
            this.lastFooter = footer;
            this.sendCount++;
        }

        Component header() {
            return java.util.Objects.requireNonNull(lastHeader, "header not sent");
        }

        Component footer() {
            return java.util.Objects.requireNonNull(lastFooter, "footer not sent");
        }

        int sendCount() {
            return sendCount;
        }
    }

    /**
     * A fake {@link TabListPackets} that records each built add entry and counts sends. The packet object is the entry,
     * so a recorded {@code (viewer, packet)} send pair carries the {@link TabEntry} that reached that viewer — this lets
     * a late-joiner test assert which entries a single viewer received.
     */
    private static class RecordingPackets implements TabListPackets {
        private final List<TabEntry> added = new ArrayList<>();
        private final List<Sent> sent = new ArrayList<>();
        private final List<List<UUID>> removed = new ArrayList<>();
        private int sends;

        @Override
        public Object addOrUpdate(TabEntry entry) {
            added.add(entry);
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
            Removal removal = new Removal(List.copyOf(ids));
            removed.add(removal.ids());
            return removal;
        }

        @Override
        public Object relist(List<UUID> ids, boolean listed) {
            return new Relist(List.copyOf(ids), listed);
        }

        @Override
        public void send(org.bukkit.entity.Player viewer, Object packet) {
            sends++;
            sent.add(new Sent(viewer.getUniqueId(), packet));
        }

        /** The add entries that reached a single viewer, in send order. */
        List<TabEntry> entriesSentTo(UUID viewer) {
            List<TabEntry> entries = new ArrayList<>();
            for (Sent s : sent) {
                if (s.viewer().equals(viewer) && s.packet() instanceof TabEntry entry) {
                    entries.add(entry);
                }
            }
            return entries;
        }

        /** Every profile id that reached {@code viewer} through a remove packet, flattened across all removals. */
        List<UUID> removedFrom(UUID viewer) {
            List<UUID> ids = new ArrayList<>();
            for (Sent s : sent) {
                if (s.viewer().equals(viewer) && s.packet() instanceof Removal removal) {
                    ids.addAll(removal.ids());
                }
            }
            return ids;
        }

        int sendCount() {
            return sends;
        }

        private record Sent(UUID viewer, Object packet) {}

        private record Removal(List<UUID> ids) {}

        private record Relist(List<UUID> ids, boolean listed) {}
    }

    /** The production-shaped dual port: complete player-info batches plus the compatibility facade. */
    private static final class BatchRecordingPackets extends RecordingPackets implements PlayerInfoPackets {
        private final List<List<PlayerInfoEntry>> addBatches = new ArrayList<>();

        @Override
        public Object addOrUpdate(List<PlayerInfoEntry> entries) {
            List<PlayerInfoEntry> batch = List.copyOf(entries);
            addBatches.add(batch);
            return batch;
        }

        @Override
        public Object displayNames(List<PlayerInfoValue<Component>> entries) {
            return List.copyOf(entries);
        }

        @Override
        public Object listOrders(List<PlayerInfoValue<Integer>> entries) {
            return List.copyOf(entries);
        }

        @Override
        public Object listed(List<PlayerInfoValue<Boolean>> entries) {
            return List.copyOf(entries);
        }

        @Override
        public Object latencies(List<PlayerInfoValue<Integer>> entries) {
            return List.copyOf(entries);
        }

        @Override
        public Object gameModes(List<PlayerInfoValue<PlayerInfoGameMode>> entries) {
            return List.copyOf(entries);
        }

        @Override
        public Object showHat(List<PlayerInfoValue<Boolean>> entries) {
            return List.copyOf(entries);
        }

        @Override
        public Object removeEntries(List<UUID> ids) {
            return List.copyOf(ids);
        }

        @Override
        public void sendPacket(org.bukkit.entity.Player viewer, Object packet) {
            send(viewer, packet);
        }
    }

    /**
     * A {@link RecordingPackets} that models the live suppress interceptor: at {@code addOrUpdate} send time it asks the
     * bound {@link TablistSuppression}'s predicate whether the entry's id would be forced unlisted, so a test can prove a
     * filler is protected at the exact moment its packet crosses the wire — the ordering the real defect turned on.
     */
    private static final class SnapshotProbingPackets extends RecordingPackets {
        private final List<UUID> sentFillerIds = new ArrayList<>();
        private final List<UUID> fillerIdsThatWouldBeHidden = new ArrayList<>();
        private @Nullable TablistSuppression suppression;

        void bind(TablistSuppression suppression) {
            this.suppression = suppression;
        }

        @Override
        public void send(org.bukkit.entity.Player viewer, Object packet) {
            super.send(viewer, packet);
            if (suppression != null && packet instanceof TabEntry entry) {
                sentFillerIds.add(entry.id());
                if (suppression.suppressPredicate(viewer.getUniqueId()).test(entry.id())) {
                    fillerIdsThatWouldBeHidden.add(entry.id());
                }
            }
        }
    }

    /** A fake profile source: an online map for inline reads, a fetchable map for the "off-thread" fetch. */
    private static final class FakeProfiles implements MojangProfileSource {
        private final java.util.Map<String, TabSkin> online = new java.util.HashMap<>();
        private final java.util.Map<String, TabSkin> fetchable = new java.util.HashMap<>();

        @Override
        public Optional<TabSkin> onlineTexture(String name) {
            return Optional.ofNullable(online.get(name));
        }

        @Override
        public Optional<TabSkin> fetchTexture(String name) {
            return Optional.ofNullable(fetchable.get(name.toLowerCase(java.util.Locale.ROOT)));
        }
    }

    /** A scheduler that runs every hop inline — used where the resolver's async fetch should complete immediately. */
    private static final class InlineScheduler extends NoopScheduler {
        @Override
        public void async(Runnable task) {
            task.run();
        }
    }

    /** A scheduler that queues async tasks so a test can assert the fetch was deferred, then run it explicitly. */
    private static final class DeferredScheduler extends NoopScheduler {
        private final List<Runnable> pending = new ArrayList<>();

        @Override
        public void async(Runnable task) {
            pending.add(task);
        }

        void runAll() {
            List<Runnable> snapshot = List.copyOf(pending);
            pending.clear();
            snapshot.forEach(Runnable::run);
        }
    }

    /** The hops the resolver does not use default to inline; only {@code async} matters for the skin fetch seam. */
    private abstract static class NoopScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(com.uxplima.uxmessentials.shared.domain.Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(com.uxplima.uxmessentials.shared.domain.PlayerRef player, Runnable task) {
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

    private TablistRenderer renderer(TablistFormatConfig config) {
        return rendererOf(new AtomicReference<>(config)::get, new AnimationRegistry(List.of()), new RecordingPackets());
    }

    private TablistRenderer rendererWith(RecordingPackets packets, TablistFormat... formats) {
        return new TablistRenderer(
                new AtomicReference<>(new TablistFormatConfig(List.of(formats)))::get,
                new AnimationRegistry(List.of()),
                packets,
                new TablistSkinResolver(new FakeProfiles(), new InlineScheduler()),
                server::getOnlinePlayers,
                new InlineScheduler());
    }

    private TablistRenderer rendererOf(
            java.util.function.Supplier<TablistFormatConfig> config,
            AnimationRegistry animations,
            RecordingPackets packets) {
        return new TablistRenderer(
                config,
                animations,
                packets,
                new TablistSkinResolver(new FakeProfiles(), new InlineScheduler()),
                server::getOnlinePlayers,
                new InlineScheduler());
    }

    private static TablistFormat format(
            String name,
            DisplayCondition condition,
            int priority,
            @Nullable String nameFormat,
            @Nullable Integer sortOrder) {
        TablistContent content =
                new TablistContent(List.of("<gold>" + name), List.of(), Duration.ofSeconds(1L), Set.of());
        return new TablistFormat(
                name,
                condition,
                priority,
                content,
                Optional.ofNullable(nameFormat),
                sortOrder == null ? OptionalInt.empty() : OptionalInt.of(sortOrder));
    }

    /** A format with an EMPTY header AND footer (a name-only / order-only format) plus the given name/order. */
    private static TablistFormat nameOnlyFormat(String name, @Nullable String nameFormat, @Nullable Integer sortOrder) {
        TablistContent blank = new TablistContent(List.of(), List.of(), Duration.ofSeconds(1L), Set.of());
        return new TablistFormat(
                name,
                DisplayCondition.always(),
                0,
                blank,
                Optional.ofNullable(nameFormat),
                sortOrder == null ? OptionalInt.empty() : OptionalInt.of(sortOrder));
    }

    /** A name-only format carrying a custom-skin source so the packet path is exercised. */
    private static TablistFormat skinFormat(
            String name, @Nullable String nameFormat, @Nullable Integer sortOrder, TablistSkinSource skin) {
        TablistContent blank = new TablistContent(List.of(), List.of(), Duration.ofSeconds(1L), Set.of());
        return new TablistFormat(
                name,
                DisplayCondition.always(),
                0,
                blank,
                Optional.ofNullable(nameFormat),
                sortOrder == null ? OptionalInt.empty() : OptionalInt.of(sortOrder),
                Optional.of(skin));
    }

    /** A header-only format carrying a fixed-slot filler {@code layout} so the filler grid path is exercised. */
    private static TablistFormat fillerFormat(String name, TablistLayout layout) {
        TablistContent content =
                new TablistContent(List.of("<gold>" + name), List.of(), Duration.ofSeconds(1L), Set.of());
        return new TablistFormat(
                name,
                DisplayCondition.always(),
                0,
                content,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                layout);
    }

    /** A header-only format with {@code suppress-real-players = true} so the TAB-C suppression path is exercised. */
    private static TablistFormat suppressFormat(String name) {
        TablistContent content =
                new TablistContent(List.of("<gold>" + name), List.of(), Duration.ofSeconds(1L), Set.of());
        return new TablistFormat(
                name,
                DisplayCondition.always(),
                0,
                content,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                TablistLayout.empty(),
                true);
    }

    /** A header-only format carrying both a filler {@code layout} and {@code suppress-real-players = true}. */
    private static TablistFormat suppressFillerFormat(String name, TablistLayout layout) {
        TablistContent content =
                new TablistContent(List.of("<gold>" + name), List.of(), Duration.ofSeconds(1L), Set.of());
        return new TablistFormat(
                name,
                DisplayCondition.always(),
                0,
                content,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                layout,
                true);
    }

    /** A connection gate that records which viewers were injected/ejected by the suppression lifecycle. */
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

    /** A no-op logger so the renderer's suppression collaborator has somewhere to route a fail-soft fault. */
    private static final class TestLogger implements com.uxplima.uxmessentials.shared.application.port.Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    private static TablistLayout layoutOf(TablistFiller... fillers) {
        return new TablistLayout(List.of(fillers), TablistLayout.Direction.COLUMNS, 20);
    }

    /** The deterministic filler entry id the renderer mints for a (viewer, slot) cell — mirrors TablistRenderer. */
    private static UUID fillerId(UUID viewer, int slot) {
        return UUID.nameUUIDFromBytes(
                ("uxmf:" + viewer + ":" + slot).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static TabSkin skinOf(TabEntry entry) {
        return java.util.Objects.requireNonNull(entry.skin(), "entry skin");
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
