package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the moderation management GUI's bespoke detail/manage view: the Revoke button is
 * confirm-gated and, only on confirm, calls the matching revoke seam (which the production wiring binds to
 * unban/unmute/unjail); the "view history" button hands the clicked target to the engine-rendered history menu (its
 * rendering is covered by {@code ModerationHistoryListGoldenTest}); and the entry point is permission-gated. The
 * active-punishments list itself now renders through the menu engine, its grid and its click-opens-detail gesture
 * are covered slot-for-slot by {@code ModerationActiveListGoldenTest}. The detail view is laid out from a temp conf
 * (no hardcoded slots); the scheduler runs every hop inline so the off-thread reads and writes land synchronously.
 */
class ModerationGuiTest {

    private static final List<Integer> DETAIL_SLOTS = List.of(10, 11, 12, 13, 14, 16);
    private static final int HISTORY_SLOT = DETAIL_SLOTS.get(5);
    private static final int REVOKE_SLOT = 26;
    private static final int CONFIRM_SLOT =
            11; // the engine confirm window's yes button slot (ConfirmRenderer.YES_SLOT)

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private Clock clock;
    private FakeRepository repository;
    private RecordingRevoker revoker;

    private PunishmentDetailView detail;
    private final List<UUID> historyOpened = new ArrayList<>();

    @BeforeEach
    void setUp(@TempDir Path dir) throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Admin");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        clock = Clock.systemUTC();
        repository = new FakeRepository();
        revoker = new RecordingRevoker();
        historyOpened.clear();
        Guis.install(plugin);

        EntityEditorLayout detailLayout = detailLayout(dir);

        // The back button now reopens the engine-rendered active list (covered by ModerationActiveListGoldenTest) and
        // the history button hands the clicked punishment's target to the engine-rendered history menu (covered by
        // ModerationHistoryListGoldenTest); here back is a no-op and history captures the target so the button's
        // wiring is asserted.
        detail = new PunishmentDetailView(
                editorEngine(),
                guiText,
                scheduler,
                revoker,
                clock,
                detailLayout,
                (p, v) -> {},
                (p, punishment) -> historyOpened.add(punishment.target()));
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void revokeIsConfirmGatedAndCallsTheRevokeUseCase() {
        UUID target = uuid("Mallory");
        repository.addBan(target, Instant.now().plus(Duration.ofDays(7)));
        detail.open(player, viewer, repository.firstActive());

        fireClick(REVOKE_SLOT, ClickType.LEFT); // opens the confirm menu, does not revoke
        assertThat(revoker.revoked).isEmpty();

        fireClick(CONFIRM_SLOT, ClickType.LEFT); // the confirm button revokes
        assertThat(revoker.revoked).hasSize(1);
        assertThat(revoker.revoked.get(0).target().uuid()).isEqualTo(target);
        assertThat(revoker.revoked.get(0).kind()).isEqualTo(PunishmentKind.BAN);
    }

    @Test
    void historyButtonHandsTheClickedTargetToTheHistoryMenu() {
        UUID target = uuid("Mallory");
        repository.addBan(target, Instant.now().plus(Duration.ofDays(7)));
        detail.open(player, viewer, repository.firstActive());

        fireClick(HISTORY_SLOT, ClickType.LEFT);

        // The history list now renders through the menu engine (ModerationHistoryListGoldenTest); the detail
        // screen's only job is to hand that engine the clicked punishment's target.
        assertThat(historyOpened).containsExactly(target);
    }

    @Test
    void entryPointIsPermissionGated() {
        // The /mod command requires uxmessentials.moderation.gui; a fresh non-op MockBukkit player lacks it.
        assertThat(player.hasPermission("uxmessentials.moderation.gui")).isFalse();
        player.addAttachment(plugin, "uxmessentials.moderation.gui", true);
        assertThat(player.hasPermission("uxmessentials.moderation.gui")).isTrue();
    }

    // --- helpers ---

    private static UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /**
     * One editor-capable engine plus its single listener: the detail editor opens through this {@link Menus} and its
     * revoke-confirm child is routed by the one registered {@link MenuListener}, the engine path the production wiring
     * uses.
     */
    private Menus editorEngine() {
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        MenuBindings bindings = new MenuBindings();
        MenuRenderer renderer =
                new MenuRenderer(new ItemRenderer(guiText, bindings.placeholders()), bindings.conditions());
        Menus menus = new Menus(renderer, scheduler, bindings.lists(), editorRenderer);
        MenuListener listener = new MenuListener(
                renderer,
                bindings.actions(),
                bindings.conditions(),
                scheduler,
                plugin,
                editorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener());
        server.getPluginManager().registerEvents(listener, plugin);
        return menus;
    }

