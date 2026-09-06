package com.uxplima.uxmessentials.custommenus.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuEditSession;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecWriter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import org.junit.jupiter.api.Test;

/**
 * Plain-JUnit coverage of the P4 click-action and view-requirement mutations {@link MenuEditSession} grew. Each proof
 * is a round-trip: clone a parsed spec, mutate a gesture's action list or the item's view requirement block, {@code
 * toSpec()} it, serialise with {@link MenuSpecWriter}, re-load through {@link MenuSpecLoader}, and assert the reloaded
 * click / view carries the change, so the edit model and the P0 writer compose without loss.
 */
class MenuClickRequirementEditTest {

    private final MenuSpecLoader loader = new MenuSpecLoader();
    private final MenuSpecWriter writer = new MenuSpecWriter();

    @Test
    void addingAnActionToAGestureRoundTrips() {
        MenuEditSession session = sessionOver("""
                rows = 1
                items { x { slot = 0, material = STONE, click { left = ["close"] } } }
                """);

        session.addAction("x", ClickKind.LEFT, Ref.parse("sound:BLOCK_NOTE_BLOCK_PLING"));

        MenuItemSpec item = reload(session).items().get("x");
        List<Ref> left = item.click().actions().get(ClickKind.LEFT);
        assertThat(left).hasSize(2);
        assertThat(left.get(0).id()).isEqualTo("close");
        assertThat(left.get(1).id()).isEqualTo("sound");
        assertThat(left.get(1).value()).isEqualTo("BLOCK_NOTE_BLOCK_PLING");
    }

    @Test
    void reorderingAndRemovingAGestureAction() {
        MenuEditSession session = sessionOver("""
                rows = 1
                items { x { slot = 0, material = STONE, click { left = ["close", "open:shop"] } } }
                """);

        session.moveAction("x", ClickKind.LEFT, 1, -1); // pull open:shop above close

        assertThat(gestureIds(session, "x")).containsExactly("open", "close");

        session.removeAction("x", ClickKind.LEFT, 0); // drop open

        List<Ref> left = reload(session).items().get("x").click().actions().get(ClickKind.LEFT);
        assertThat(left).hasSize(1);
        assertThat(left.get(0).id()).isEqualTo("close");
    }

    @Test
    void addingAViewRequirementAndSettingMinimumRoundTrips() {
        MenuEditSession session = sessionOver("""
                rows = 1
                items { x { slot = 0, material = STONE, click { left = ["close"] } } }
                """);

        session.addRequirement("x", Ref.parse("perm:shop.use"));
        session.addRequirement("x", Ref.parse("perm:shop.vip"));
        session.setViewMinimum("x", 1);

        MenuItemSpec item = reload(session).items().get("x");
        assertThat(item.view().requirements()).hasSize(2);
        assertThat(item.view().requirements().get(0).condition().id()).isEqualTo("perm");
        assertThat(item.view().minimum()).isEqualTo(1);
    }

    @Test
    void removingAViewRequirementDropsIt() {
        MenuEditSession session = sessionOver("""
                rows = 1
                items { x { slot = 0, material = STONE, view { requirements = ["perm:a", "perm:b"] }, click { left = ["close"] } } }
                """);

        session.removeRequirement("x", 0);

        assertThat(reload(session).items().get("x").view().requirements()).hasSize(1);
    }

    @Test
    void gestureMutationsAreNoOpsForAnUnknownItem() {
        MenuEditSession session = sessionOver("rows = 1\nitems { x { slot = 0, material = STONE } }");

        session.addAction("ghost", ClickKind.LEFT, Ref.parse("close"));

        assertThat(session.items()).containsOnlyKeys("x");
    }

    private MenuEditSession sessionOver(String hocon) {
        return MenuEditSession.from(loader.parse(hocon));
    }

    private MenuSpec reload(MenuEditSession session) {
        return loader.parse(writer.write(session.toSpec()));
    }

    private List<String> gestureIds(MenuEditSession session, String itemId) {
        return session.item(itemId).orElseThrow().click().actions().get(ClickKind.LEFT).stream()
                .map(Ref::id)
                .toList();
    }
}
