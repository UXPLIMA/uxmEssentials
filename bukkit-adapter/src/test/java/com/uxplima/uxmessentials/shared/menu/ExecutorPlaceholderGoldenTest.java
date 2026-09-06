package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * End-to-end golden of the {@code %player%} vs {@code %executor%} distinction through the real {@link Menus} open
 * path. The menu's one item names itself {@code %player%} (the viewer) and lores {@code %executor%} (the opener).
 *
 * <p>A self-open ({@code menus.open(steve, ...)}) leaves the executor defaulted to the viewer, so both read
 * "Steve". An open-for-another through the six-argument overload ({@code menus.open(bob, ..., steve)}) makes Bob
 * the viewer (whom {@code %player%} names, since the target is always the viewer on an open-for-another) and Steve
 * the executor (whom {@code %executor%} names), so the window Bob sees reads name "Bob" and lore "Steve". The
 * executor rides the holder's context, so a page flip, which the engine performs as {@code ctx.withPage(...)},
 * keeps {@code %executor%} pointed at the opener.
 */
class ExecutorPlaceholderGoldenTest {

    private static final String SPEC = """
            rows = 1
            items {
              panel {
                slots = ["0"]
                material = "PAPER"
                name = "%player%"
                lore = ["%executor%"]
              }
            }
            """;

    private ServerMock server;
    private PlayerMock steve;
    private PlayerMock bob;
    private Menus menus;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        steve = server.addPlayer("Steve");
        bob = server.addPlayer("Bob");

        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        menus = engine.menus();
        MenuVocabulary.registerPlaceholders(engine.bindings());

        menus.registerSpec("greet", new MenuSpecLoader().parse(SPEC));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aSelfOpenReadsTheExecutorAsTheViewer() {
        menus.open(new PlayerRef(steve.getUniqueId(), steve.getName()), "greet", null);
        ItemStack item = topItemFor(steve);

        assertThat(plainName(item)).as("%player% is the viewer").isEqualTo("Steve");
        assertThat(plainLore(item))
                .as("%executor% defaults to the viewer for a self-open, so it reads the same")
                .containsExactly("Steve");
    }

    @Test
    void anOpenForAnotherNamesTheOpenerAsExecutorAndTheTargetAsPlayer() {
        PlayerRef bobRef = new PlayerRef(bob.getUniqueId(), bob.getName());
        PlayerRef steveRef = new PlayerRef(steve.getUniqueId(), steve.getName());

        menus.open(bobRef, "greet", null, 0, Map.of(), steveRef);
        ItemStack item = topItemFor(bob);

        assertThat(plainName(item))
                .as("the viewer is the target, so %player% names Bob")
                .isEqualTo("Bob");
        assertThat(plainLore(item))
                .as("the executor is the opener, so %executor% names Steve")
                .containsExactly("Steve");
    }

    @Test
    void aPageFlipKeepsTheExecutorPointedAtTheOpener() {
        PlayerRef bobRef = new PlayerRef(bob.getUniqueId(), bob.getName());
        PlayerRef steveRef = new PlayerRef(steve.getUniqueId(), steve.getName());

        menus.open(bobRef, "greet", null, 0, Map.of(), steveRef);
        MenuHolder holder =
                (MenuHolder) bob.getOpenInventory().getTopInventory().getHolder();

        // A page flip re-renders from holder.ctx().withPage(...); the copy carries the executor, so %executor% stays
        // the opener across the redraw.
        assertThat(holder.ctx().withPage(1).executor().name()).isEqualTo("Steve");
    }

    private static ItemStack topItemFor(PlayerMock who) {
        Inventory top = who.getOpenInventory().getTopInventory();
        return Objects.requireNonNull(top.getItem(0), "item at slot 0");
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    private static List<String> plainLore(ItemStack item) {
        // The body only: the title line the canon puts above it is asserted where the title is asserted.
        return TileText.body(item).stream()
                .map(line -> PlainTextComponentSerializer.plainText().serialize(line))
                .toList();
    }

    /** A synchronous scheduler that runs every hop inline so the open path completes within the test call. */
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

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
