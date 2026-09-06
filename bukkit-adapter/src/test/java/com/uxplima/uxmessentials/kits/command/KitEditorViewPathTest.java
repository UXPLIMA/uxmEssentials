package com.uxplima.uxmessentials.kits.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorListener;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorView;
import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.application.KitEditor;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
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
 * MockBukkit coverage of the editable {@code /kit editor} window: the window is seeded with the kit's current
 * stacks at their definition-order slots; the editor's edit to the private window copy is encoded back into the
 * kit on close, and nothing is duplicated because the kit is overwritten wholesale from the window's final
 * contents (replace, never append): an item added in the window lands once and an item removed disappears.
 *
 * <p>The scheduler is a synchronous double so the entity-bound open and the async persist run inline, and the
 * close is dispatched as a real {@link InventoryCloseEvent} through the same {@link KitEditorListener} a live
 * close routes through. The recording repository captures the saved definition so the test asserts on the kit's
 * new item set directly.
 */
class KitEditorViewPathTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock editor;
    private KitEditorView view;
    private RecordingRepository repository;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        editor = server.addPlayer("Staff");
        repository = new RecordingRepository();
        Notifier notifier = new Notifier(new KeyMessages(), new NoSink());
        KitEditor kitEditor = new KitEditor(repository, notifier);
        view = new KitEditorView(new KeyMessages(), kitEditor, new SyncScheduler());
        server.getPluginManager().registerEvents(new KitEditorListener(view), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void seedsTheWindowWithTheKitItems() {
        view.open(editor, ref(editor), repository.starter());

        Inventory window = editor.getOpenInventory().getTopInventory();
        assertThat(window.getSize()).isEqualTo(54);
        assertThat(window.getItem(0)).isNotNull();
        assertThat(window.getItem(0).getType()).isEqualTo(Material.DIAMOND_SWORD);
        assertThat(window.getItem(5)).isNull(); // an unused slot is empty and editable
    }

    @Test
    void addingAnItemThenClosingSavesTheNewContentsWithoutDuplicating() {
        view.open(editor, ref(editor), repository.starter());
        Inventory window = editor.getOpenInventory().getTopInventory();

        // The editor drops a second stack into a free slot and closes.
        window.setItem(5, new ItemStack(Material.GOLDEN_APPLE, 3));
        server.getPluginManager().callEvent(new InventoryCloseEvent(editor.getOpenInventory()));

        KitDefinition saved = repository.lastSaved();
        assertThat(saved).isNotNull();
        assertThat(saved.id()).isEqualTo(KitId.of("starter"));
        // Two items in, two items out: the original sword plus the added apple, never the sword twice.
        assertThat(saved.items()).hasSize(2);
        assertThat(materialAt(saved, 0)).isEqualTo(Material.DIAMOND_SWORD);
        assertThat(materialAt(saved, 1)).isEqualTo(Material.GOLDEN_APPLE);
        // The non-item settings are carried untouched from the original definition.
        assertThat(saved.cooldown()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void removingTheOnlyItemSavesAnEmptyKit() {
        view.open(editor, ref(editor), repository.starter());
        Inventory window = editor.getOpenInventory().getTopInventory();

        window.setItem(0, null); // take the sword out and close on an empty window
        server.getPluginManager().callEvent(new InventoryCloseEvent(editor.getOpenInventory()));

        KitDefinition saved = repository.lastSaved();
        assertThat(saved).isNotNull();
        assertThat(saved.items()).isEmpty();
    }

    private static Material materialAt(KitDefinition kit, int index) {
        return KitItemCodec.decode(kit.items().get(index)).getType();
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    /** A repository holding one {@code starter} kit and recording the definition the editor saves back. */
    private static final class RecordingRepository implements KitRepository {
        private final KitDefinition starter = KitDefinition.repeatable(
                KitId.of("starter"),
                List.of(KitItemCodec.encode(new ItemStack(Material.DIAMOND_SWORD))),
                Duration.ofSeconds(60));
        private KitDefinition saved;

        KitDefinition starter() {
            return starter;
        }

        KitDefinition lastSaved() {
            return saved;
        }

        @Override
        public Optional<KitDefinition> find(KitId id) {
            return id.equals(starter.id()) ? Optional.of(starter) : Optional.empty();
        }

        @Override
        public List<KitDefinition> all() {
            return List.of(starter);
        }

        @Override
        public boolean exists(KitId id) {
            return id.equals(starter.id());
        }

        @Override
        public void save(KitDefinition definition) {
            this.saved = definition;
        }

        @Override
        public void delete(KitId id) {}
    }

    private static final class NoSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

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
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
