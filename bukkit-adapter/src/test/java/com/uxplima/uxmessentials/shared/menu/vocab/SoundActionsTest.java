package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.SoundActions;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
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
import org.mockbukkit.mockbukkit.sound.AudioExperience;

/**
 * MockBukkit coverage of the sound action pack: the enhanced {@code sound} (now with optional volume/pitch), plus
 * the new {@code broadcast-sound} (all online players hear it at their own location) and {@code rawsound} (a
 * verbatim namespaced key for resource-pack sounds through the Adventure sound API). Each registered handler is
 * fetched back through {@link MenuBindings#action(String)} and fired with the same {@link MenuActionContext} the
 * click listener builds, then the recorded {@link AudioExperience} on the live MockBukkit player is asserted for
 * key, volume and pitch. The pure {@link SoundActions.SoundArg} grammar is exercised separately with no server.
 */
class SoundActionsTest {

    private ServerMock server;
    private PlayerMock viewer;
    private MenuBindings bindings;
    private Menus menus;
    private RecordingLogger log;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = server.addPlayer("Viewer");
        GuiText guiText = new GuiText(new KeyMessages());
        bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        menus = new Menus(renderer, new SyncScheduler(), new ListSourceRegistry());
        log = new RecordingLogger();
        // Register the real generic actions (for the enhanced `sound`) and the real sound pack (broadcast/raw).
        MenuVocabulary.registerActions(bindings, menus, true, log);
        SoundActions.register(bindings, log);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- enhanced sound (volume/pitch) --------------------------------------------------------------------------

    @Test
    void soundWithVolumeAndPitchIsHeardWithThoseValues() {
        invoke("sound", "block.note_block.pling 0.5 2.0", viewer);

        AudioExperience heard = only(viewer);
        assertThat(heard.getVolume()).isEqualTo(0.5f);
        assertThat(heard.getPitch()).isEqualTo(2.0f);
    }

    @Test
    void soundWithoutArgsStaysAtVolumeAndPitchOne() {
        invoke("sound", "block.note_block.pling", viewer);

        AudioExperience heard = only(viewer);
        assertThat(heard.getVolume()).isEqualTo(1f);
        assertThat(heard.getPitch()).isEqualTo(1f);
    }

    @Test
    void soundAcceptsTheUpperSnakeConstantForm() {
        // The documented example on the menu engine page is written this way, and so is every sound in the UI style
        // canon. Handing the name straight to the client played block_note_block_pling, which names nothing.
        invoke("sound", "BLOCK_NOTE_BLOCK_PLING", viewer);

        assertThat(only(viewer).getSound()).isEqualTo("block.note_block.pling");
    }

    @Test
    void soundPlaysTheShippedPageTurnOfEveryMenu() {
        // The exact value 104 shipped menu files use for their page-turn button. Its key is item.book.page_turn,
        // so the underscore inside page_turn is the whole difficulty, and getting it wrong silences the one sound
        // the plugin plays most often in a way nobody reports: a menu that turns a page quietly still turns it.
        invoke("sound", "ITEM_BOOK_PAGE_TURN 0.7 1.2", viewer);

        AudioExperience heard = only(viewer);
        assertThat(heard.getSound()).isEqualTo("item.book.page_turn");
        assertThat(heard.getVolume()).isEqualTo(0.7f);
        assertThat(heard.getPitch()).isEqualTo(1.2f);
    }

    @Test
    void soundLeavesAResourcePackKeyAlone() {
        // A key the vanilla registry does not know belongs to the operator's resource pack. It reached the client
        // before this and has to keep reaching it, so the registry lookup is a translation and never a filter.
        invoke("sound", "myserver:custom.ding", viewer);

        assertThat(only(viewer).getSound()).isEqualTo("myserver:custom.ding");
    }

    @Test
    void blankSoundIsANoOp() {
        invoke("sound", "", viewer);

        assertThat(viewer.getHeardSounds()).isEmpty();
    }

    // --- broadcast-sound (all online players) -------------------------------------------------------------------

    @Test
    void broadcastSoundReachesEveryOnlinePlayerWithVolumeAndPitch() {
        PlayerMock second = server.addPlayer("Second");

        invoke("broadcast-sound", "block.note_block.pling 0.25 1.5", viewer);

        AudioExperience first = only(viewer);
        AudioExperience other = only(second);
        assertThat(first.getVolume()).isEqualTo(0.25f);
        assertThat(first.getPitch()).isEqualTo(1.5f);
        assertThat(other.getVolume()).isEqualTo(0.25f);
        assertThat(other.getPitch()).isEqualTo(1.5f);
    }

    @Test
    void soundallIsAnAliasOfBroadcastSound() {
        PlayerMock second = server.addPlayer("Second");

        invoke("soundall", "block.note_block.pling", viewer);

        assertThat(viewer.getHeardSounds()).isNotEmpty();
        assertThat(second.getHeardSounds()).isNotEmpty();
    }

    @Test
    void broadcastSoundAcceptsTheUpperSnakeConstantForm() {
        PlayerMock second = server.addPlayer("Second");

        invoke("broadcast-sound", "BLOCK_NOTE_BLOCK_PLING", viewer);

        assertThat(only(viewer).getSound()).isEqualTo("block.note_block.pling");
        assertThat(only(second).getSound()).isEqualTo("block.note_block.pling");
    }

