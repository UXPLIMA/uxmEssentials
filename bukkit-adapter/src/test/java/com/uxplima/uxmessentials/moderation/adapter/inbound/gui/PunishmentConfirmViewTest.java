package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmessentials.shared.menu.TileText;
import com.uxplima.uxmlib.gui.Guis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The punishment-confirm golden test: the engine-rendered screen must draw the exact window the original bespoke
 * {@code PunishmentConfirmView} drew, and its buttons must keep the behaviour. The screen draws the target's
 * PLAYER_HEAD@4, the apply REDSTONE_BLOCK@10, the silent BARRIER@12 (hidden for {@code /banip}), the reason
 * WRITABLE_BOOK@14 and the back ARROW@22 over a grey-glass backdrop, snapshotted as {@code (slot -> material,
 * plain name)} and asserted equal slot for slot to the baseline the old view produced (frozen here so the old
 * SimpleGui path could be deleted), for both the with-silent ({@code /ban}) and no-silent ({@code /banip}) layouts.
 *
 * <p>Clicking apply runs the subject's executor with {@code silent=false}; clicking silent runs it with
 * {@code silent=true}; the back button runs {@code onBack}. The set-reason button opens the shared anvil seam,
 * which MockBukkit leaves unimplemented (no {@code player.openAnvil}), so the reason round-trip is verified through
 * the package-private {@code applyReason} seam. Reopening carries the captured reason in the subject, which the
 * reason item's lore key then reflects.
 */
class PunishmentConfirmViewTest {

    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final int ROWS = 3;
    private static final int HEAD_SLOT = 4;
    private static final int APPLY_SLOT = 10;
    private static final int SILENT_SLOT = 12;
    private static final int REASON_SLOT = 14;
    private static final int BACK_SLOT = 22;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock actor;
    private PlayerRef actorRef;
    private PlayerRef target;
    private TextInput textInput;
    private TestMenuEngine engine;
    private PunishmentConfirmView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        actor = server.addPlayer("Staff");
        actorRef = new PlayerRef(actor.getUniqueId(), actor.getName());
        target = new PlayerRef(java.util.UUID.randomUUID(), "Target");
        textInput = TextInputTestKit.create(
                plugin,
                new GuiText(new KeyMessages()),
                new SyncScheduler(),
                java.nio.file.Path.of("nonexistent"),
                new NoopLogger());
        Guis.install(plugin);
        engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        engine.installListener(plugin);
        view = new PunishmentConfirmView(engine.menus(), new SyncScheduler(), textInput);
        view.register(engine.bindings(), specDir(), new NoopLogger());
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameConfirmScreenAsTheOldViewWithSilent() {
        view.open(actor, actorRef, target, PunishmentAction.BAN, recording(), () -> {});

        Inventory inv = actor.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(ROWS * 9);
        assertThat(snapshot(inv)).isEqualTo(baseline(true, PunishmentAction.BAN));
    }

    @Test
    void engineHidesTheSilentButtonForBanip() {
        view.open(actor, actorRef, target, PunishmentAction.BANIP, recording(), () -> {});

        Inventory inv = actor.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(ROWS * 9);
        // /banip has no silent form, so its slot carries only the backdrop, exactly as the old view omitted it.
        assertThat(Objects.requireNonNull(inv.getItem(SILENT_SLOT)).getType()).isEqualTo(FILLER);
        assertThat(snapshot(inv)).isEqualTo(baseline(false, PunishmentAction.BANIP));
    }

    @Test
    void theApplyButtonCallsTheExecutorNonSilent() {
        RecordingExecutor executor = recording();
        view.open(actor, actorRef, target, PunishmentAction.BAN, executor, () -> {});

        fireClick(APPLY_SLOT);

        assertThat(executor.calls).hasSize(1);
        assertThat(executor.calls.get(0).target()).isEqualTo(target);
        assertThat(executor.calls.get(0).silent()).isFalse();
    }

    @Test
    void theSilentButtonCallsTheExecutorSilent() {
        RecordingExecutor executor = recording();
        view.open(actor, actorRef, target, PunishmentAction.MUTE, executor, () -> {});

        fireClick(SILENT_SLOT);

        assertThat(executor.calls).hasSize(1);
        assertThat(executor.calls.get(0).target()).isEqualTo(target);
        assertThat(executor.calls.get(0).silent()).isTrue();
    }

