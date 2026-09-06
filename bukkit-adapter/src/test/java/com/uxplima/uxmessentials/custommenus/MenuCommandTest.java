package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.custommenus.adapter.CustomMenuLoader;
import com.uxplima.uxmessentials.custommenus.adapter.convert.DeluxeMenusConvertService;
import com.uxplima.uxmessentials.custommenus.adapter.convert.DeluxeMenusConverter;
import com.uxplima.uxmessentials.custommenus.adapter.convert.GuiPlusConvertService;
import com.uxplima.uxmessentials.custommenus.adapter.convert.GuiPlusConverter;
import com.uxplima.uxmessentials.custommenus.adapter.convert.OguiConvertService;
import com.uxplima.uxmessentials.custommenus.adapter.convert.OguiConverter;
import com.uxplima.uxmessentials.custommenus.adapter.convert.ZMenuConvertService;
import com.uxplima.uxmessentials.custommenus.adapter.convert.ZMenuConverter;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.MenuCommand;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.OpenCommandSpec;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecPersistence;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecWriter;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.LastMenu;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.LastMenuCleanupListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the operator {@code /menu} command through its real Brigadier node. A spec is registered
 * under {@code shop}; dispatching {@code /menu open shop} must land the viewer in a {@link MenuHolder}-backed
 * window, an unknown name replies with the not-found line and crashes nothing, {@code /menu list} surfaces the
 * registered names, and {@code /menu reload} re-runs the loader and reports the counts.
 */
class MenuCommandTest {

    private static final String SPEC_HOCON = """
            rows = 1
            items { x { slot = 0, material = STONE, name = "", click { left = ["close"] } } }
            """;

    private static final String TWO_ITEMS = """
            rows = 1
            items {
              a { slot = 0, material = STONE, name = "", click { left = ["close"] } }
              b { slots = [1,2], material = DIRT, name = "", click { left = ["close"], right = ["close"] } }
            }
            """;

    private ServerMock server;
    private PlayerMock player;
    private Menus menus;
    private LastMenu lastMenu;
    private MenuBindings bindings;
    private CustomMenuLoader loader;
    private RecordingMessages messages;
    private final List<String> names = new ArrayList<>();
    private final Map<String, OpenCommandSpec> openCommands = new java.util.HashMap<>();
    private final AtomicInteger reloads = new AtomicInteger();
    private final AtomicReference<String> executedFor = new AtomicReference<>();
    private final AtomicReference<String> executedArg = new AtomicReference<>();
    private final AtomicReference<String> editorOpenedFor = new AtomicReference<>();
    private final AtomicReference<String> capturedSlotFor = new AtomicReference<>();