    @Test
    void blankBroadcastSoundIsANoOp() {
        PlayerMock second = server.addPlayer("Second");

        invoke("broadcast-sound", "   ", viewer);

        assertThat(viewer.getHeardSounds()).isEmpty();
        assertThat(second.getHeardSounds()).isEmpty();
    }

    // --- rawsound (verbatim namespaced key via Adventure) -------------------------------------------------------

    @Test
    void rawSoundPlaysTheVerbatimNamespacedKey() {
        invoke("rawsound", "minecraft:entity.player.levelup 1 1", viewer);

        AudioExperience heard = only(viewer);
        assertThat(heard.getSound()).isEqualTo("minecraft:entity.player.levelup");
        assertThat(heard.getVolume()).isEqualTo(1f);
        assertThat(heard.getPitch()).isEqualTo(1f);
    }

    @Test
    void rawSoundIsAliasedAsRawDashSound() {
        assertThat(bindings.action("raw-sound")).isPresent();

        invoke("raw-sound", "myserver:custom.ding 0.5 2", viewer);

        AudioExperience heard = only(viewer);
        assertThat(heard.getSound()).isEqualTo("myserver:custom.ding");
        assertThat(heard.getVolume()).isEqualTo(0.5f);
        assertThat(heard.getPitch()).isEqualTo(2f);
    }

    @Test
    void rawSoundWithAnInvalidKeyIsAFailSoftNoOp() {
        // A key token the Adventure Key parser rejects (a bang is not a legal key character) throws
        // InvalidKeyException; the fail-soft wrapper turns that into a logged no-op, never a thrown exception.
        assertThatCode(() -> invoke("rawsound", "bad!key 1 1", viewer)).doesNotThrowAnyException();

        assertThat(viewer.getHeardSounds()).isEmpty();
    }

    @Test
    void rawSoundWithTheLiteralInvalidExampleDoesNotThrow() {
        // The prompt's example arg splits on whitespace, so its first token ("not") is a legal key, the point of
        // this case is that a malformed-looking arg never propagates an exception into the click dispatch.
        assertThatCode(() -> invoke("rawsound", "not a key!!", viewer)).doesNotThrowAnyException();
    }

    @Test
    void blankRawSoundIsANoOp() {
        invoke("rawsound", "", viewer);

        assertThat(viewer.getHeardSounds()).isEmpty();
    }

    // --- pure SoundArg grammar (no server needed) ---------------------------------------------------------------

    @Test
    void parsingAKeyOnlyDefaultsVolumeAndPitchToOne() {
        SoundActions.SoundArg arg = SoundActions.SoundArg.parse("block.note_block.pling");

        assertThat(arg.key()).isEqualTo("block.note_block.pling");
        assertThat(arg.volume()).isEqualTo(1f);
        assertThat(arg.pitch()).isEqualTo(1f);
    }

    @Test
    void parsingAKeyAndVolumeReadsVolumeAndDefaultsPitch() {
        SoundActions.SoundArg arg = SoundActions.SoundArg.parse("block.note_block.pling 0.5");

        assertThat(arg.volume()).isEqualTo(0.5f);
        assertThat(arg.pitch()).isEqualTo(1f);
    }

    @Test
    void parsingAKeyVolumeAndPitchReadsAllThree() {
        SoundActions.SoundArg arg = SoundActions.SoundArg.parse("block.note_block.pling 0.5 2.0");

        assertThat(arg.key()).isEqualTo("block.note_block.pling");
        assertThat(arg.volume()).isEqualTo(0.5f);
        assertThat(arg.pitch()).isEqualTo(2.0f);
    }

    @Test
    void parsingAMalformedNumberFallsBackToTheDefault() {
        SoundActions.SoundArg arg = SoundActions.SoundArg.parse("block.note_block.pling loud fast");

        assertThat(arg.volume()).isEqualTo(1f);
        assertThat(arg.pitch()).isEqualTo(1f);
    }

    @Test
    void parsingABlankValueYieldsABlankKey() {
        assertThat(SoundActions.SoundArg.parse("   ").key()).isBlank();
    }

    /** The single sound the player heard; fails the test if none or more than one was recorded. */
    private static AudioExperience only(PlayerMock player) {
        assertThat(player.getHeardSounds()).hasSize(1);
        return player.getHeardSounds().getFirst();
    }

    /** Builds the context the click listener would build, then fires the handler registered under {@code id}. */
    private void invoke(String id, String arg, PlayerMock as) {
        Consumer<MenuActionContext> handler =
                bindings.action(id).orElseThrow(() -> new AssertionError("action not registered: " + id));
        PlayerRef ref = new PlayerRef(as.getUniqueId(), as.getName());
        MenuActionContext ctx =
                new MenuActionContext(MenuContext.of(ref, null, 0), as, ClickKind.LEFT, Map.of("value", arg));
        handler.accept(ctx);
    }

    /** Captures expanded warn lines so a test can assert what the console operator logger recorded. */
    private static final class RecordingLogger implements Logger {
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            String expanded = message;
            for (Object arg : args) {
                expanded = expanded.replaceFirst("\\{}", String.valueOf(arg));
            }
            warnings.add(expanded);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
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

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}
