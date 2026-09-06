package com.uxplima.uxmessentials.custommenus.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuEditSession;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecWriter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import org.junit.jupiter.api.Test;

/**
 * The golden round-trip proof for {@link MenuSpecWriter}: a spec that has been through the loader, then the writer,
 * then the loader again must equal the spec it started as. It is the load-time contract of the whole menu editor
 * an in-game edit is only safe to write back if writing then re-reading is lossless, so the coverage is deliberately
 * exhaustive: the shipped {@code menus/example.conf} plus synthesized specs that exercise every key the loader reads
 * (multiple items, slot ranges, the full decor surface, every click gesture with a requirement block and an
 * else-chain, view requirements and the {@code permission}/{@code pages} shorthands, a list item, item-drag, refresh,
 * a non-chest inventory type, a bottom-inventory canvas, the chest-only routing hint, a fill item, and a Bedrock
 * form). Pure JUnit and Configurate only, no MockBukkit, because the writer and the model it walks are Bukkit-free.
 *
 * <p>Equality is the records' own deep value equality: {@link MenuSpec} and every type it holds are records, so a
 * single {@code isEqualTo} compares title, rows, refresh, actions, items, decor, clicks, requirements, and the rest
 * field by field.
 */
class MenuSpecRoundTripTest {

    private final MenuSpecLoader loader = new MenuSpecLoader();
    private final MenuSpecWriter writer = new MenuSpecWriter();

    @Test
    void shippedExampleMenuRoundTrips() {
        assertRoundTrips(readResource("/menus/example.conf"));
    }

    @Test
    void kitchenSinkMenuRoundTripsEveryKey() {
        assertRoundTrips(KITCHEN_SINK);
    }

    @Test
    void nonChestInventoryTypeRoundTrips() {
        assertRoundTrips("""
                inventory-type = hopper
                rows = 1
                items { a { slot = 0, material = STONE, click { left = ["close"] } } }
                """);
    }

    @Test
    void bottomInventoryCanvasRoundTrips() {
        assertRoundTrips("""
                bottom-inventory = true
                items {
                  top { slot = 0, material = STONE, click { left = ["close"] } }
                  below { slots = ["60-62"], material = DIRT }
                }
                """);
    }

    @Test
    void chestOnlyRoutingHintRoundTrips() {
        assertRoundTrips("""
                chest-only = true
                rows = 1
                items { a { slot = 0, material = BARRIER } }
                """);
    }

    @Test
    void bedrockFormRoundTrips() {
        assertRoundTrips(BEDROCK_FORM);
    }

    /**
     * The editor's write path, end to end: a spec built up through {@link MenuEditSession} mutations, the very model
     * the in-game editor drives: must survive write → re-load unchanged. This locks the edit-model → HOCON path against
     * regression the way the golden cases above lock the loader → writer path, covering a captured {@code b64:} material
     * token, a renamed item with lore and glow, an appended right-click action, and a resize.
     */
    @Test
    void anEditorProducedSpecRoundTrips() {
        MenuSpec base = loader.parse("""
                rows = 3
                items { x { slot = 0, material = STONE, click { left = ["close"] } } }
                """);
        MenuEditSession session = MenuEditSession.from(base);
        session.setTitle("<gold>Edited menu");
        session.setName("x", "<aqua>Renamed");
        session.setLore("x", List.of("<gray>Line one", "<gray>Line two"));
        session.setGlow("x", true);
        // A capture stores the held item as a b64 token: the material the /menu captureitem and drag paths write.
        session.setMaterial("x", "b64:cmVhbGx5LWFuLW9wYXF1ZS1zdGFjaw==");
        session.addAction("x", ClickKind.RIGHT, Ref.parse("sound:UI_BUTTON_CLICK_1"));
        session.addItem("y", base.items().get("x").withSlots(new SlotSet(List.of(5))));
        session.setRows(4);

        MenuSpec edited = session.toSpec();
        MenuSpec reloaded = loader.parse(writer.write(edited));

        assertThat(reloaded).isEqualTo(edited);
    }

    /** Parse, write, re-parse, and assert the two models are equal: the round-trip every case shares. */
    private void assertRoundTrips(String hocon) {
        MenuSpec original = loader.parse(hocon);
        MenuSpec reloaded = loader.parse(writer.write(original));
        assertThat(reloaded).isEqualTo(original);
    }

