package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.ModerationJailedMenu;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.application.Unjail;
import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.BanEntry;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailEntry;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.MuteEntry;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The jailed-players golden test: the engine-rendered release list must draw the exact grid the original
 * {@code JailedPlayersView} drew. The store holds two active jails ("Alpha" in jail "north", "Beta" in jail
 * "south"), so the list draws two PLAYER_HEAD icons (content slots 0 and 1. Each player's name surfaces through the
 * {@code mod_jailed_player} token) and the two ARROW nav buttons (slots 48 and 50). The engine window is snapshotted
 * as {@code (slot -> material, plain name)} and asserted equal, slot for slot, to the baseline the old view produced
 * for this fixture, then frozen here as the contract so the old class could be deleted. Then a left click on the
 * first head through the engine's own {@link MenuListener} proves the migrated path releases that player through the
 * same audited {@code Unjail} use case {@code /unjail} takes. Captured by the recording {@code Sanctions} the use
 * case calls.
 *
 * <p>The {@code KeyMessages} catalog surfaces the entry name's {@code mod_jailed_player} token, so a jailed player's
 * name appears in the rendered label; every other key renders verbatim. A real rendering difference (a wrong key, a
 * wrong material, a misplaced cell, a lost name token) therefore still shows up as a snapshot mismatch.
 */
class ModerationJailedListGoldenTest {

    private static final UUID ALPHA = UUID.randomUUID();
    private static final UUID BETA = UUID.randomUUID();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeRepository repository;
    private RecordingSanctions sanctions;
    private Unjail unjail;

