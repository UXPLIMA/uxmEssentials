package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
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
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Unit coverage of the {@link ListProperty} apply seam, the package-private {@code applyAdd}/{@code applyEdit} the
 * anvil's submit branch calls: independent of which runtime opened the sub-menu. A non-blank add appends and saves
 * through the setter; a non-blank edit rewrites one entry and saves; a blank line writes nothing. The reopen after a
 * save rides a no-op selector opener so the seam runs on the engine path without painting a real window, exactly the
 * way {@code ColourPropertyTest} drives {@code applyCustom}. The scheduler runs every hop inline so the off-thread
 * write lands synchronously.
 */
class ListPropertyApplyTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private TextInput textInput;
    private List<String> lines;
    private List<List<String>> writes;
    private ListProperty property;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        Guis.install(plugin);
        textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP_LOG);
        lines = new ArrayList<>(List.of("alpha", "beta", "gamma"));
        writes = new ArrayList<>();
        property = new ListProperty(
                "demo-line",
                Key.LABEL,
                Material.BOOK,
                guiText,
                () -> lines,
                next -> {
                    lines = new ArrayList<>(next);
                    writes.add(new ArrayList<>(next));
                },
                listText(),
                listLayout(),
                textInput,
                scheduler);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void applyAddAppendsANonBlankLineAndSaves() {
        property.applyAdd(engineContext(), "delta");

        assertThat(lines).containsExactly("alpha", "beta", "gamma", "delta");
        assertThat(writes).hasSize(1);
    }

    @Test
    void applyAddIgnoresABlankLine() {
        property.applyAdd(engineContext(), "   ");

        assertThat(lines).containsExactly("alpha", "beta", "gamma");
        assertThat(writes).isEmpty();
    }

    @Test
    void applyEditRewritesTheEntryAndSaves() {
        property.applyEdit(engineContext(), 1, "BETA-2");

        assertThat(lines).containsExactly("alpha", "BETA-2", "gamma");
        assertThat(writes).hasSize(1);
    }

    @Test
    void applyEditIgnoresABlankLine() {
        property.applyEdit(engineContext(), 0, "");

        assertThat(lines).containsExactly("alpha", "beta", "gamma");
        assertThat(writes).isEmpty();
    }

    /**
     * A context on the engine path: it carries a no-op selector opener so the post-save reopen takes the engine branch
     * without opening a real window. The confirm opener is unused by add/edit, so it is a harmless no-op.
     */
    private ClickContext engineContext() {
        SelectorOpener noOpSelector = (v, title, rows, filler, buttons) -> {};
        ConfirmOpener noOpConfirm = (v, title, onYes, onNo) -> {};
        return new ClickContext(player, viewer, false, false, () -> {}, noOpSelector, noOpConfirm);
    }

    private static ListPropertyText listText() {
        return new ListPropertyText(
                Key.TITLE,
                Key.ENTRY_NAME,
                Key.ENTRY_HINTS,
                Key.ADD_NAME,
                Key.ADD_PROMPT,
                Key.EDIT_PROMPT,
                Key.REMOVE_CONFIRM,
                Key.BACK);
    }

    private static ListPropertyLayout listLayout() {
        return new ListPropertyLayout(
                2,
                List.of(0, 1, 2, 3),
                16,
                17,
                Material.PAPER,
                Material.EMERALD,
                Material.ARROW,
                Material.BLACK_STAINED_GLASS_PANE);
    }

    private enum Key implements MessageKey {
        LABEL("demo.prop.label"),
        TITLE("demo.list.title"),
        ENTRY_NAME("demo.list.entry-name"),
        ENTRY_HINTS("demo.list.entry-hints"),
        ADD_NAME("demo.list.add-name"),
        ADD_PROMPT("demo.list.add-prompt"),
        EDIT_PROMPT("demo.list.edit-prompt"),
        REMOVE_CONFIRM("demo.list.remove-confirm"),
        BACK("demo.list.back");

        private final String key;

        Key(String key) {
            this.key = key;
        }

        @Override
        public String key() {
            return key;
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final Logger NOOP_LOG = new Logger() {
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
