package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MessagingActions;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.advancement.Toasts;
import com.uxplima.uxmlib.scheduler.PaperScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the messaging action pack. Each registered handler is fetched back through
 * {@link MenuBindings#action(String)} and fired with a {@link MenuActionContext} built exactly as the click
 * listener builds it, then the effect on the live MockBukkit player(s) is asserted where the mock records it
 * a delivered chat message, a broadcast fan-out, an action bar, and asserted no-crash where MockBukkit cannot
 * model the effect (the Adventure title path and the synthetic-advancement toast). The pure grammar edge cases
 * live in {@link #MESSAGE_TO}/{@link #TITLE}/{@link #TOAST}-parsing assertions that need no server at all.
 */
class MessagingActionsTest {

    private ServerMock server;
    private PlayerMock viewer;
    private MenuBindings bindings;
    private RecordingLogger log;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = server.addPlayer("Viewer");
        bindings = new MenuBindings();
        log = new RecordingLogger();
        var plugin = MockBukkit.createMockPlugin();
        Toasts toasts = new Toasts(plugin, new PaperScheduler(plugin));
        MessagingActions.register(bindings, toasts, log);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- message-to / whisper -----------------------------------------------------------------------------------

    @Test
    void messageToDeliversTheRenderedMessageToTheNamedTarget() {
        PlayerMock target = server.addPlayer("Target");

        invoke("message-to", "Target <green>hello there");

        assertThat(target.nextMessage()).contains("hello there");
        assertThat(viewer.nextMessage()).as("the viewer is not the target").isNull();
    }

    @Test
    void whisperIsAnAliasOfMessageTo() {
        PlayerMock target = server.addPlayer("Target");

        invoke("whisper", "Target psst");

        assertThat(target.nextMessage()).contains("psst");
    }

    @Test
    void messageToAnOfflineTargetIsAFailSoftNoOp() {
        assertThatCode(() -> invoke("message-to", "Ghost you there?")).doesNotThrowAnyException();
        assertThat(viewer.nextMessage()).isNull();
    }

    @Test
    void messageToWithATargetButNoBodyDoesNotThrow() {
        PlayerMock target = server.addPlayer("Target");

        assertThatCode(() -> invoke("message-to", "Target")).doesNotThrowAnyException();
        // An empty body renders to an empty component; the point is only that it never throws into the click.
        assertThatCode(target::nextMessage).doesNotThrowAnyException();
    }

    // --- broadcast (minimessage / json / legacy) --------------------------------------------------------------

    @Test
    void broadcastFansOutMiniMessageToEveryPlayer() {
        PlayerMock other = server.addPlayer("Other");

        invoke("broadcast", "<yellow>server wide");

        assertThat(viewer.nextMessage()).contains("server wide");
        assertThat(other.nextMessage()).contains("server wide");
    }

    @Test
    void broadcastJsonParsesAComponentAndBroadcastsIt() {
        invoke("broadcast-json", "{\"text\":\"json line\",\"color\":\"red\"}");

        assertThat(plain(viewer.nextComponentMessage())).isEqualTo("json line");
    }

    @Test
    void broadcastJsonWithMalformedJsonIsAFailSoftNoOp() {
        assertThatCode(() -> invoke("broadcast-json", "not json at all")).doesNotThrowAnyException();
        assertThat(viewer.nextMessage()).isNull();
        assertThat(log.warnings).as("a malformed json broadcast warns once").hasSize(1);
    }

    @Test
    void broadcastLegacyParsesAmpersandColourCodes() {
        invoke("broadcast-legacy", "&clegacy line");

        assertThat(plain(viewer.nextComponentMessage())).isEqualTo("legacy line");
    }

    // --- action-bar ---------------------------------------------------------------------------------------------

    @Test
    void actionBarSendsToTheViewer() {
        invoke("action-bar", "<aqua>on the bar");

        assertThat(plain(viewer.nextActionBar())).isEqualTo("on the bar");
    }

    @Test
    void actionbarIsAnAlias() {
        invoke("actionbar", "aliased");

        assertThat(plain(viewer.nextActionBar())).isEqualTo("aliased");
    }

    // --- title --------------------------------------------------------------------------------------------------

    @Test
    void titleWithTimingsDispatchesWithoutThrowing() {
        // MockBukkit's PlayerMock does not record the Adventure showTitle(Title) path, so the parsed timings are
        // pinned by the pure parse tests below; here we prove the apply path is well-formed and never throws.
        assertThatCode(() -> invoke("title", "<gold>Welcome|<gray>to the server|5|40|15"))
                .doesNotThrowAnyException();
    }

    @Test
    void bareTitleWithNoTimingsDispatchesWithoutThrowing() {
        assertThatCode(() -> invoke("title", "Just a title")).doesNotThrowAnyException();
    }

    @Test
    void titleWithMalformedTimingsFallsBackWithoutThrowing() {
        assertThatCode(() -> invoke("title", "Title|Sub|fast|slow|nope")).doesNotThrowAnyException();
    }

    // --- toast --------------------------------------------------------------------------------------------------

    @Test
    void toastWithAValidSpecDoesNotCrash() {
        assertThatCode(() -> invoke("toast", "stone|Hello|World|GOAL")).doesNotThrowAnyException();
    }

    @Test
    void toastWithAnUnknownMaterialIsANoOp() {
        assertThatCode(() -> invoke("toast", "not_a_material|Hello")).doesNotThrowAnyException();
    }

    @Test
    void toastWithAnUnknownFrameDoesNotCrash() {
        assertThatCode(() -> invoke("toast", "stone|Hello|World|WOBBLE")).doesNotThrowAnyException();
    }

    // --- log ----------------------------------------------------------------------------------------------------

    @Test
    void logWritesTheOperatorLineToTheConsoleLogger() {
        invoke("log", "a diagnostic line");

        assertThat(log.infos).containsExactly("a diagnostic line");
    }

    // --- pure parsing edge cases (no server needed) -------------------------------------------------------------

    @Test
    void whisperParsingSplitsOffTheFirstTokenAsTheTarget() {
        MessagingActions.ParsedWhisper parsed = MessagingActions.ParsedWhisper.parse("  Steve hey how are you  ");

        assertThat(parsed.target()).isEqualTo("Steve");
        assertThat(parsed.message()).isEqualTo("hey how are you");
    }

    @Test
    void whisperParsingWithATargetOnlyLeavesAnEmptyMessage() {
        MessagingActions.ParsedWhisper parsed = MessagingActions.ParsedWhisper.parse("Steve");

        assertThat(parsed.target()).isEqualTo("Steve");
        assertThat(parsed.message()).isEmpty();
    }

    @Test
    void titleParsingWithNoSubtitleOrTimesUsesTheVanillaDefaults() {
        MessagingActions.ParsedTitle parsed = MessagingActions.ParsedTitle.parse("Welcome");

        assertThat(parsed.title()).isEqualTo("Welcome");
        assertThat(parsed.subtitle()).isEmpty();
        assertThat(parsed.fadeInTicks()).isEqualTo(10);
        assertThat(parsed.stayTicks()).isEqualTo(70);
        assertThat(parsed.fadeOutTicks()).isEqualTo(20);
    }

    @Test
    void titleParsingWithFullTimesReadsAllThree() {
        MessagingActions.ParsedTitle parsed = MessagingActions.ParsedTitle.parse("Title|Sub|5|40|15");

        assertThat(parsed.title()).isEqualTo("Title");
        assertThat(parsed.subtitle()).isEqualTo("Sub");
        assertThat(parsed.fadeInTicks()).isEqualTo(5);
        assertThat(parsed.stayTicks()).isEqualTo(40);
        assertThat(parsed.fadeOutTicks()).isEqualTo(15);
    }

    @Test
    void titleParsingWithAMalformedTailKeepsTheWholeTailAsSubtitle() {
        MessagingActions.ParsedTitle parsed = MessagingActions.ParsedTitle.parse("Title|Sub|fast|slow|nope");

        assertThat(parsed.title()).isEqualTo("Title");
        assertThat(parsed.subtitle()).isEqualTo("Sub|fast|slow|nope");
        assertThat(parsed.fadeInTicks()).isEqualTo(10);
    }

    @Test
    void toastParsingReadsIconTitleDescriptionAndFrame() {
        MessagingActions.ParsedToast parsed = MessagingActions.ParsedToast.parse("stone|Hello|World|GOAL");

        assertThat(parsed.icon()).isEqualTo("stone");
        assertThat(parsed.title()).isEqualTo("Hello");
        assertThat(parsed.description()).isEqualTo("World");
        assertThat(parsed.frame()).isEqualTo("GOAL");
    }

    @Test
    void toastParsingWithOnlyIconAndTitleLeavesDescriptionAndFrameEmpty() {
        MessagingActions.ParsedToast parsed = MessagingActions.ParsedToast.parse("diamond|Nice");

        assertThat(parsed.icon()).isEqualTo("diamond");
        assertThat(parsed.title()).isEqualTo("Nice");
        assertThat(parsed.description()).isEmpty();
        assertThat(parsed.frame()).isEmpty();
    }

    /** Builds the context the click listener would build, then fires the handler registered under {@code id}. */
    private void invoke(String id, String arg) {
        Consumer<MenuActionContext> handler =
                bindings.action(id).orElseThrow(() -> new AssertionError("action not registered: " + id));
        PlayerRef ref = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        MenuActionContext ctx =
                new MenuActionContext(MenuContext.of(ref, null, 0), viewer, ClickKind.LEFT, Map.of("value", arg));
        handler.accept(ctx);
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Captures expanded info/warn lines so a test can assert what the console operator logger recorded. */
    private static final class RecordingLogger implements Logger {
        private final List<String> infos = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            infos.add(expand(message, args));
        }

        @Override
        public void warn(String message, Object... args) {
            warnings.add(expand(message, args));
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}

        private static String expand(String message, Object... args) {
            String expanded = message;
            for (Object arg : args) {
                expanded = expanded.replaceFirst("\\{}", String.valueOf(arg));
            }
            return expanded;
        }
    }
}