    private final java.nio.file.Path dataFolder = java.nio.file.Path.of("nonexistent");

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Staff");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        repository = new FakeRepository();
        sanctions = new RecordingSanctions();
        Notifier notifier = new Notifier(new KeyMessages(), new SilentSink());
        unjail = new Unjail(repository, sanctions, notifier, new SilentAudit(), new SilentEvents(), Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameHeadGridAndNavAsTheOldView() {
        repository.jail(ALPHA, "north");
        repository.jail(BETA, "south");

        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void clickingAJailedPlayerThroughTheEngineReleasesThemThroughUnjail() {
        repository.jail(ALPHA, "north");
        openEngine();

        fireClick(0); // content slot 0 holds Alpha; a left click must release them

        // Unjail releases the target through the Sanctions adapter for an online target, so the recording adapter
        // captured Alpha's UUID: the audited release path ran for the clicked player.
        assertThat(sanctions.released).containsExactly(ALPHA);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code JailedPlayersView} produced for this fixture (two
     * active jails, "Alpha" in "north" and "Beta" in "south"), captured while both paths rendered it identically and
     * frozen here: two PLAYER_HEAD icons (content slots 0 and 1. The names surface through the {@code
     * mod_jailed_player} token) and the two nav ARROWs (slots 48 and 50).
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.PLAYER_HEAD, "Alpha"));
        baseline.put(1, new Snapshot(Material.PLAYER_HEAD, "Beta"));
        baseline.put(48, new Snapshot(Material.ARROW, "moderation.gui.jail.jailed-prev"));
        baseline.put(50, new Snapshot(Material.ARROW, "moderation.gui.jail.jailed-next"));
        return baseline;
    }

    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    private void openEngine() {
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());

        ModerationJailedMenu menu = new ModerationJailedMenu(
                menus, scheduler, unjail, repository, new FakeLookup(), Clock.system(ZoneOffset.UTC));
        menu.register(bindings, dataFolder, NOOP);
        menu.open(viewer);
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The slot -> (material, plain name) map for every non-empty, non-filler slot of {@code inv}. */
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

    /** A jail-state store keyed by UUID; the menu reads {@code activeJails} and the use case reads/writes jail state. */
    private static final class FakeRepository implements ModerationRepository {
        private final Map<UUID, JailState> jails = new ConcurrentHashMap<>();
        private final List<JailEntry> active = new ArrayList<>();

        void jail(UUID target, String jail) {
            jails.put(target, JailState.permanent(jail, Issuer.console("Console"), Optional.empty(), Instant.EPOCH));
            active.add(new JailEntry(
                    target, jail, Issuer.console("Console"), Optional.empty(), Optional.empty(), Optional.empty()));
        }

        @Override
        public List<JailEntry> activeJails(Instant now, int limit) {
            return List.copyOf(active);
        }

        @Override
        public ModerationProfile load(PlayerRef target) {
            return new ModerationProfile(target, MuteState.none(), loadJail(target), TempbanState.none());
        }

        @Override
        public MuteState loadMute(PlayerRef target) {
            return MuteState.none();
        }

        @Override
        public TempbanState loadTempban(PlayerRef target) {
            return TempbanState.none();
        }

        @Override
        public JailState loadJail(PlayerRef target) {
            return jails.getOrDefault(target.uuid(), JailState.none());
        }

        @Override
        public void saveJail(PlayerRef target, JailState jail) {
            if (jail instanceof JailState.None) {
                jails.remove(target.uuid());
            } else {
                jails.put(target.uuid(), jail);
            }
        }

        @Override
        public List<BanEntry> activeBans(Instant now, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<MuteEntry> activeMutes(Instant now, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveMute(PlayerRef target, MuteState mute) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveTempban(PlayerRef target, TempbanState tempban) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int appendWarn(PlayerRef target, Warn warn) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Warn> warns(PlayerRef target, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int clearWarns(PlayerRef target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int clearWarnsByActor(PlayerRef target, PlayerRef actor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveIpBan(IpBan ban) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeIpBan(String ip) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<IpBan> activeIpBan(String ip, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordSeen(PlayerRef who, Optional<String> ip, Instant at) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<SeenRecord> seen(PlayerRef who) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void ensureUserExists(PlayerRef target, Instant at) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isLockedDown() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLockedDown(boolean enabled) {
            throw new UnsupportedOperationException();
        }
    }

    /** Records every release so the test can assert the audited unjail ran for the clicked target. */
    private static final class RecordingSanctions implements Sanctions {
        private final List<UUID> released = new ArrayList<>();

        @Override
        public void releaseFromJail(PlayerRef target) {
            released.add(target.uuid());
        }

        @Override
        public void kick(PlayerRef target, MessageKey reasonKey, String reasonText) {}

        @Override
        public void freeze(PlayerRef target) {}

        @Override
        public void unfreeze(PlayerRef target) {}

        @Override
        public boolean isFrozen(PlayerRef target) {
            return false;
        }

        @Override
        public void sendToJail(PlayerRef target, String jail) {}

        @Override
        public java.util.Collection<PlayerRef> onlinePlayers() {
            return List.of();
        }
    }

    private static final class FakeLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            if (uuid.equals(ALPHA)) {
                return Optional.of(new PlayerRef(uuid, "Alpha"));
            }
            if (uuid.equals(BETA)) {
                return Optional.of(new PlayerRef(uuid, "Beta"));
            }
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return true;
        }
    }

    private static final class SilentAudit implements ModerationAudit {
        @Override
        public void muted(
                PlayerRef actor, PlayerRef target, Optional<String> duration, boolean ok, Optional<String> r) {}

        @Override
        public void unmuted(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

        @Override
        public void jailed(
                PlayerRef actor,
                PlayerRef target,
                String jail,
                Optional<String> duration,
                boolean ok,
                Optional<String> reason) {}

        @Override
        public void unjailed(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

        @Override
        public void tempbanned(PlayerRef actor, PlayerRef target, String duration, boolean ok, Optional<String> r) {}

        @Override
        public void unbanned(PlayerRef actor, PlayerRef target, boolean ok) {}

        @Override
        public void warned(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

        @Override
        public void clearedWarns(PlayerRef actor, PlayerRef target, boolean ok, int count) {}

        @Override
        public void kicked(PlayerRef actor, PlayerRef target, boolean ok, Optional<String> reason) {}

        @Override
        public void kickedAll(PlayerRef actor, int affected, Optional<String> reason) {}

        @Override
        public void froze(PlayerRef actor, PlayerRef target, boolean frozen, boolean ok) {}

        @Override
        public void ipBanned(
                PlayerRef actor,
                String targetIp,
                Optional<UUID> target,
                Optional<String> duration,
                boolean ok,
                Optional<String> reason) {}

        @Override
        public void ipUnbanned(PlayerRef actor, String targetIp, boolean ok) {}

        @Override
        public void altDetected(UUID uuid, String ip, List<UUID> matchedAlts, boolean kicked) {}

        @Override
        public void jailLocationDefined(PlayerRef actor, String jail) {}

        @Override
        public void jailLocationRemoved(PlayerRef actor, String jail) {}

        @Override
        public void lockdown(UUID actor, boolean enabled) {}
    }

    private static final class SilentEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class SilentSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    /**
     * Surfaces the entry name's {@code mod_jailed_player} token so a jailed player's name appears; else the bare key.
     * The engine resolves a {@code @key} line through a lambda {@link MessageKey} carrying only the key string, so the
     * match is by {@link MessageKey#key()} rather than enum identity.
     */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(ModerationMessageKey.MOD_GUI_JAILED_ENTRY_NAME.key())) {
                return placeholders.getOrDefault("mod_jailed_player", "");
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