    private static String readResource(String path) {
        try (InputStream stream = MenuSpecRoundTripTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("missing test resource " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to read " + path, failure);
        }
    }

    /** A single menu that touches every menu-level and item-level key the loader understands. */
    private static final String KITCHEN_SINK = """
            title = "<gold>Everything Menu"
            rows = 6
            click-cooldown = 250
            refresh { enabled = true, interval-ticks = 20 }
            open-requirement = ["perm:menu.open"]
            open-actions = ["sound:BLOCK_NOTE_BLOCK_PLING", "message:opened"]
            close-actions = ["command:spawn"]
            placeholders {
              welcome = "<green>Hi %player%"
              vip_tag = "<gold>[VIP]"
            }
            fill-item {
              material = GRAY_STAINED_GLASS_PANE
              name = " "
            }
            items {
              decorated {
                slot = 0
                material = DIAMOND_SWORD
                name = "<aqua>Fancy"
                lore = ["<gray>Line one", "<gray>Line two"]
                lore-mode = append
                priority = 5
                update = true
                decor {
                  amount = 3
                  model-data = 1001
                  glow = true
                  flags = ["HIDE_ATTRIBUTES", "HIDE_ENCHANTS"]
                  unbreakable = true
                  enchantments = ["sharpness:5", "unbreaking:3"]
                  stored-enchantments = ["mending:1"]
                  leather-color = "#A1FF33"
                  damage = 100
                  item-model = "minecraft:diamond_sword"
                  rarity = EPIC
                  tooltip-style = "minecraft:fancy"
                  hide-tooltip = true
                  enchant-glint = true
                  enchantable = 10
                  attribute-modifiers = ["generic.attack_damage:5:add_number:hand"]
                  potion { type = STRENGTH, color = "#00AAFF", effects = ["speed:1:600"] }
                  banner { patterns = ["stripe_top:red", "border:white"] }
                  trim { material = diamond, pattern = sentry }
                  food { nutrition = 4, saturation = 2.4, can-always-eat = true }
                  tool { default-mining-speed = 1.0, damage-per-block = 2 }
                }
              }
              dynamic {
                slot = 1
                material = PAPER
                decor { amount = "%stack%", model-data = "%tier%" }
              }
              ranged {
                slots = ["2-4", "8"]
                material = STONE
                view = { requirements = ["perm:vip", "!has-empty-slots:1"], minimum = 1 }
              }
              gated {
                slot = 9
                material = EMERALD
                permission = "menu.gated"
                pages = "1-3,5"
              }
              clicky {
                slot = 10
                material = LEVER
                click {
                  left = ["command:kit daily", "close"]
                  shift-right = ["message:shifted"]
                  any = ["sound:UI_BUTTON_CLICK_1"]
                  right {
                    actions = ["command:pay Steve 100"]
                    requirements = ["has-money:100", "!has-cooldown:pay"]
                    minimum = 1
                    deny = ["message:cannot afford"]
                    stop-at-success = true
                    else = {
                      requirements = ["perm:refund"]
                      actions = ["message:refunded"]
                      else = { actions = ["message:tough luck"] }
                    }
                  }
                  middle {
                    do = [ { do = "command:delayed", delay = 20, chance = 50.0, deny = "message:missed" } ]
                  }
                }
              }
              listy {
                slot = 11
                material = CHEST
                list {
                  source = "warps"
                  template {
                    slots = ["12-14"]
                    material = ENDER_PEARL
                    name = "<yellow>%warp%"
                    click { left = ["warp:teleport"] }
                  }
                }
              }
              draggable {
                slot = 15
                material = HOPPER
                item-drag {
                  rules { materials = ["DIAMOND", "EMERALD"], min-amount = 2, name-contains = "gem" }
                  consume = true
                  actions = ["message:accepted", "sound:ENTITY_PLAYER_LEVELUP"]
                }
              }
            }
            """;

    /** A menu whose only special surface is a full Bedrock form with one widget of every kind. */
    private static final String BEDROCK_FORM = """
            rows = 1
            bedrock {
              title = "<gold>Form"
              content = "Pick options"
              widgets = [
                { type = label, text = "Welcome" }
                { type = input, name = "note", label = "Your note", placeholder = "type here", default = "hi" }
                { type = dropdown, name = "pick", label = "Choose", options = ["A", "B", "C"], default = 1 }
                { type = slider, name = "amount", label = "How many", min = 1, max = 10, step = 2, default = 4 }
                { type = toggle, name = "confirm", label = "Sure?", default = true }
              ]
              on-submit = ["message:submitted", "close"]
            }
            items { a { slot = 0, material = STONE, click { left = ["close"] } } }
            """;
}
