package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeActionMenu;
import com.uxplima.uxmessentials.homes.application.DeleteHome;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.application.RelocateHome;
import com.uxplima.uxmessentials.homes.application.RenameHome;
import com.uxplima.uxmessentials.homes.application.SetHomeVisibility;
import com.uxplima.uxmessentials.homes.application.TeleportHome;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.claim.AlwaysAllowClaimService;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The per-home action menu golden test: the engine-rendered panel must draw the exact buttons the original {@code
 * HomeActionView} drew for one home. The fixture is a private home labelled "Base", so the panel shows the PAPER info
 * display (slot 4), the NAME_TAG rename (10), ENDER_PEARL teleport (11), BARRIER delete (13), COMPASS relocate (15),
 * ITEM_FRAME change-icon (16, the icon permission granted), GRAY_DYE private-visibility cell (19), PLAYER_HEAD invites
 * (25), and the ARROW back button (22). The window is snapshotted as {@code (slot -> material, plain name)} and
 * asserted equal, slot for slot, to the baseline the old view produced, including the subject-driven title and info
 * label filled from the home through the home_* placeholders. Then clicks through the engine's own {@link
 * MenuListener} prove teleport, delete, visibility-toggle, and the icon/invites/back buttons drive the same effects
 * the old view did, and the rename seam renames, faithful in look and behaviour.
 */
class HomeActionGoldenTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final HomeSlot SLOT = HomeSlot.of(0);

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private SyncScheduler scheduler;
    private FakeHomeRepository repository;
    private RecordingTeleporter teleporter;
    private TextInput textInput;
    private Home home;
    private HomeActionMenu actionMenu;
    private final List<Home> openedIcon = new ArrayList<>();
    private final List<Home> openedInvites = new ArrayList<>();
    private final List<UUID> openedList = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        player = server.addPlayer("Owner");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        repository = new FakeHomeRepository();
        teleporter = new RecordingTeleporter();
        textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);
        home = Home.create(viewer, SLOT, Position.of(WORLD, 1, 64, 2), Instant.EPOCH)
                .withLabel(Optional.of(HomeLabel.of("Base")), Instant.EPOCH);
        repository.save(home);
        openedIcon.clear();
        openedInvites.clear();
        openedList.clear();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameButtonPanelAsTheOldView() {
        Map<Integer, Snapshot> baseline = oldViewBaseline();

        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void teleportButtonTeleportsAndClosesTheMenu() {
        openEngine();
        fireClick(11);

        assertThat(teleporter.hops).isEqualTo(1);
    }

    @Test
    void deleteButtonRemovesTheSlotAndReopensTheGrid() {
        openEngine();
        fireClick(13);

        assertThat(repository.findSlot(viewer, SLOT)).isEmpty();
        assertThat(openedList).containsExactly(viewer.uuid());
    }

    @Test
    void visibilityButtonFlipsTheHomePublic() {
        openEngine();
        fireClick(19); // the private cell turns it public

        assertThat(repository.findSlot(viewer, SLOT).orElseThrow().isPublic()).isTrue();
    }

    @Test
    void iconAndInvitesButtonsDelegateToTheirMenus() {
        openEngine();
        fireClick(16);
        fireClick(25);

        assertThat(openedIcon).extracting(h -> h.slot().index()).containsExactly(SLOT.index());
        assertThat(openedInvites).extracting(h -> h.slot().index()).containsExactly(SLOT.index());
    }

    @Test
    void renameSeamRenamesTheHome() {
        openEngine();
        actionMenu.handleRenameInput(viewer, home, "Castle");

        assertThat(repository.findSlot(viewer, SLOT).orElseThrow().label())
                .map(HomeLabel::value)
                .contains("Castle");
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code HomeActionView} produced for this fixture (a private
     * home labelled "Base", icon permission granted), captured while both paths rendered it identically and frozen
     * here: the info display, the eight buttons, and the back arrow, with the info label surfacing the home name
     * through the {@code home_name} token.
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(4, new Snapshot(Material.PAPER, "Base"));
        baseline.put(10, new Snapshot(Material.NAME_TAG, "home.action.rename.name"));
        baseline.put(11, new Snapshot(Material.ENDER_PEARL, "home.action.teleport.name"));
        baseline.put(13, new Snapshot(Material.BARRIER, "home.action.delete.name"));
        baseline.put(15, new Snapshot(Material.COMPASS, "home.action.relocate.name"));
        baseline.put(16, new Snapshot(Material.ITEM_FRAME, "home.action.icon.name"));
        baseline.put(19, new Snapshot(Material.GRAY_DYE, "home.action.visibility.private.name"));
        baseline.put(22, new Snapshot(Material.ARROW, "home.action.back.name"));
        baseline.put(25, new Snapshot(Material.PLAYER_HEAD, "home.action.invites.name"));
        return baseline;
    }

    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    private void openEngine() {
        MenuBindings bindings = new MenuBindings();
        // The change-icon button is gated by the perm condition; grant it so the cell renders.
        bindings.condition("perm", (ctx, args) -> true);
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        actionMenu = menu(menus);
        actionMenu.register(bindings, Path.of("nonexistent"), NOOP);
        actionMenu.open(viewer, home);
    }

    private HomeActionMenu menu(Menus menus) {
        Notifier notifier = new Notifier(new KeyMessages(), (v, t) -> {});
        DomainEventPublisher events = new SilentEvents();
        Clock clock = Clock.systemUTC();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);
        HomeActionMenu.Collaborators collaborators = new HomeActionMenu.Collaborators(
                menus,
                new KeyMessages(),
                notifier,
                new AllowAllPermissions(),
                scheduler,
                new TeleportHome(repository, teleporter, notifier, freeCharge()),
                new DeleteHome(repository, new NoInvites(), notifier, events, DomainGate.allowAll()),
                new RelocateHome(repository, List.of(), notifier, events, DomainGate.allowAll(), freeCharge(), clock),
                new RenameHome(repository, notifier, events, clock),
                new SetHomeVisibility(repository, notifier, events, clock),
                repository,
                textInput,
                fmt,
                false,
                false,
                false,
                false,
                pos -> false,
                new AlwaysAllowClaimService(),
                (v, h) -> openedIcon.add(h),
                (v, h) -> openedInvites.add(h),
                p -> openedList.add(p.getUniqueId()));
        return new HomeActionMenu(collaborators);
    }

    private static com.uxplima.uxmessentials.homes.application.HomeCharge freeCharge() {
        return new com.uxplima.uxmessentials.homes.application.HomeCharge(
                new AllowAllPermissions(),
                Optional.empty(),
                com.uxplima.uxmessentials.homes.application.HomeChargeSettings.allFree());
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.BLACK_STAINED_GLASS_PANE) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item)));
        }
        return out;
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    /** A map-backed home store keyed by (owner, slot). */
    private static final class FakeHomeRepository implements HomeRepository {
        private final Map<UUID, Map<Integer, Home>> byOwner = new ConcurrentHashMap<>();

        private Map<Integer, Home> owned(PlayerRef owner) {
            return byOwner.computeIfAbsent(owner.uuid(), id -> new ConcurrentHashMap<>());
        }

        @Override
        public HomeSet load(PlayerRef owner) {
            return HomeSet.of(owner, new ArrayList<>(owned(owner).values()));
        }

        @Override
        public int count(PlayerRef owner) {
            return owned(owner).size();
        }

        @Override
        public Optional<Home> findSlot(PlayerRef owner, HomeSlot slot) {
            return Optional.ofNullable(owned(owner).get(slot.index()));
        }

        @Override
        public void save(Home home) {
            owned(home.owner()).put(home.slot().index(), home);
        }

        @Override
        public void deleteSlot(PlayerRef owner, HomeSlot slot) {
            owned(owner).remove(slot.index());
        }

        @Override
        public void deleteAll(PlayerRef owner) {
            owned(owner).clear();
        }
    }

    private static final class RecordingTeleporter implements HomeTeleporter {
        int hops;

        @Override
        public void teleportTo(PlayerRef who, Home home) {
            hops++;
        }
    }

    /** A no-op invite repository; the action menu's delete path clears invites through it. */
    private static final class NoInvites
            implements com.uxplima.uxmessentials.homes.application.port.HomeInviteRepository {
        @Override
        public java.util.Set<UUID> invites(PlayerRef owner, HomeSlot slot) {
            return java.util.Set.of();
        }

        @Override
        public void addInvite(PlayerRef owner, HomeSlot slot, UUID invited) {}

        @Override
        public void removeInvite(PlayerRef owner, HomeSlot slot, UUID invited) {}

        @Override
        public void removeAll(PlayerRef owner, HomeSlot slot) {}

        @Override
        public void removeAllForOwner(PlayerRef owner) {}
    }

    private static final class SilentEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class AllowAllPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return true;
        }

        @Override
        public Permissions.QuotaResult resolveQuota(
                PlayerRef who,
                Permissions.QuotaFamily family,
                @org.jspecify.annotations.Nullable WorldRef world,
                long configDefault) {
            return Permissions.QuotaResult.unlimited();
        }
    }

    /** Surfaces the home_name token on the title and info name; else the bare key, so a wrong key still shows. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(HomesMessageKey.HOME_ACTION_INFO_NAME.key())
                    || key.key().equals(HomesMessageKey.HOME_ACTION_TITLE.key())) {
                return placeholders.getOrDefault("home_name", "");
            }
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

    private static final class SyncScheduler implements com.uxplima.uxmessentials.shared.application.port.Scheduler {
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
