package com.uxplima.uxmessentials.custommenus.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.OpenCommandSpec;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuEditSession;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecWriter;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.LoreMode;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RefreshSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import org.junit.jupiter.api.Test;

/**
 * Plain-JUnit coverage of {@link MenuEditSession}, the mutable edit model the menu editor mutates before it writes.
 * The linchpin proof is a round-trip: clone a parsed spec, mutate it through the model's operations, {@code toSpec()}
 * it, serialize with {@link MenuSpecWriter}, re-load through {@link MenuSpecLoader}, and assert it equals the spec an
 * operator would have written by hand, so the edit model and the writer compose without loss.
 */
class MenuEditSessionTest {

    private final MenuSpecLoader loader = new MenuSpecLoader();
    private final MenuSpecWriter writer = new MenuSpecWriter();

    @Test
    void cloneMutateAndWriteRoundTripsToTheExpectedSpec() {
        MenuSpec initial = loader.parse("""
                title = "<gold>Old"
                rows = 1
                items { a { slot = 0, material = STONE, name = "", click { left = ["close"] } } }
                """);
        MenuSpec expected = loader.parse("""
                title = "<red>New"
                rows = 3
                inventory-type = "hopper"
                items {
                  a { slots = [2], material = STONE, name = "", click { left = ["close"] } }
                  b { slots = [4], material = DIRT, name = "", click { left = ["close"] } }
                }
                """);

        MenuEditSession session = MenuEditSession.from(initial);
        session.setTitle("<red>New");
        session.setRows(3);
        session.setInventoryType("hopper");
        session.moveItem("a", new SlotSet(List.of(2)));
        session.addItem("b", expected.items().get("b"));

        MenuSpec reloaded = loader.parse(writer.write(session.toSpec()));
        assertThat(reloaded).isEqualTo(expected);
    }

    @Test
    void addItemAndRemoveItemChangeTheItemMap() {
        MenuSpec initial = loader.parse("""
                rows = 1
                items {
                  a { slot = 0, material = STONE, click { left = ["close"] } }
                  b { slot = 1, material = DIRT, click { left = ["close"] } }
                }
                """);
        MenuEditSession session = MenuEditSession.from(initial);

        session.removeItem("a");

        assertThat(session.toSpec().items()).containsOnlyKeys("b");
        assertThat(session.item("a")).isEmpty();
        assertThat(session.item("b")).isPresent();
    }

    @Test
    void moveItemPreservesEveryOtherFieldOfTheItem() {
        MenuSpec initial = loader.parse("""
                rows = 2
                items { a { slot = 0, priority = 3, material = STONE, name = "<green>Buy", click { left = ["close"] } } }
                """);
        MenuEditSession session = MenuEditSession.from(initial);

        session.moveItem("a", new SlotSet(List.of(9)));

        var moved = session.item("a").orElseThrow();
        assertThat(moved.slots().slots()).containsExactly(9);
        assertThat(moved.priority()).isEqualTo(3);
        assertThat(moved.material()).isEqualTo("STONE");
        assertThat(moved.name()).isEqualTo("<green>Buy");
    }

    @Test
    void menuLevelSettersReadBackThroughToSpec() {
        MenuEditSession session = MenuEditSession.from(loader.parse("rows = 1"));

        session.setTitle("<aqua>Panel");
        session.setRows(6);
        session.setClickCooldownMs(250L);
        session.setChestOnly(true);
        session.setBottomInventory(false);

        MenuSpec spec = session.toSpec();
        assertThat(spec.title()).isEqualTo("<aqua>Panel");
        assertThat(spec.rows()).isEqualTo(6);
        assertThat(spec.clickCooldownMs()).isEqualTo(250L);
        assertThat(spec.chestOnly()).isTrue();
    }

    @Test
    void clearingTheInventoryTypeReturnsToTheChestDefault() {
        MenuEditSession session = MenuEditSession.from(loader.parse("rows = 3\ninventory-type = \"hopper\"\n"));
        assertThat(session.toSpec().inventoryType()).contains("hopper");

        session.setInventoryType(null);

        assertThat(session.toSpec().inventoryType()).isEmpty();
    }

    @Test
    void itemFieldSettersRoundTripThroughTheWriter() {
        MenuSpec initial = loader.parse("""
                rows = 3
                items { a { slot = 0, material = STONE, name = "", click { left = ["close"] } } }
                """);
        MenuEditSession session = MenuEditSession.from(initial);

        session.setMaterial("a", "DIAMOND");
        session.setName("a", "<gold>Shiny");
        session.setAmount("a", 16);
        session.setPriority("a", 5);
        session.setGlow("a", true);
        session.setModelData("a", Optional.of(7));
        session.setFlags("a", List.of("HIDE_ATTRIBUTES"));
        session.setLore("a", List.of("<gray>one", "<gray>two"));
        session.setLoreMode("a", LoreMode.APPEND);
        session.setType("a", ItemType.NEXT);
        session.setSlots("a", new SlotSet(List.of(4)));

        MenuSpec reloaded = loader.parse(writer.write(session.toSpec()));
        MenuItemSpec item = reloaded.items().get("a");
        assertThat(item.material()).isEqualTo("DIAMOND");
        assertThat(item.name()).isEqualTo("<gold>Shiny");
        assertThat(item.decor().amount()).isEqualTo(16);
        assertThat(item.priority()).isEqualTo(5);
        assertThat(item.decor().glow()).isTrue();
        assertThat(item.decor().modelData()).contains(7);
        assertThat(item.decor().flagTokens()).containsExactly("HIDE_ATTRIBUTES");
        assertThat(item.lore()).containsExactly("<gray>one", "<gray>two");
        assertThat(item.loreMode()).isEqualTo(LoreMode.APPEND);
        assertThat(item.type()).isEqualTo(ItemType.NEXT);
        assertThat(item.slots().slots()).containsExactly(4);
    }