    @TempDir
    Path menusDir;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Operator");
        player.setOp(true); // the happy-path dispatches hold both the use and admin nodes; a gate test drops them
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        lastMenu = new LastMenu();
        // The bindings back the action registry /menu execute dispatches through and the loader validates against:
        // close is the SPEC_HOCON item's click action, record captures the target's live context so the execute test
        // can assert what ran and for whom. The engine records a subject-less open into the tracker for /menu last.
        bindings = new MenuBindings();
        bindings.action("close", c -> {});
        bindings.action("record", c -> {
            executedFor.set(c.player().getName());
            executedArg.set(c.arg());
        });
        menus = new Menus(
                renderer,
                new SyncScheduler(),
                new ListSourceRegistry(),
                null,
                bindings.actions(),
                bindings.conditions(),
                lastMenu);
        loader = new CustomMenuLoader(new MenuSpecLoader(), bindings, menus, new NoopLogger());
        MenuSpec spec = new MenuSpecLoader().parse(SPEC_HOCON);
        menus.registerSpec("shop", spec);
        names.add("shop");
        messages = new RecordingMessages();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void openShowsTheRegisteredSpecInAMenuHolderWindow() {
        execute("menu open shop", player);

        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        assertThat(holder).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) holder).specId()).isEqualTo("shop");
    }

    @Test
    void openUnknownNameRepliesNotFoundWithoutCrashing() {
        execute("menu open ghost", player);

        assertThat(menuHolderIsOpenFor(player)).isFalse();
        assertThat(messages.keys).contains("menu.not-found");
    }

    @Test
    void openForOtherOpensTheMenuForTheNamedTarget() {
        PlayerMock steve = server.addPlayer("Steve");

        execute("menu open shop Steve", player);

        var top = steve.getOpenInventory().getTopInventory();
        assertThat(top.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) top.getHolder()).specId()).isEqualTo("shop");
        // The invoking operator opens it for the target, not for themselves.
        assertThat(menuHolderIsOpenFor(player)).isFalse();
        assertThat(messages.keys).contains("menu.opened-for");
    }

    @Test
    void openForOtherAttributesTheOpenToTheCommandSender() {
        PlayerMock steve = server.addPlayer("Steve");

        execute("menu open shop Steve", player);

        MenuHolder holder =
                (MenuHolder) steve.getOpenInventory().getTopInventory().getHolder();
        // The viewer is the target (so %player% and every player-scoped placeholder resolve against Steve), while the
        // executor is the operator who ran the command (so %executor% names the opener, distinct from %player%).
        assertThat(holder.ctx().viewer().name()).isEqualTo("Steve");
        assertThat(holder.ctx().executor().name()).isEqualTo("Operator");
    }

    @Test
    void openForOtherIsHiddenWithoutTheOthersNodeButSelfOpenStillWorks() {
        PlayerMock plain = server.addPlayer("Plain"); // holds use but not the open.others node
        plain.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.menu.use", true);
        server.addPlayer("Steve");

        // The target branch is invisible without the others node, so open-for-other fails to parse.
        executeExpectingDenial("menu open shop Steve", plain);
        // But opening the menu for oneself still works.
        execute("menu open shop", plain);

        assertThat(menuHolderIsOpenFor(plain)).isTrue();
    }

    @Test
    void openForOtherWithAnUnknownMenuNameRepliesNotFound() {
        PlayerMock steve = server.addPlayer("Steve");

        execute("menu open ghost Steve", player);

        assertThat(messages.keys).contains("menu.not-found");
        assertThat(menuHolderIsOpenFor(steve)).isFalse();
    }

    @Test
    void listSurfacesTheRegisteredNames() {
        execute("menu list", player);

        assertThat(messages.keys).contains("menu.list.header", "menu.list.entry");
        assertThat(messages.placeholdersFor("menu.list.entry")).containsEntry("name", "shop");
    }

    @Test
    void listOnAnEmptyRegistrySaysSo() {
        names.clear();

        execute("menu list", player);

        assertThat(messages.keys).contains("menu.list.empty");
    }

    @Test
    void reloadReRunsTheLoaderAndReportsCounts() {
        execute("menu reload", player);

        assertThat(reloads.get()).isEqualTo(1);
        assertThat(messages.keys).contains("menu.reloaded");
    }

    @Test
    void reloadOfASingleMenuReRegistersJustThatOne() throws Exception {
        Files.writeString(menusDir.resolve("shiny.conf"), SPEC_HOCON);

        execute("menu reload shiny", player);

        assertThat(messages.keys).contains("menu.reloaded-one");
        assertThat(messages.placeholdersFor("menu.reloaded-one"))
                .containsEntry("name", "shiny")
                .containsEntry("loaded", "1")
                .containsEntry("skipped", "0");
        assertThat(menus.registeredSpec("shiny")).isPresent();
        // The per-menu reload uses its own single-file seam, not the reload-all supplier.
        assertThat(reloads.get()).isZero();
    }

    @Test
    void reloadOfAnUnknownMenuRepliesNotFound() {
        execute("menu reload ghost", player);

        assertThat(messages.keys).contains("menu.not-found");
        assertThat(menus.registeredSpec("ghost")).isEmpty();
    }

    @Test
    void reloadOfASingleMenuIsGatedByTheAdminPermission() {
        PlayerMock plain = server.addPlayer("NoPerms"); // holds no admin node, so the reload branch stays hidden
        executeExpectingDenial("menu reload shiny", plain);
    }

    @Test
    void executeRunsTheActionForTheNamedTargetOnTheirContext() {
        server.addPlayer("Steve");

        execute("menu execute Steve record:hi", player);

        // The action ran on Steve's context (its live player is Steve) and reached the record handler with its arg.
        assertThat(executedFor.get()).isEqualTo("Steve");
        assertThat(executedArg.get()).isEqualTo("hi");
        assertThat(messages.keys).contains("menu.executed");
        assertThat(messages.placeholdersFor("menu.executed"))
                .containsEntry("name", "Steve")
                .containsEntry("action", "record:hi");
    }

    @Test
    void executeOnAnUnknownPlayerRepliesUnknownAndRunsNothing() {
        execute("menu execute Ghost record:hi", player); // Ghost was never added, so the selector matches nobody

        assertThat(messages.keys).contains("command.unknown-player");
        assertThat(executedFor.get()).isNull();
    }

    @Test
    void executeIsGatedByTheAdminPermission() {
        PlayerMock plain = server.addPlayer("NoPerms"); // holds no admin node, so the execute branch stays hidden
        server.addPlayer("Steve");

        executeExpectingDenial("menu execute Steve record:hi", plain);

        assertThat(executedFor.get()).isNull();
    }

    @Test
    void dumpListsTheTitleRowsAndEveryItem() {
        menus.registerSpec("panel", new MenuSpecLoader().parse(TWO_ITEMS));
        names.add("panel");

        execute("menu dump panel", player);

        assertThat(messages.keys).contains("menu.dump-header", "menu.dump-item");
        assertThat(messages.placeholdersFor("menu.dump-header"))
                .containsEntry("name", "panel")
                .containsEntry("rows", "1")
                .containsEntry("items", "2");
        List<Map<String, String>> items = messages.allPlaceholdersFor("menu.dump-item");
        assertThat(items).hasSize(2);
        assertThat(items).extracting(item -> item.get("id")).containsExactlyInAnyOrder("a", "b");
        assertThat(items).anySatisfy(item -> {
            assertThat(item.get("id")).isEqualTo("b");
            assertThat(item.get("slots")).isEqualTo("1,2");
            assertThat(item.get("material")).isEqualTo("DIRT");
            assertThat(item.get("actions")).isEqualTo("2"); // left + right, each one action
        });
    }

    @Test
    void dumpOfAnUnknownMenuRepliesNotFound() {
        execute("menu dump ghost", player);

        assertThat(messages.keys).contains("menu.not-found");
    }

    @Test
    void dumpIsGatedByTheAdminPermission() {
        PlayerMock plain = server.addPlayer("NoPerms"); // holds no admin node, so the dump branch stays hidden
        executeExpectingDenial("menu dump shop", plain);
    }

    @Test
    void metaShowsRowsItemCountAndFlagsOnOneLine() {
        execute("menu meta shop", player);

        assertThat(messages.keys).contains("menu.meta");
        assertThat(messages.allPlaceholdersFor("menu.meta"))
                .hasSize(1); // one compact summary line, not a per-item dump
        assertThat(messages.placeholdersFor("menu.meta"))
                .containsEntry("name", "shop")
                .containsEntry("rows", "1")
                .containsEntry("items", "1")
                .containsEntry("has-list", "false")
                .containsEntry("has-bedrock", "false")
                .containsEntry("chest-only", "false")
                .containsEntry("bottom-inventory", "false");
    }

    @Test
    void metaOfAnUnknownMenuRepliesNotFound() {
        execute("menu meta ghost", player);

        assertThat(messages.keys).contains("menu.not-found");
    }

    @Test
    void saveRewritesTheRegisteredSpecToAFileThatReloadsEqual() throws Exception {
        MenuSpec original = menus.registeredSpec("shop").orElseThrow();

        execute("menu save shop", player);

        assertThat(messages.keys).contains("menu.saved");
        assertThat(messages.placeholdersFor("menu.saved")).containsEntry("name", "shop");
        Path file = menusDir.resolve("shop.conf");
        assertThat(Files.exists(file)).isTrue();
        assertThat(new MenuSpecLoader().parse(Files.readString(file))).isEqualTo(original);
    }

    @Test
    void saveRoundTripsThroughTheRealLoaderIncludingTheCommandBlock() {
        OpenCommandSpec command = new OpenCommandSpec(
                "shop",
                List.of("store"),
                Optional.of("menu.shop"),
                Optional.of("<red>no"),
                false,
                List.of(new ArgumentSpec("target", ArgumentSpec.ArgType.ONLINE_PLAYER)),
                Optional.of("/shop"));
        openCommands.put("shop", command);
        MenuSpec original = menus.registeredSpec("shop").orElseThrow();

        execute("menu save shop", player);

        // The written file, re-read through the real loader, reproduces both the spec and its command {} block.
        CustomMenuLoader.LoadResult result = loader.loadFrom(menusDir);
        assertThat(menus.registeredSpec("shop")).contains(original);
        assertThat(result.openCommands().get("shop")).isEqualTo(command);
    }

    @Test
    void saveOfAnUnknownMenuRepliesNotFound() {
        execute("menu save ghost", player);

        assertThat(messages.keys).contains("menu.not-found");
        assertThat(Files.exists(menusDir.resolve("ghost.conf"))).isFalse();
    }

    @Test
    void saveOfASpecWithUnknownRefsIsRefusedAndWritesNoFile() {
        menus.registerSpec(
                "bad",
                new MenuSpecLoader()
                        .parse("rows = 1\nitems { x { slot = 0, material = STONE, click { left = [\"ghost\"] } } }"));
        names.add("bad");

        execute("menu save bad", player);

        assertThat(messages.keys).contains("menu.save-invalid");
        assertThat(messages.placeholdersFor("menu.save-invalid")).containsEntry("missing", "ghost");
        assertThat(Files.exists(menusDir.resolve("bad.conf"))).isFalse();
    }

    @Test
    void saveIsGatedByTheAdminPermission() {
        PlayerMock plain = server.addPlayer("NoPerms"); // holds no admin node, so the save branch stays hidden
        executeExpectingDenial("menu save shop", plain);

        assertThat(Files.exists(menusDir.resolve("shop.conf"))).isFalse();
    }

    @Test
    void editorOpensThePickerForThePlayer() {
        execute("menu editor", player);

        assertThat(editorOpenedFor.get()).isEqualTo("Operator");
    }

    @Test
    void editorFromConsoleRepliesPlayersOnly() {
        execute("menu editor", server.getConsoleSender());

        assertThat(messages.keys).contains("command.players-only");
        assertThat(editorOpenedFor.get()).isNull();
    }

    @Test
    void editorIsGatedByTheEditorPermission() {
        PlayerMock plain = server.addPlayer("NoPerms"); // holds no editor node, so the editor branch stays hidden
        executeExpectingDenial("menu editor", plain);

        assertThat(editorOpenedFor.get()).isNull();
    }

    @Test
    void captureItemRoutesTheSlotToTheCaptureHook() {
        execute("menu captureitem 5", player);

        assertThat(capturedSlotFor.get()).isEqualTo("Operator:5");
    }

    @Test
    void captureItemFromConsoleRepliesPlayersOnly() {
        execute("menu captureitem 5", server.getConsoleSender());

        assertThat(messages.keys).contains("command.players-only");
        assertThat(capturedSlotFor.get()).isNull();
    }

    @Test
    void captureItemIsGatedByTheEditorPermission() {
        PlayerMock plain = server.addPlayer("NoPerms"); // holds no editor node, so the branch stays hidden
        executeExpectingDenial("menu captureitem 5", plain);

        assertThat(capturedSlotFor.get()).isNull();
    }

    @Test
    void convertDeluxeMenusWritesAConfAndReportsTheCounts() throws Exception {
        Files.writeString(
                menusDir.resolve("shop.yml"),
                "menu_title: 'Shop'\nsize: 9\nitems:\n  a:\n    material: STONE\n    slot: 0\n");

        execute("menu convert deluxemenus shop.yml", player);

        assertThat(messages.keys).contains("menu.converted");
        assertThat(messages.placeholdersFor("menu.converted")).containsEntry("converted", "1");
        assertThat(Files.exists(menusDir.resolve("shop.conf"))).isTrue();
    }

    @Test
    void convertDeluxeMenusRepliesFailedWhenThePathMatchesNothing() {
        execute("menu convert deluxemenus ghost.yml", player);

        assertThat(messages.keys).contains("menu.convert-failed");
    }

    @Test
    void convertDeluxeMenusIsGatedByTheAdminPermission() {
        PlayerMock plain = server.addPlayer("NoPerms"); // holds no admin node, so the convert branch stays hidden
        executeExpectingDenial("menu convert deluxemenus shop.yml", plain);
    }

    @Test
    void convertZMenuWritesAConfAndReportsTheCounts() throws Exception {
        Files.writeString(
                menusDir.resolve("cookies.yml"),
                "name: 'Cookies'\nsize: 36\nitems:\n  c:\n    slot: 13\n    item:\n      material: COOKIE\n");

        execute("menu convert zmenu cookies.yml", player);

        assertThat(messages.keys).contains("menu.converted");
        assertThat(messages.placeholdersFor("menu.converted")).containsEntry("converted", "1");
        assertThat(Files.exists(menusDir.resolve("cookies.conf"))).isTrue();
    }

    @Test
    void convertZMenuRepliesFailedWhenThePathMatchesNothing() {
        execute("menu convert zmenu ghost.yml", player);

        assertThat(messages.keys).contains("menu.convert-failed");
    }

    @Test
    void convertZMenuIsGatedByTheAdminPermission() {
        PlayerMock plain = server.addPlayer("NoPerms"); // holds no admin node, so the convert branch stays hidden
        executeExpectingDenial("menu convert zmenu cookies.yml", plain);
    }

    @Test
    void convertOguiWritesAConfAndReportsTheCounts() throws Exception {
        Files.writeString(
                menusDir.resolve("shop.yml"),
                "shop:\n  title: 'Shop'\n  rows: 3\n  items:\n    '10':\n      material: DIAMOND\n");

        execute("menu convert ogui shop.yml", player);

        assertThat(messages.keys).contains("menu.converted");
        assertThat(messages.placeholdersFor("menu.converted")).containsEntry("converted", "1");
        assertThat(Files.exists(menusDir.resolve("shop.conf"))).isTrue();
    }

    @Test
    void convertOguiRepliesFailedWhenThePathMatchesNothing() {
        execute("menu convert ogui ghost.yml", player);

        assertThat(messages.keys).contains("menu.convert-failed");
    }

    @Test
    void convertOguiIsGatedByTheAdminPermission() {
        PlayerMock plain = server.addPlayer("NoPerms"); // holds no admin node, so the convert branch stays hidden
        executeExpectingDenial("menu convert ogui shop.yml", plain);
    }

    @Test
    void convertGuiPlusWritesAConfAndReportsTheCounts() throws Exception {
        Files.writeString(menusDir.resolve("shop.yml"), """
                type: chest
                rows: 3
                title: 'Shop'
                scenes:
                  '0':
                    items:
                      '1':
                        slot: 11
                        item: DIAMOND
                """);

        execute("menu convert guiplus shop.yml", player);

        assertThat(messages.keys).contains("menu.converted");
        assertThat(messages.placeholdersFor("menu.converted")).containsEntry("converted", "1");
        assertThat(Files.exists(menusDir.resolve("shop.conf"))).isTrue();
    }

    @Test
    void convertGuiPlusRepliesFailedWhenThePathMatchesNothing() {
        execute("menu convert guiplus ghost.yml", player);

        assertThat(messages.keys).contains("menu.convert-failed");
    }

    @Test
    void convertGuiPlusIsGatedByTheAdminPermission() {
        PlayerMock plain = server.addPlayer("NoPerms"); // holds no admin node, so the convert branch stays hidden
        executeExpectingDenial("menu convert guiplus shop.yml", plain);
    }

    @Test
    void lastReopensTheRecordedCustomMenuWithItsArguments() {
        // A subject-less open records into the tracker; the player closes it, then /menu last brings it back.
        menus.open(BukkitRefs.toRef(player), "shop", null, 0, Map.of("who", "Steve"));
        player.closeInventory();

        execute("menu last", player);

        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        assertThat(holder).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) holder).specId()).isEqualTo("shop");
        assertThat(((MenuHolder) holder).ctx().arguments()).containsEntry("who", "Steve");
    }

    @Test
    void lastReopensOnTheRecordedPage() {
        menus.open(BukkitRefs.toRef(player), "shop", null, 2, Map.of());
        player.closeInventory();

        execute("menu last", player);

        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        assertThat(((MenuHolder) holder).ctx().page()).isEqualTo(2);
    }

    @Test
    void lastWithNothingRecordedRepliesNoLast() {
        execute("menu last", player); // Operator opened nothing this test

        assertThat(messages.keys).contains("menu.no-last");
        assertThat(menuHolderIsOpenFor(player)).isFalse();
    }

    @Test
    void lastIgnoresASubjectMenuAndRepliesNoLast() {
        menus.registerSpec("warp-list", new MenuSpecLoader().parse(SPEC_HOCON));
        names.add("warp-list");
        // A feature menu carries a live domain subject, so the engine does not remember it for a blind reopen.
        menus.open(BukkitRefs.toRef(player), "warp-list", "a-subject", 0, Map.of());
        player.closeInventory();

        execute("menu last", player);

        assertThat(messages.keys).contains("menu.no-last");
        assertThat(menuHolderIsOpenFor(player)).isFalse();
    }

    @Test
    void lastWhoseMenuIsNoLongerRegisteredRepliesNoLastWithoutThrowing() {
        // A menu can be recorded (opened, then dropped by a reload) yet no longer be a registered spec. reopenLast
        // guards on the engine's registered specs, so a recorded-but-unregistered id replies no-last rather than
        // raising the loud unknown-spec failure a blind reopen would.
        lastMenu.record(player.getUniqueId(), new LastMenu.LastOpen("ghost", 0, Map.of()));

        execute("menu last", player);

        assertThat(messages.keys).contains("menu.no-last");
        assertThat(menuHolderIsOpenFor(player)).isFalse();
    }

    @Test
    void lastReplaysWithoutGrowingTheBackHistory() {
        // A single open records one entry; /menu last replays it without re-recording, so the history stays one deep
        // however many times it is reopened: a back from it then finds nothing beneath.
        menus.open(BukkitRefs.toRef(player), "shop", null, 0, Map.of());
        execute("menu last", player);
        execute("menu last", player);

        assertThat(lastMenu.back(player.getUniqueId())).isEmpty();
    }

    @Test
    void lastFromConsoleRepliesPlayersOnly() {
        execute("menu last", server.getConsoleSender());

        assertThat(messages.keys).contains("command.players-only");
    }

    @Test
    void quitClearsThePlayersRememberedMenu() {
        menus.open(BukkitRefs.toRef(player), "shop", null, 0, Map.of());
        assertThat(lastMenu.get(player.getUniqueId())).isPresent();

        new LastMenuCleanupListener(lastMenu)
                .onQuit(new PlayerQuitEvent(
                        player, net.kyori.adventure.text.Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertThat(lastMenu.get(player.getUniqueId())).isEmpty();
    }

    @Test
    void anEngineWiredWithoutATrackerRecordsNothing() {
        // The backward-compat path: a Menus built through the old ctor (no tracker) opens exactly as before and
        // remembers no reopen target, so /menu last over a still-empty tracker replies no-last.
        Menus plain = new Menus(
                new MenuRenderer(
                        new ItemRenderer(new GuiText(new KeyMessages()), new PlaceholderRegistry()),
                        new ConditionRegistry()),
                new SyncScheduler(),
                new ListSourceRegistry());
        plain.registerSpec("shop", new MenuSpecLoader().parse(SPEC_HOCON));
        plain.open(BukkitRefs.toRef(player), "shop", null, 0, Map.of());

        assertThat(lastMenu.get(player.getUniqueId())).isEmpty();
    }

    @Test
    void openFromConsoleRepliesPlayersOnly() {
        execute("menu open shop", server.getConsoleSender());

        assertThat(messages.keys).contains("command.players-only");
    }

    @Test
    void openIsGatedByTheUsePermission() {
        PlayerMock plain = server.addPlayer("NoPerms"); // not op, so the requires gate hides the open branch
        executeExpectingDenial("menu open shop", plain);

        assertThat(menuHolderIsOpenFor(plain)).isFalse();
    }

    @Test
    void reloadIsGatedByTheAdminPermission() {
        PlayerMock plain = server.addPlayer("NoPerms"); // not op, so the admin-gated reload branch stays hidden
        executeExpectingDenial("menu reload", plain);

        assertThat(reloads.get()).isZero();
    }

    private void execute(String input, org.bukkit.command.CommandSender who) {
        try {
            dispatcherFor().execute(input, CommandSourceStackMock.from(who));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    /**
     * Dispatch when the sender is expected to lack the gating node: Brigadier hides a {@code requires}-gated branch
     * from a sender without the permission, so the input fails to parse: that parse failure is the denial we assert.
     */
    private void executeExpectingDenial(String input, org.bukkit.command.CommandSender who) {
        try {
            dispatcherFor().execute(input, CommandSourceStackMock.from(who));
            throw new AssertionError("expected a permission denial for: " + input);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
            // The gated branch is invisible to this sender, so the dispatch is rejected: the denial we wanted.
        }
    }

    private CommandDispatcher<CommandSourceStack> dispatcherFor() {
        DeluxeMenusConvertService deluxeMenusConvert =
                new DeluxeMenusConvertService(menusDir, new DeluxeMenusConverter(), new NoopLogger());
        ZMenuConvertService zMenuConvert = new ZMenuConvertService(menusDir, new ZMenuConverter(), new NoopLogger());
        OguiConvertService oguiConvert = new OguiConvertService(menusDir, new OguiConverter(), new NoopLogger());
        GuiPlusConvertService guiPlusConvert =
                new GuiPlusConvertService(menusDir, new GuiPlusConverter(), new NoopLogger());
        MenuSpecPersistence persistence = new MenuSpecPersistence(new MenuSpecWriter(), bindings, new NoopLogger());
        MenuCommand command = new MenuCommand(
                menus,
                () -> List.copyOf(names),
                () -> {
                    reloads.incrementAndGet();
                    return new CustomMenuLoader.LoadResult(List.of("shop", "spawn"), List.of("broken"));
                },
                name -> loader.loadSingle(menusDir, name),
                deluxeMenusConvert,
                zMenuConvert,
                oguiConvert,
                guiPlusConvert,
                persistence,
                name -> java.util.Optional.ofNullable(openCommands.get(name)),
                player -> editorOpenedFor.set(player.getName()),
                (player, slot) -> capturedSlotFor.set(player.getName() + ":" + slot),
                menusDir,
                new SyncScheduler(),
                messages);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());
        return dispatcher;
    }

    /** True when {@code who}'s open top inventory is one of the menu engine's holder-backed windows. */
    private static boolean menuHolderIsOpenFor(PlayerMock who) {
        var top = who.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder;
    }

    /**
     * Records each delivered key and the placeholders it carried so a path's outcome is asserted by the line it
     * produced. {@link CommandFeedback} re-resolves the {@code prefix} key (with empty placeholders) on every send,
     * so the per-key map keeps the content key's own placeholders rather than being clobbered by that prefix pass.
     */
    private static final class RecordingMessages implements Messages {
        private final List<String> keys = new ArrayList<>();
        private final Map<String, Map<String, String>> byKey = new java.util.HashMap<>();
        private final Map<String, List<Map<String, String>>> allByKey = new java.util.HashMap<>();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            keys.add(key.key());
            byKey.put(key.key(), placeholders);
            allByKey.computeIfAbsent(key.key(), k -> new ArrayList<>()).add(placeholders);
            return key.key();
        }

        Map<String, String> placeholdersFor(String key) {
            return byKey.getOrDefault(key, Map.of());
        }

        /** Every placeholder map delivered under {@code key}, in send order: for a line the command emits per item. */
        List<Map<String, String>> allPlaceholdersFor(String key) {
            return allByKey.getOrDefault(key, List.of());
        }
    }

    /** A no-op logger for the convert service the command holds; these tests never exercise the convert path. */
    private static final class NoopLogger implements com.uxplima.uxmessentials.shared.application.port.Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** A pass-through messages double for the GuiText the renderer leans on (titles/names resolve to their key). */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class SyncScheduler implements Scheduler {
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
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(java.time.Duration delay, Runnable task) {
            task.run();
        }
    }
}
