package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeInvitesMenu;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.application.InviteToHome;
import com.uxplima.uxmessentials.homes.application.ListHomeInvites;
import com.uxplima.uxmessentials.homes.application.UninviteFromHome;
import com.uxplima.uxmessentials.homes.application.port.HomeInviteRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
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
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
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
 * The invited-players list golden test: the engine-rendered list must draw the exact menu the original {@code
 * InvitedPlayersMenu} drew. The home has two invited players ("alpha", "beta"), so the list draws two PLAYER_HEAD
 * heads in the first two inner cells (slots 10 and 11, sorted by name), the LIME_DYE add button (slot 49), the
 * ARROW back button (slot 45), and the two ARROW nav buttons (slots 48 and 50). The window is snapshotted as
 * {@code (slot -> material, plain name)} and asserted equal, slot for slot, to the baseline the old view produced
 * captured while both rendered the same fixture, then frozen here so the old class could be deleted. Then a head
 * click, an add submission, and the back button through the engine's own {@link MenuListener} prove the migrated path
 * revokes an invite, invites a resolved player, and returns to the action menu, faithful in look and behaviour.
 */
class HomeInvitesGoldenTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final HomeSlot SLOT = HomeSlot.of(0);

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeHomeRepository repository;
    private FakeInviteRepository invites;
    private FakeLookup lookup;
    private TextInput textInput;
    private Home home;
    private final List<Home> reopenedAction = new ArrayList<>();

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
        invites = new FakeInviteRepository();
        lookup = new FakeLookup();
        textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);
        home = Home.create(viewer, SLOT, Position.of(WORLD, 0, 64, 0), Instant.EPOCH);
        repository.save(home);
        reopenedAction.clear();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameHeadsAddBackAndNavAsTheOldView() {
        invitePlayer("alpha");
        invitePlayer("beta");
        Map<Integer, Snapshot> baseline = oldViewBaseline();

        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void clickingAHeadThroughTheEngineRevokesThatInvite() {
        UUID alpha = invitePlayer("alpha");
        openEngine();
        // Content slot 10 is the first inner cell, "alpha"; clicking it revokes that invite.
        fireClick(10);

        assertThat(invites.invites(viewer, SLOT)).doesNotContain(alpha);
    }

    @Test
    void addingAResolvedNameThroughTheEngineInvitesThatPlayer() {
        openEngine();
        UUID carol = lookup.register("carol");

        HomeInvitesMenu menu = menu(menusFor());
        menu.addByName(viewer, home, "carol");

        assertThat(invites.invites(viewer, SLOT)).contains(carol);
    }

    @Test
    void clickingBackThroughTheEngineReopensTheActionMenu() {
        invitePlayer("alpha");
        openEngine();
        fireClick(45); // the back button

        assertThat(reopenedAction).extracting(h -> h.slot().index()).containsExactly(SLOT.index());
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code InvitedPlayersMenu} produced for this fixture (two
     * invited players "alpha" and "beta"), captured while both paths rendered it identically and frozen here as the
     * contract: two PLAYER_HEAD heads in the first inner cells (slots 10 and 11, names through {@code invited_player}),
     * the LIME_DYE add button (slot 49), the ARROW back button (slot 45), and the two nav ARROWs (slots 48 and 50).
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(10, new Snapshot(Material.PLAYER_HEAD, "alpha"));
        baseline.put(11, new Snapshot(Material.PLAYER_HEAD, "beta"));
        baseline.put(45, new Snapshot(Material.ARROW, "home.invites.back"));
        baseline.put(48, new Snapshot(Material.ARROW, "home.invites.prev"));
        baseline.put(49, new Snapshot(Material.LIME_DYE, "home.invites.add.name"));
        baseline.put(50, new Snapshot(Material.ARROW, "home.invites.next"));
        return baseline;
    }

    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    private void openEngine() {
        Menus menus = menusFor();
        menu(menus).open(viewer, home);
    }

    private Menus menusFor() {
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        menu(menus).register(bindings, Path.of("nonexistent"), NOOP);
        return menus;
    }

    private HomeInvitesMenu menu(Menus menus) {
        Notifier notifier = new Notifier(new KeyMessages(), (v, t) -> {});
        return new HomeInvitesMenu(
                menus,
                scheduler,
                new KeyMessages(),
                new ListHomeInvites(invites),
                new InviteToHome(repository, invites, notifier),
                new UninviteFromHome(invites, notifier),
                lookup,
                notifier,
                textInput,
                (p, v, h) -> reopenedAction.add(h));
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private UUID invitePlayer(String name) {
        UUID id = lookup.register(name);
        invites.addInvite(viewer, SLOT, id);
        return id;
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

    /** A map-backed invite store keyed by (owner, slot). */
    private static final class FakeInviteRepository implements HomeInviteRepository {
        private final Map<String, Set<UUID>> bySlot = new ConcurrentHashMap<>();

        private static String key(PlayerRef owner, HomeSlot slot) {
            return owner.uuid() + ":" + slot.index();
        }

        @Override
        public Set<UUID> invites(PlayerRef owner, HomeSlot slot) {
            return Set.copyOf(bySlot.getOrDefault(key(owner, slot), Set.of()));
        }

        @Override
        public void addInvite(PlayerRef owner, HomeSlot slot, UUID invited) {
            bySlot.computeIfAbsent(key(owner, slot), k -> ConcurrentHashMap.newKeySet())
                    .add(invited);
        }

        @Override
        public void removeInvite(PlayerRef owner, HomeSlot slot, UUID invited) {
            bySlot.computeIfAbsent(key(owner, slot), k -> ConcurrentHashMap.newKeySet())
                    .remove(invited);
        }

        @Override
        public void removeAll(PlayerRef owner, HomeSlot slot) {
            bySlot.remove(key(owner, slot));
        }

        @Override
        public void removeAllForOwner(PlayerRef owner) {
            bySlot.keySet().removeIf(k -> k.startsWith(owner.uuid() + ":"));
        }
    }

    /** Resolves names to stable uuids registered up front, so a head's label and an add submission both resolve. */
    private static final class FakeLookup implements PlayerLookup {
        private final Map<String, UUID> byName = new ConcurrentHashMap<>();
        private final Map<UUID, String> byId = new ConcurrentHashMap<>();

        UUID register(String name) {
            UUID id = UUID.randomUUID();
            byName.put(name, id);
            byId.put(id, name);
            return id;
        }

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.ofNullable(byName.get(name)).map(id -> new PlayerRef(id, name));
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.ofNullable(byId.get(uuid)).map(name -> new PlayerRef(uuid, name));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return byId.containsKey(uuid);
        }
    }

    /** Surfaces the entry name's {@code invited_player} token; else the bare key, so a wrong key still shows. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(HomesMessageKey.HOME_INVITES_ENTRY_NAME.key())) {
                return placeholders.getOrDefault("invited_player", "");
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