    @Test
    void loreLinesAddReorderRemoveRoundTrip() {
        MenuSpec initial = loader.parse("""
                rows = 1
                items { a { slot = 0, material = STONE, lore = ["one", "two"], click { left = ["close"] } } }
                """);
        MenuEditSession session = MenuEditSession.from(initial);

        // The ListProperty rewrites the whole list on each add / reorder / remove, so the session-level setter takes
        // the fully rebuilt list: add "three", reorder the first two, then drop "one".
        List<String> lore = new ArrayList<>(session.item("a").orElseThrow().lore());
        lore.add("three");
        Collections.swap(lore, 0, 1);
        lore.remove("one");
        session.setLore("a", lore);

        MenuSpec reloaded = loader.parse(writer.write(session.toSpec()));
        assertThat(reloaded.items().get("a").lore()).containsExactly("two", "three");
    }

    @Test
    void updateItemIsANoOpForAnUnknownId() {
        MenuEditSession session =
                MenuEditSession.from(loader.parse("rows = 1\nitems { a { slot = 0, material = STONE } }"));

        session.setMaterial("ghost", "DIRT");

        assertThat(session.items()).containsOnlyKeys("a");
        assertThat(session.item("a").orElseThrow().material()).isEqualTo("STONE");
    }

    @Test
    void everyMenuLevelSetterRoundTripsThroughTheWriter() {
        MenuEditSession session = MenuEditSession.from(loader.parse("rows = 1"));

        session.setTitle("<red>Shop");
        session.setRows(3);
        session.setInventoryType("hopper");
        session.setClickCooldownMs(200L);
        session.setChestOnly(true);
        session.setBottomInventory(false);
        session.setOpenRequirement(List.of(Ref.parse("perm:shop.use")));
        session.setOpenActions(List.of(Ref.parse("sound:PLING")));
        session.setCloseActions(List.of(Ref.parse("message:bye")));
        session.setRefresh(true, 40);

        MenuSpec reloaded = loader.parse(writer.write(session.toSpec()));
        assertThat(reloaded.title()).isEqualTo("<red>Shop");
        assertThat(reloaded.rows()).isEqualTo(3);
        assertThat(reloaded.inventoryType()).contains("hopper");
        assertThat(reloaded.clickCooldownMs()).isEqualTo(200L);
        assertThat(reloaded.chestOnly()).isTrue();
        assertThat(reloaded.openRequirement()).extracting(Ref::id).containsExactly("perm");
        assertThat(reloaded.openActions()).extracting(Ref::id).containsExactly("sound");
        assertThat(reloaded.closeActions()).extracting(Ref::id).containsExactly("message");
        assertThat(reloaded.refresh()).isEqualTo(new RefreshSpec(true, 40));
    }

    @Test
    void decreasingRowsDropsItemsThatFallOutOfRangeWithoutThrowing() {
        MenuEditSession session = MenuEditSession.from(loader.parse("""
                rows = 6
                items {
                  keep { slot = 0, material = STONE, click { left = ["close"] } }
                  drop { slot = 45, material = DIRT, click { left = ["close"] } }
                }
                """));

        session.setRows(1); // capacity shrinks to 9, so slot 45 no longer fits

        MenuSpec spec = session.toSpec(); // must not throw
        assertThat(spec.rows()).isEqualTo(1);
        assertThat(spec.items()).containsOnlyKeys("keep");
    }

    @Test
    void theRefreshTogglePreservesAValidIntervalWhenEnabled() {
        MenuEditSession session = MenuEditSession.from(loader.parse("rows = 1"));

        session.setRefresh(true, 0); // enabling with no interval must not produce an invalid RefreshSpec

        assertThat(session.refresh().enabled()).isTrue();
        assertThat(session.refresh().intervalTicks()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void theCommandBlockRidesTheSessionAndWritesBackWithTheSpec() {
        MenuSpec spec =
                loader.parse("rows = 1\nitems { x { slot = 0, material = STONE, click { left = [\"close\"] } } }");
        OpenCommandSpec command = new OpenCommandSpec(
                "shop",
                List.of("store"),
                Optional.of("menu.shop"),
                Optional.empty(),
                false,
                List.of(new ArgumentSpec("target", ArgumentSpec.ArgType.ONLINE_PLAYER)),
                Optional.of("/shop"));

        MenuEditSession session = MenuEditSession.from(spec, command);

        assertThat(session.command()).contains(command);
        // The written file carries the command {} block, so re-reading it reproduces the same open command.
        String hocon = writer.write(session.toSpec(), session.command().orElse(null));
        assertThat(hocon).contains("command");
        assertThat(hocon).contains("store");
    }
}
