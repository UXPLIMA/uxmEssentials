package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpPeopleMenu;
import com.uxplima.uxmessentials.playerwarps.application.ManageBans;
import com.uxplima.uxmessentials.playerwarps.application.ManageMembers;
import com.uxplima.uxmessentials.playerwarps.application.ManageWhitelist;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpBanStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpWhitelistStore;
import com.uxplima.uxmessentials.playerwarps.domain.BanRecord;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpMember;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PagedListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuTextPrompt;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The three people-management sub-menus ({@code pwarp-members} / {@code pwarp-whitelist} / {@code pwarp-bans}) the
 * manage panel opens. Proves the shipped specs load and render one snapshot row per person over a real engine, that a
 * row's left click reaches the matching removal use case ({@link ManageMembers#removeMember} /
 * {@link ManageWhitelist#unwhitelist} / {@link ManageBans#unban}), and that each add button threads a typed name through
 * the engine's {@code input:} step into the matching grant. The two members buttons fixing the CO_OWNER and MANAGER
 * roles, the whitelist button whitelisting, and the bans button imposing a permanent, reasonless ban. Drives the façade
 * through a synchronous scheduler so the async snapshot and the entity render run inline, and stands a recording prompt
 * in for the text-input seam so an add submit is driven by hand.
 */
class PwarpPeopleMenuTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneOffset.UTC);

    // The people menus' fixed bottom-row button slots, matching the three shipped specs.
    private static final int FIRST_ROW_SLOT = 0;
    private static final int MEMBERS_ADD_COOWNER_SLOT = 47;
    private static final int MEMBERS_ADD_MANAGER_SLOT = 48;
    private static final int WHITELIST_ADD_SLOT = 48;
    private static final int BANS_ADD_SLOT = 48;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private SyncScheduler scheduler;
    private RecordingPrompt prompt;

    private PlayerWarpRepository repository;
    private PlayerLookup players;
    private WarpMemberStore memberStore;
    private WarpWhitelistStore whitelistStore;
    private WarpBanStore banStore;
    private ManageMembers manageMembers;
    private ManageWhitelist manageWhitelist;
    private ManageBans manageBans;
    private PlayerWarpPeopleMenu menu;
    private PlayerWarp warp;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler = new SyncScheduler();
        repository = mock(PlayerWarpRepository.class);
        players = mock(PlayerLookup.class);
        memberStore = mock(WarpMemberStore.class);
        whitelistStore = mock(WarpWhitelistStore.class);
        banStore = mock(WarpBanStore.class);
        manageMembers = mock(ManageMembers.class);
        manageWhitelist = mock(ManageWhitelist.class);
        manageBans = mock(ManageBans.class);
        when(memberStore.list(any())).thenReturn(List.of());
        when(whitelistStore.list(any())).thenReturn(List.of());
        when(banStore.list(any())).thenReturn(List.of());
        warp = PlayerWarp.create(viewer, viewer.name(), PlayerWarpName.of("base"), at(), CLOCK.instant())
                .withId(PlayerWarpId.of(1));
        when(repository.findByName(warp.name())).thenReturn(Optional.of(warp));
        wireEngine();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void clickingAMemberRowRemovesThatMember() {
        UUID target = UUID.randomUUID();
        when(memberStore.list(PlayerWarpId.of(1))).thenReturn(List.of(new WarpMember(target, WarpRole.MANAGER, now())));
        when(players.findByUuid(target)).thenReturn(Optional.of(new PlayerRef(target, "Charlie")));
        menu.openMembers(viewer, warp.name());

        assertThat(top().getItem(FIRST_ROW_SLOT).getType()).isEqualTo(Material.PLAYER_HEAD);
        fireClick(FIRST_ROW_SLOT, ClickType.LEFT);

        verify(manageMembers)
                .removeMember(
                        eq(viewer), eq(warp.name()), argThat(ref -> ref.uuid().equals(target)));
    }

    @Test
    void addingACoOwnerThreadsTheTypedNameIntoAddMemberWithTheCoOwnerRole() {
        UUID granted = UUID.randomUUID();
        when(players.findByName("Bob")).thenReturn(Optional.of(new PlayerRef(granted, "Bob")));
        menu.openMembers(viewer, warp.name());

        fireClick(MEMBERS_ADD_COOWNER_SLOT, ClickType.LEFT); // opens the input prompt, grants nothing yet
        assertThat(prompt.prompts).isEqualTo(1);
        prompt.submit("Bob");

        verify(manageMembers)
                .addMember(
                        eq(viewer), eq(warp.name()), argThat(ref -> ref.uuid().equals(granted)), eq(WarpRole.CO_OWNER));
    }

    @Test
    void addingAManagerThreadsTheTypedNameIntoAddMemberWithTheManagerRole() {
        UUID granted = UUID.randomUUID();
        when(players.findByName("Bob")).thenReturn(Optional.of(new PlayerRef(granted, "Bob")));
        menu.openMembers(viewer, warp.name());

        fireClick(MEMBERS_ADD_MANAGER_SLOT, ClickType.LEFT);
        prompt.submit("Bob");

        verify(manageMembers)
                .addMember(
                        eq(viewer), eq(warp.name()), argThat(ref -> ref.uuid().equals(granted)), eq(WarpRole.MANAGER));
    }

    @Test
    void clickingAWhitelistRowUnwhitelistsThatPlayer() {
        UUID target = UUID.randomUUID();
        when(whitelistStore.list(PlayerWarpId.of(1))).thenReturn(List.of(target));
        when(players.findByUuid(target)).thenReturn(Optional.of(new PlayerRef(target, "Charlie")));
        menu.openWhitelist(viewer, warp.name());

        assertThat(top().getItem(FIRST_ROW_SLOT).getType()).isEqualTo(Material.PLAYER_HEAD);
        fireClick(FIRST_ROW_SLOT, ClickType.LEFT);

        verify(manageWhitelist)
                .unwhitelist(
                        eq(viewer), eq(warp.name()), argThat(ref -> ref.uuid().equals(target)));
    }

    @Test
    void addingToTheWhitelistThreadsTheTypedNameIntoWhitelist() {
        UUID granted = UUID.randomUUID();
        when(players.findByName("Bob")).thenReturn(Optional.of(new PlayerRef(granted, "Bob")));
        menu.openWhitelist(viewer, warp.name());

        fireClick(WHITELIST_ADD_SLOT, ClickType.LEFT);
        prompt.submit("Bob");

        verify(manageWhitelist)
                .whitelist(
                        eq(viewer), eq(warp.name()), argThat(ref -> ref.uuid().equals(granted)));
    }

    @Test
    void clickingABanRowLiftsThatBan() {
        UUID target = UUID.randomUUID();
        when(banStore.list(PlayerWarpId.of(1)))
                .thenReturn(
                        List.of(new BanRecord(target, Optional.empty(), Optional.empty(), Optional.empty(), now())));
        when(players.findByUuid(target)).thenReturn(Optional.of(new PlayerRef(target, "Charlie")));
        menu.openBans(viewer, warp.name());

        assertThat(top().getItem(FIRST_ROW_SLOT).getType()).isEqualTo(Material.PLAYER_HEAD);
        fireClick(FIRST_ROW_SLOT, ClickType.LEFT);

        verify(manageBans)
                .unban(eq(viewer), eq(warp.name()), argThat(ref -> ref.uuid().equals(target)));
    }

    @Test
    void addingABanThreadsTheTypedNameIntoAPermanentReasonlessBan() {
        UUID banned = UUID.randomUUID();
        when(players.findByName("Bob")).thenReturn(Optional.of(new PlayerRef(banned, "Bob")));
        menu.openBans(viewer, warp.name());

        fireClick(BANS_ADD_SLOT, ClickType.LEFT);
        prompt.submit("Bob");

        // A GUI ban is permanent and reasonless: empty duration, empty reason; the command owns timed/reasoned bans.
        verify(manageBans)
                .ban(
                        eq(viewer),
                        eq(warp.name()),
                        argThat(ref -> ref.uuid().equals(banned)),
                        eq(Optional.empty()),
                        eq(Optional.empty()));
    }

    // --- harness ---

    private void wireEngine() {
        MenuBindings bindings = new MenuBindings();
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        Menus menus = new Menus(renderer, scheduler, bindings.lists(), editorRenderer);
        prompt = new RecordingPrompt();
        MenuListener listener = new MenuListener(
                renderer,
                bindings.actions(),
                bindings.conditions(),
                scheduler,
                plugin,
                editorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener(),
                0L,
                System::currentTimeMillis,
                new PagedListSourceRegistry(),
                prompt);
        server.getPluginManager().registerEvents(listener, plugin);
        Notifier notifier = new Notifier(new KeyMessages(), noopSink());
        menu = new PlayerWarpPeopleMenu(
                menus,
                scheduler,
                repository,
                players,
                memberStore,
                whitelistStore,
                banStore,
                manageMembers,
                manageWhitelist,
                manageBans,
                new KeyMessages(),
                notifier,
                (ref, name) -> {});
        menu.register(bindings, dataFolder, NOOP);
    }

    private Inventory top() {
        return player.getOpenInventory().getTopInventory();
    }

    private void fireClick(int slot, ClickType click) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, click, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private static Instant now() {
        return CLOCK.instant();
    }

    private static Position at() {
        return Position.of(new WorldRef(UUID.randomUUID(), "world"), 0, 64, 0);
    }

    private static MessageSink noopSink() {
        return (viewer, renderedText) -> {};
    }

    /** A synchronous stand-in for the text-input seam: it records the callbacks so the test fires submit by hand. */
    private static final class RecordingPrompt implements MenuTextPrompt {
        int prompts;

        @Nullable Consumer<String> onSubmit;

        @Override
        public void prompt(
                org.bukkit.entity.Player player,
                String key,
                Component promptLabel,
                @Nullable String initialText,
                Consumer<String> onSubmit,
                Runnable onCancel) {
            this.prompts++;
            this.onSubmit = onSubmit;
        }

        void submit(String text) {
            java.util.Objects.requireNonNull(onSubmit, "onSubmit").accept(text);
        }
    }

    /** Renders every catalog key as its key path; the people rows carry no asserted text, only the head material. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

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
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
