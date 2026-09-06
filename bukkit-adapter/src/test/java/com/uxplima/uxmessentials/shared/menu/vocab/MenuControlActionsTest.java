package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuControl;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuControlActions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Unit coverage of the menu-control action pack away from a live window: the {@code refresh-slot} slot grammar and
 * the {@code open:<menu> [page]} target grammar are pinned with plain assertions, the {@code refresh-slot} ref is
 * proved to split its slot argument, and, the regression that keeps every other action pack safe, a context built
 * the way a unit test or a feature binding builds it (the four-argument constructor) is proved to carry a no-op
 * control, so firing {@code refresh}/{@code reset-pagination}/{@code refresh-slot} through it does nothing rather than
 * dereferencing null. The through-the-listener behaviour (a real repaint, a real page reset) is covered by
 * {@code MenuControlGoldenTest}, where the engine supplies a control bound to a real holder.
 */
class MenuControlActionsTest {

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
        MenuControlActions.register(bindings, log);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- registration + no-op control safety ---------------------------------------------------------------------

    @Test
    void registersRefreshRefreshSlotAndBothResetAliases() {
        assertThat(bindings.action("refresh")).isPresent();
        assertThat(bindings.action("refresh-slot")).isPresent();
        assertThat(bindings.action("reset-pagination")).isPresent();
        assertThat(bindings.action("reset-page")).isPresent();
    }

    @Test
    void fourArgumentContextCarriesANoOpControl() {
        MenuActionContext ctx = context("");

        assertThat(ctx.control()).isSameAs(MenuControl.NOOP);
    }

    @Test
    void controlActionsAreSafeNoOpsWhenNoWindowSuppliedAControl() {
        assertThatCode(() -> {
                    invoke("refresh", "");
                    invoke("refresh-slot", "4");
                    invoke("reset-pagination", "");
                    invoke("reset-page", "");
                })
                .doesNotThrowAnyException();
        assertThat(log.warnings).as("a no-op control action should not warn").isEmpty();
    }

    @Test
    void malformedRefreshSlotIsANoOp() {
        assertThatCode(() -> invoke("refresh-slot", "notaslot")).doesNotThrowAnyException();
        assertThat(log.warnings).isEmpty();
    }

    // --- pure grammar --------------------------------------------------------------------------------------------

    @Test
    void parseSlotReadsAWholeNumber() {
        assertThat(MenuControlActions.parseSlot("4")).hasValue(4);
        assertThat(MenuControlActions.parseSlot("  7 ")).hasValue(7);
    }

    @Test
    void parseSlotIsEmptyForBlankOrNonNumeric() {
        assertThat(MenuControlActions.parseSlot("")).isEmpty();
        assertThat(MenuControlActions.parseSlot("   ")).isEmpty();
        assertThat(MenuControlActions.parseSlot("notaslot")).isEmpty();
    }

    @Test
    void parseSlotKeepsANegativeSlotForTheControlToReject() {
        // Parsing is grammar-only: a negative slot is a well-formed number, and the control rejects it as
        // out-of-range at repaint time rather than the parser dropping it here.
        assertThat(MenuControlActions.parseSlot("-1")).isEqualTo(OptionalInt.of(-1));
    }

    @Test
    void openTargetParsesMenuAndPage() {
        MenuVocabulary.OpenTarget target = MenuVocabulary.OpenTarget.parse("menu2 1");

        assertThat(target.menu()).isEqualTo("menu2");
        assertThat(target.page()).isEqualTo(1);
    }

    @Test
    void openTargetDefaultsPageToZeroWhenAbsent() {
        MenuVocabulary.OpenTarget target = MenuVocabulary.OpenTarget.parse("menu2");

        assertThat(target.menu()).isEqualTo("menu2");
        assertThat(target.page()).isZero();
    }

    @Test
    void openTargetTreatsANonNumericOrNegativePageAsZero() {
        assertThat(MenuVocabulary.OpenTarget.parse("menu2 later").page()).isZero();
        assertThat(MenuVocabulary.OpenTarget.parse("menu2 -3").page()).isZero();
    }

    @Test
    void openTargetTrimsSurroundingWhitespace() {
        MenuVocabulary.OpenTarget target = MenuVocabulary.OpenTarget.parse("  menu2   2  ");

        assertThat(target.menu()).isEqualTo("menu2");
        assertThat(target.page()).isEqualTo(2);
    }

    @Test
    void refreshSlotRefSplitsItsSlotArgument() {
        // refresh-slot is a generic arg-bearing prefix, so its slot arrives in the ref's value the same way sound:
        // and open: do; without that the whole "refresh-slot:4" token would be an unresolvable action id.
        Ref ref = Ref.parse("refresh-slot:4");

        assertThat(ref.id()).isEqualTo("refresh-slot");
        assertThat(ref.value()).isEqualTo("4");
    }

    // --- harness -------------------------------------------------------------------------------------------------

    /** Builds the four-argument context a unit test or feature binding builds, no window, so a no-op control. */
    private MenuActionContext context(String arg) {
        PlayerRef ref = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        return new MenuActionContext(MenuContext.of(ref, null, 0), viewer, ClickKind.LEFT, Map.of("value", arg));
    }

    private void invoke(String id, String arg) {
        Consumer<MenuActionContext> handler =
                bindings.action(id).orElseThrow(() -> new AssertionError("action not registered: " + id));
        handler.accept(context(arg));
    }

    /** Captures expanded warning lines so a test can assert a fail-soft action logged (or, here, stayed silent). */
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
}