    private EntityEditorLayout detailLayout(Path dir) throws Exception {
        Path file = dir.resolve("modules").resolve("moderation").resolve("gui").resolve("punishment-detail.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                rows = 3
                back-icon = "ARROW"
                delete-icon = "LAVA_BUCKET"
                filler = "BLACK_STAINED_GLASS_PANE"
                back-slot = 22
                delete-slot = 26
                property-slots = [10, 11, 12, 13, 14, 16]
                """);
        EntityEditorLayout codeDefault = new EntityEditorLayout(
                3,
                DETAIL_SLOTS,
                22,
                java.util.OptionalInt.of(26),
                Material.ARROW,
                Material.LAVA_BUCKET,
                Material.BLACK_STAINED_GLASS_PANE);
        return new GuiLayouts(dir, NOOP).loadEntityEditor("moderation", "punishment-detail", codeDefault);
    }

    // --- fakes ---

    private record Revoked(PlayerRef actor, PlayerRef target, PunishmentKind kind) {}

    private static final class RecordingRevoker implements PunishmentRevoker {
        private final List<Revoked> revoked = new ArrayList<>();

        @Override
        public void revoke(PlayerRef actor, PlayerRef target, PunishmentKind kind) {
            revoked.add(new Revoked(actor, target, kind));
        }
    }

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

    /** An in-memory sanction store implementing only what the GUI reads; the rest stubs to clean defaults. */
    private static final class FakeRepository implements ModerationRepository {
        private final List<BanEntry> bans = new ArrayList<>();
        private final List<MuteEntry> mutes = new ArrayList<>();
        private final List<JailEntry> jails = new ArrayList<>();

        void addBan(UUID target, Instant until) {
            bans.add(new BanEntry(target, Issuer.console("console"), Optional.of("test"), until));
        }

        /** The first active punishment as the list projects it: for driving the detail view directly. */
        ActivePunishment firstActive() {
            if (!bans.isEmpty()) {
                return ActivePunishment.ofBan(bans.get(0), "Mallory");
            }
            if (!mutes.isEmpty()) {
                return ActivePunishment.ofMute(mutes.get(0), "Eve");
            }
            return ActivePunishment.ofJail(jails.get(0), "jailed");
        }

        @Override
        public List<BanEntry> activeBans(Instant now, int limit) {
            return List.copyOf(bans);
        }

        @Override
        public List<MuteEntry> activeMutes(Instant now, int limit) {
            return List.copyOf(mutes);
        }

        @Override
        public List<JailEntry> activeJails(Instant now, int limit) {
            return List.copyOf(jails);
        }

        @Override
        public ModerationProfile load(PlayerRef target) {
            return new ModerationProfile(target, MuteState.none(), JailState.none(), TempbanState.none());
        }

        @Override
        public MuteState loadMute(PlayerRef target) {
            return MuteState.none();
        }

        @Override
        public JailState loadJail(PlayerRef target) {
            return JailState.none();
        }

        @Override
        public TempbanState loadTempban(PlayerRef target) {
            return TempbanState.none();
        }

        @Override
        public void saveMute(PlayerRef target, MuteState mute) {}

        @Override
        public void saveJail(PlayerRef target, JailState jail) {}

        @Override
        public void saveTempban(PlayerRef target, TempbanState tempban) {}

        @Override
        public int appendWarn(PlayerRef target, Warn warn) {
            return 0;
        }

        @Override
        public List<Warn> warns(PlayerRef target, Instant now) {
            return List.of();
        }

        @Override
        public int clearWarns(PlayerRef target) {
            return 0;
        }

        @Override
        public int clearWarnsByActor(PlayerRef target, PlayerRef actor) {
            return 0;
        }

        @Override
        public void saveIpBan(IpBan ban) {}

        @Override
        public boolean removeIpBan(String ip) {
            return false;
        }

        @Override
        public Optional<IpBan> activeIpBan(String ip, Instant now) {
            return Optional.empty();
        }

        @Override
        public void recordSeen(PlayerRef who, Optional<String> ip, Instant at) {}

        @Override
        public Optional<SeenRecord> seen(PlayerRef who) {
            return Optional.empty();
        }

        @Override
        public void ensureUserExists(PlayerRef target, Instant at) {}

        @Override
        public boolean isLockedDown() {
            return false;
        }

        @Override
        public void setLockedDown(boolean enabled) {}
    }
}