    @Test
    void theBackButtonRunsOnBack() {
        List<String> back = new ArrayList<>();
        view.open(actor, actorRef, target, PunishmentAction.BAN, recording(), () -> back.add("back"));

        fireClick(BACK_SLOT);

        assertThat(back).containsExactly("back");
    }

    @Test
    void theReasonSeamReopensCarryingTheCapturedReason() {
        view.open(actor, actorRef, target, PunishmentAction.BAN, recording(), () -> {});
        // Before a reason is set, the reason item shows the "no reason" lore line.
        assertThat(loreKey(REASON_SLOT)).isEqualTo(ModerationMessageKey.MOD_GUI_CONFIRM_REASON_NONE_LORE.key());

        // Drive the reason-input submit branch directly (MockBukkit cannot open a live anvil): it reopens the screen
        // carrying the captured reason in the subject, which the reason item's lore key now reflects.
        view.applyReason(
                actorRef,
                new PunishmentConfirmView.Confirm(
                        PunishmentAction.BAN, target, recording(), Optional.empty(), () -> {}),
                Optional.of("griefing"));

        assertThat(loreKey(REASON_SLOT)).isEqualTo(ModerationMessageKey.MOD_GUI_CONFIRM_REASON_SET_LORE.key());
    }

    /**
     * The slot -> (material, plain name) map the deleted bespoke confirm view produced: the PLAYER_HEAD@4, the apply
     * REDSTONE_BLOCK@10, the silent BARRIER@12 (only when the verb supports it), the reason WRITABLE_BOOK@14 and the
     * back ARROW@22, each carrying its catalog key (the test's {@code KeyMessages} returns each key verbatim, so a
     * wrong key or material still mismatches). The grey-glass backdrop fills every other slot.
     */
    private static Map<Integer, Snapshot> baseline(boolean silent, PunishmentAction action) {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        for (int slot = 0; slot < ROWS * 9; slot++) {
            baseline.put(slot, new Snapshot(FILLER, ""));
        }
        baseline.put(
                HEAD_SLOT, new Snapshot(Material.PLAYER_HEAD, key(ModerationMessageKey.MOD_GUI_CONFIRM_TARGET_NAME)));
        baseline.put(
                APPLY_SLOT,
                new Snapshot(Material.REDSTONE_BLOCK, action.applyLabel().key()));
        if (silent) {
            baseline.put(
                    SILENT_SLOT,
                    new Snapshot(
                            Material.BARRIER, action.silentLabel().orElseThrow().key()));
        }
        baseline.put(
                REASON_SLOT, new Snapshot(Material.WRITABLE_BOOK, key(ModerationMessageKey.MOD_GUI_CONFIRM_REASON)));
        baseline.put(BACK_SLOT, new Snapshot(Material.ARROW, key(ModerationMessageKey.MOD_GUI_CONFIRM_BACK)));
        return baseline;
    }

    private static String key(ModerationMessageKey messageKey) {
        return messageKey.key();
    }

    private RecordingExecutor recording() {
        return new RecordingExecutor();
    }

    private String loreKey(int slot) {
        ItemStack item = Objects.requireNonNull(
                actor.getOpenInventory().getTopInventory().getItem(slot), "item");
        List<Component> lore = TileText.body(item);
        return PlainTextComponentSerializer.plainText().serialize(lore.get(0));
    }

    private void fireClick(int slot) {
        InventoryView inventoryView = actor.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                inventoryView, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The bundled spec directory under the source tree, so the view loads the shipped confirm spec. */
    private static java.nio.file.Path specDir() {
        java.nio.file.Path repoRoot = java.nio.file.Path.of("").toAbsolutePath();
        while (repoRoot != null && !java.nio.file.Files.exists(repoRoot.resolve("settings.gradle.kts"))) {
            repoRoot = repoRoot.getParent();
        }
        Objects.requireNonNull(repoRoot, "repo root");
        return repoRoot.resolve("bukkit-adapter/src/main/resources");
    }

    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null) {
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

    private record Snapshot(Material material, String name) {}

    private record Call(PlayerRef actor, PlayerRef target, Optional<String> reason, boolean silent) {}

    private static final class RecordingExecutor implements PunishmentAction.Executor {
        private final List<Call> calls = new ArrayList<>();

        @Override
        public void execute(PlayerRef actor, PlayerRef target, Optional<String> reason, boolean silent) {
            calls.add(new Call(actor, target, reason, silent));
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
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
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
