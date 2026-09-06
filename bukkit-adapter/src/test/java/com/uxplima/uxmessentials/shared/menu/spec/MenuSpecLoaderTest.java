package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.BedrockFormSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.DataComponents;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDragSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecException;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RefreshSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Requirement;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RequirementSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RichMeta;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

class MenuSpecLoaderTest {

    private static final String HOCON = """
            title = "@menu.test.title"
            rows = 3
            refresh { enabled = true, interval-ticks = 20 }
            open-requirement = [ "perm:x" ]
            items {
              border { slots = ["0-2"], material = GRAY_STAINED_GLASS_PANE, name = "" }
              go { slot = 4, material = "%icon%", name = "@n", view = ["warp:is-server-warp"], priority = 5,
                   click { left = ["warp:set-icon"], right = ["close"] }, update = true }
            }
            """;

    @Test
    void parsesMenu() {
        MenuSpec s = new MenuSpecLoader().parse(HOCON);
        assertThat(s.rows()).isEqualTo(3);
        assertThat(s.refresh().enabled()).isTrue();
        assertThat(s.items().get("border").slots().slots()).containsExactly(0, 1, 2);
        assertThat(s.items().get("go").click().actionsFor(ClickKind.LEFT))
                .extracting(Ref::id)
                .containsExactly("warp:set-icon");
        assertThat(s.items().get("go").view().requirements())
                .extracting(r -> r.condition().id())
                .containsExactly("warp:is-server-warp");
    }

    @Test
    void failsFastOnBadRows() {
        assertThatThrownBy(() -> new MenuSpecLoader().parse("rows = 9\nitems {}"))
                .isInstanceOf(MenuSpecException.class);
    }

    @Test
    void parsesAFlatViewListWithInversionAsAnAndBlock() {
        String hocon = "rows=1\nitems{ x{ slot=0, material=STONE, view=[\"has-money:100\", \"!has-empty-slots:1\"] } }";
        RequirementSpec view =
                new MenuSpecLoader().parse(hocon).items().get("x").view();

        assertThat(view.minimum())
                .as("a flat view list is an all-mandatory AND block")
                .isZero();
        assertThat(view.requirements())
                .extracting(r -> r.condition().id(), Requirement::inverted)
                .containsExactly(tuple("has-money:100", false), tuple("has-empty-slots:1", true));
    }

    @Test
    void parsesAMapViewBlockWithAMinimum() {
        String hocon = "rows=1\nitems{ x{ slot=0, material=STONE,"
                + " view={ requirements=[\"has-empty-slots:1\", \"has-empty-slots:9\"], minimum=1 } } }";
        RequirementSpec view =
                new MenuSpecLoader().parse(hocon).items().get("x").view();

        assertThat(view.minimum()).isEqualTo(1);
        assertThat(view.effectiveMinimum())
                .as("minimum 1 over two conditions is an OR")
                .isEqualTo(1);
        assertThat(view.requirements())
                .extracting(r -> r.condition().id())
                .containsExactly("has-empty-slots:1", "has-empty-slots:9");
    }

    @Test
    void anAbsentViewIsTheEmptyBlock() {
        RequirementSpec view = new MenuSpecLoader()
                .parse("rows=1\nitems{ x{ slot=0, material=STONE } }")
                .items()
                .get("x")
                .view();
        assertThat(view).isEqualTo(RequirementSpec.NONE);
    }

    @Test
    void thePermissionShorthandAppendsAMandatoryPermRequirement() {
        RequirementSpec view = new MenuSpecLoader()
                .parse("rows=1\nitems{ x{ slot=0, material=STONE, permission = \"a.b\" } }")
                .items()
                .get("x")
                .view();

        assertThat(view.minimum())
                .as("a shorthand-only view is still an all-mandatory AND")
                .isZero();
        assertThat(view.requirements())
                .extracting(r -> r.condition().id(), r -> r.condition().value(), Requirement::inverted)
                .containsExactly(tuple("perm", "a.b", false));
    }

    @Test
    void thePagesShorthandAppendsAMandatoryOnPageRequirement() {
        RequirementSpec view = new MenuSpecLoader()
                .parse("rows=1\nitems{ x{ slot=0, material=STONE, pages = \"2-3\" } }")
                .items()
                .get("x")
                .view();

        assertThat(view.requirements())
                .extracting(r -> r.condition().id(), r -> r.condition().value(), Requirement::inverted)
                .containsExactly(tuple("on-page", "2-3", false));
    }

    @Test
    void aViewBlockAndAPermissionShorthandBothLandInTheViewAsMandatory() {
        RequirementSpec view = new MenuSpecLoader()
                .parse("rows=1\nitems{ x{ slot=0, material=STONE, view=[\"has-money:100\"], permission = \"a.b\" } }")
                .items()
                .get("x")
                .view();

        assertThat(view.minimum())
                .as("appending to an AND view keeps it an AND")
                .isZero();
        assertThat(view.requirements())
                .extracting(r -> r.condition().id(), Requirement::inverted)
                .containsExactly(tuple("has-money:100", false), tuple("perm", false));
    }

    @Test
    void withoutAShorthandTheViewIsUnchanged() {
        RequirementSpec view = new MenuSpecLoader()
                .parse("rows=1\nitems{ x{ slot=0, material=STONE, view=[\"has-money:100\"] } }")
                .items()
                .get("x")
                .view();

        assertThat(view)
                .as("an item declaring no permission/pages key parses its view byte-identically to before")
                .isEqualTo(RequirementSpec.allOf(List.of(Ref.parse("has-money:100"))));
    }

    private static final String RICH = """
            rows = 1
            items {
              thing {
                slot = 0
                material = DIAMOND_SWORD
                decor {
                  amount = "%count%"
                  model-data = 7
                  glow = true
                  flags = ["HIDE_ATTRIBUTES"]
                  unbreakable = true
                  enchantments = ["sharpness:5", "unbreaking:3"]
                  stored-enchantments = ["mending:1"]
                  leather-color = "#A1FF33"
                  potion { type = STRENGTH, color = "#00AAFF", effects = ["speed:1:600"] }
                  banner { patterns = ["stripe_top:red", "circle:white"] }
                  trim { material = diamond, pattern = sentry }
                  damage = 100
                  item-model = "minecraft:diamond_sword"
                }
              }
            }
            """;

    @Test
    void parsesRichDecorIntoStringTokens() {
        ItemDecor decor = new MenuSpecLoader().parse(RICH).items().get("thing").decor();
        RichMeta meta = decor.meta();

        // A %placeholder% amount stays dynamic with the static amount kept at its default; model-data parses as an int.
        assertThat(decor.amount()).isEqualTo(1);
        assertThat(meta.dynamicAmount()).contains("%count%");
        assertThat(decor.modelData()).contains(7);
        assertThat(meta.dynamicModelData()).isEmpty();

        assertThat(meta.unbreakable()).isTrue();
        assertThat(meta.enchantments()).containsExactly("sharpness:5", "unbreaking:3");
        assertThat(meta.storedEnchantments()).containsExactly("mending:1");
        assertThat(meta.leatherColor()).contains("#A1FF33");
        assertThat(meta.potion().type()).contains("STRENGTH");
        assertThat(meta.potion().color()).contains("#00AAFF");
        assertThat(meta.potion().effects()).containsExactly("speed:1:600");
        assertThat(meta.bannerPatterns()).containsExactly("stripe_top:red", "circle:white");
        assertThat(meta.trim()).contains(new RichMeta.TrimSpec("diamond", "sentry"));
        assertThat(meta.damage()).contains(100);
        assertThat(meta.itemModel()).contains("minecraft:diamond_sword");
    }

    @Test
    void dynamicModelDataTokenIsCarriedAndStaticIsLeftEmpty() {
        ItemDecor decor = new MenuSpecLoader()
                .parse("rows=1\nitems{ x{ slot=0, decor{ model-data = \"%md%\" } } }")
                .items()
                .get("x")
                .decor();

        assertThat(decor.modelData()).isEmpty();
        assertThat(decor.meta().dynamicModelData()).contains("%md%");
    }

    @Test
    void absentDecorIsRichMetaNone() {
        ItemDecor decor = new MenuSpecLoader()
                .parse("rows=1\nitems{ x{ slot=0, material=STONE } }")
                .items()
                .get("x")
                .decor();

        assertThat(decor.meta()).isEqualTo(RichMeta.NONE);
        assertThat(decor.meta().components()).isEqualTo(DataComponents.NONE);
        assertThat(decor.amount()).isEqualTo(1);
    }

    private static final String COMPONENTS = """
            rows = 1
            items {
              thing {
                slot = 0
                material = DIAMOND_SWORD
                decor {
                  rarity = EPIC
                  tooltip-style = "minecraft:fancy"
                  hide-tooltip = true
                  enchant-glint = true
                  enchantable = 10
                  attribute-modifiers = ["generic.attack_damage:5:add_number:hand", "generic.max_health:2:add_number:any"]
                  food { nutrition = 4, saturation = 2.4, can-always-eat = true }
                  tool { default-mining-speed = 1.0, damage-per-block = 2 }
                }
              }
            }
            """;

    @Test
    void parsesDataComponentsIntoTokens() {
        DataComponents components = new MenuSpecLoader()
                .parse(COMPONENTS)
                .items()
                .get("thing")
                .decor()
                .meta()
                .components();

        assertThat(components.rarity()).contains("EPIC");
        assertThat(components.tooltipStyle()).contains("minecraft:fancy");
        assertThat(components.hideTooltip()).contains(true);
        assertThat(components.enchantGlint()).contains(true);
        assertThat(components.enchantable()).contains(10);
        assertThat(components.attributeModifiers())
                .containsExactly("generic.attack_damage:5:add_number:hand", "generic.max_health:2:add_number:any");
        assertThat(components.food())
                .contains(new DataComponents.FoodSpec(
                        java.util.Optional.of(4), java.util.Optional.of(2.4), java.util.Optional.of(true)));
        assertThat(components.tool())
                .contains(new DataComponents.ToolSpec(java.util.Optional.of(1.0), java.util.Optional.of(2)));
    }

    @Test
    void parsesTheTooltipKeysAndLeavesThemUnsetWhenAbsent() {
        DataComponents declared =
                new MenuSpecLoader().parse("""
                        rows = 1
                        items { t { slot = 0, decor {
                          hide-vanilla-tooltip = false
                          hidden-components = ["dyed_color", "equippable"]
                        } } }
                        """).items().get("t").decor().meta().components();

        assertThat(declared.hideVanillaTooltip()).contains(false);
        assertThat(declared.hiddenComponents()).containsExactly("dyed_color", "equippable");

        // Unset is not the same as false: the renderer reads an absent key as "this is a button, silence the
        // client", and an operator who never writes the key must not be forced either way by the loader.
        DataComponents silent = new MenuSpecLoader()
                .parse("rows=1\nitems{ t{ slot=0 } }")
                .items()
                .get("t")
                .decor()
                .meta()
                .components();
        assertThat(silent.hideVanillaTooltip()).isEmpty();
        assertThat(silent.hiddenComponents()).isEmpty();
    }

    private static final String MODIFIERS = """
            rows = 1
            items {
              b {
                slot = 0
                material = DIAMOND
                click {
                  left = [
                    { do = "command:eco give 100", delay = 20, chance = 25, deny = "message:none" }
                    "sound:UI_BUTTON_CLICK"
                  ]
                }
              }
            }
            """;

    @Test
    void parsesTheMapFormWithDelayChanceAndDenyAlongsidePlainScalars() {
        List<Ref> actions =
                new MenuSpecLoader().parse(MODIFIERS).items().get("b").click().actionsFor(ClickKind.LEFT);

        assertThat(actions).hasSize(2);

        Ref first = actions.get(0);
        assertThat(first.id()).isEqualTo("command");
        assertThat(first.value()).isEqualTo("eco give 100");
        assertThat(first.delayTicks()).isEqualTo(20);
        assertThat(first.chance()).isEqualTo(25.0);
        assertThat(first.deny()).map(Ref::id).contains("message");

        // The scalar entry parses exactly as before: no modifiers.
        Ref second = actions.get(1);
        assertThat(second.id()).isEqualTo("sound");
        assertThat(second.delayTicks()).isZero();
        assertThat(second.chance()).isEqualTo(100.0);
        assertThat(second.deny()).isEmpty();
    }

    @Test
    void mapEntryWithoutAnActionTokenIsSkipped() {
        List<Ref> actions = new MenuSpecLoader()
                .parse("rows=1\nitems{ b{ slot=0, click{ left = [ { chance = 50 }, \"close\" ] } } }")
                .items()
                .get("b")
                .click()
                .actionsFor(ClickKind.LEFT);

        assertThat(actions).extracting(Ref::id).containsExactly("close");
    }

    @Test
    void aPlainStringListStillParsesUnchanged() {
        List<Ref> actions = new MenuSpecLoader()
                .parse("rows=1\nitems{ b{ slot=0, click{ left = [\"give\", \"close\"] } } }")
                .items()
                .get("b")
                .click()
                .actionsFor(ClickKind.LEFT);

        assertThat(actions).extracting(Ref::id).containsExactly("give", "close");
        assertThat(actions).allSatisfy(ref -> {
            assertThat(ref.delayTicks()).isZero();
            assertThat(ref.chance()).isEqualTo(100.0);
            assertThat(ref.deny()).isEmpty();
        });
    }

    @Test
    void unsetToggleStaysEmptySoItNeverOverridesTheItem() {
        DataComponents components = new MenuSpecLoader()
                .parse("rows=1\nitems{ x{ slot=0, decor{ rarity = RARE } } }")
                .items()
                .get("x")
                .decor()
                .meta()
                .components();

        assertThat(components.rarity()).contains("RARE");
        assertThat(components.hideTooltip()).isEmpty();
        assertThat(components.enchantGlint()).isEmpty();
        assertThat(components.food()).isEmpty();
        assertThat(components.tool()).isEmpty();
        assertThat(components.attributeModifiers()).isEmpty();
    }

    @Test
    void parsesTheChestOnlyFlagFromTheMenuRoot() {
        MenuSpec spec = new MenuSpecLoader().parse("chest-only = true\nrows=1\nitems{ x{ slot=0, material=STONE } }");

        assertThat(spec.chestOnly())
                .as("chest-only = true opts the menu out of the Bedrock form redirect")
                .isTrue();
    }

    @Test
    void anAbsentChestOnlyFlagIsFalse() {
        MenuSpec spec = new MenuSpecLoader().parse("rows=1\nitems{ x{ slot=0, material=STONE } }");

        assertThat(spec.chestOnly())
                .as("no chest-only node means the menu is a form candidate for a Bedrock viewer")
                .isFalse();
    }

    @Test
    void parsesTheInventoryTypeTokenFromTheMenuRoot() {
        MenuSpec spec = new MenuSpecLoader().parse("inventory-type = \"hopper\"\nitems{ x{ slot=0, material=STONE } }");

        assertThat(spec.inventoryType()).contains("hopper");
    }

    @Test
    void anAbsentInventoryTypeIsAnEmptyOptionalAndTheMenuIsAChest() {
        MenuSpec spec = new MenuSpecLoader().parse("rows=1\nitems{ x{ slot=0, material=STONE } }");

        assertThat(spec.inventoryType())
                .as("no inventory-type node means the default rows-based chest")
                .isEmpty();
    }

    @Test
    void aBlankInventoryTypeIsAnEmptyOptional() {
        MenuSpec spec = new MenuSpecLoader().parse("inventory-type = \"\"\nrows=1\nitems{ x{ slot=0 } }");

        assertThat(spec.inventoryType()).isEmpty();
    }

    @Test
    void aNonChestMenuNeedNotDeclareRowsAndKeepsOversizeSlotsForRenderToSkip() {
        // A hopper spec omits rows: the loader defaults rows to the largest chest so slot 8 loads (it exceeds the
        // hopper's five slots but not the chest fallback), to be skipped later at render rather than rejected here.
        MenuSpec spec = new MenuSpecLoader()
                .parse("inventory-type = \"hopper\"\nitems{ a{ slots=[\"0-4\"], material=STONE }, b{ slot=8 } }");

        assertThat(spec.rows()).isEqualTo(6);
        assertThat(spec.items().get("b").slots().slots()).containsExactly(8);
    }

    @Test
    void theDelegatingConstructorDefaultsTheInventoryTypeToEmpty() {
        MenuSpec spec =
                new MenuSpec("t", 1, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), java.util.Map.of());

        assertThat(spec.inventoryType())
                .as("the seven-argument constructor keeps every existing call-site on the default chest")
                .isEmpty();
    }

    @Test
    void parsesTheLocalPlaceholdersBlock() {
        MenuSpec spec = new MenuSpecLoader()
                .parse("placeholders { header = \"<gold>The Shop\", greeting = \"Hi %player%\" }\n"
                        + "items { x { slot = 0, material = STONE } }");

        assertThat(spec.placeholders())
                .as("the menu's own placeholders {} block parses into spec.placeholders()")
                .containsEntry("header", "<gold>The Shop")
                .containsEntry("greeting", "Hi %player%");
    }

    @Test
    void aMenuWithoutAPlaceholdersBlockHasAnEmptyLocalMap() {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 1\nitems { x { slot = 0, material = STONE } }");

        assertThat(spec.placeholders())
                .as("a menu declaring no placeholders {} block carries an empty local map")
                .isEmpty();
    }

    @Test
    void aMenuWithoutAPlaceholdersBlockParsesIdenticallyEachTime() {
        String hocon = "rows = 1\nitems { x { slot = 0, material = STONE } }";

        assertThat(new MenuSpecLoader().parse(hocon))
                .as("adding the local placeholder field leaves a block-less menu byte-identical to itself")
                .isEqualTo(new MenuSpecLoader().parse(hocon));
    }

    @Test
    void aPositiveUpdateIntervalKeyEnablesTheRefreshAtThatCadence() {
        RefreshSpec refresh = new MenuSpecLoader()
                .parse("update-interval = 40\nrows = 1\nitems {}")
                .refresh();

        assertThat(refresh).isEqualTo(new RefreshSpec(true, 40));
    }

    @Test
    void aRefreshBlockAloneIsHonouredWhenNoUpdateIntervalIsSet() {
        RefreshSpec refresh = new MenuSpecLoader()
                .parse("rows = 1\nrefresh { enabled = true, interval-ticks = 20 }\nitems {}")
                .refresh();

        assertThat(refresh).isEqualTo(new RefreshSpec(true, 20));
    }

    @Test
    void neitherUpdateIntervalNorARefreshBlockLeavesRefreshDisabled() {
        RefreshSpec refresh = new MenuSpecLoader().parse("rows = 1\nitems {}").refresh();

        assertThat(refresh).isEqualTo(new RefreshSpec(false, 0));
    }

    @Test
    void updateIntervalWinsOverARefreshBlockWhenBothAreSet() {
        RefreshSpec refresh = new MenuSpecLoader()
                .parse("update-interval = 40\nrefresh { enabled = true, interval-ticks = 20 }\nrows = 1\nitems {}")
                .refresh();

        assertThat(refresh)
                .as("the convenience update-interval key takes precedence over an explicit refresh block")
                .isEqualTo(new RefreshSpec(true, 40));
    }

    @Test
    void aNonPositiveUpdateIntervalFallsBackToTheRefreshBlock() {
        RefreshSpec refresh = new MenuSpecLoader()
                .parse("update-interval = 0\nrefresh { enabled = true, interval-ticks = 15 }\nrows = 1\nitems {}")
                .refresh();

        assertThat(refresh)
                .as("update-interval only takes over when it is a positive tick count")
                .isEqualTo(new RefreshSpec(true, 15));
    }

    @Test
    void aChestWithoutRowsAutoSizesToFitItsHighestSlot() {
        MenuSpec spec = new MenuSpecLoader()
                .parse("items { a { slot = 0, material = STONE }, b { slot = 20, material = STONE } }");

        assertThat(spec.rows()).as("a slot in the third row needs three rows").isEqualTo(3);
    }

    @Test
    void aChestAutoSizesToFiveRowsForASlotInTheFifthRow() {
        MenuSpec spec = new MenuSpecLoader().parse("items { a { slot = 40, material = STONE } }");

        assertThat(spec.rows()).isEqualTo(5);
    }

    @Test
    void anExplicitButTooSmallRowsGrowsToFitAHigherSlot() {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 2\nitems { a { slot = 30, material = STONE } }");

        assertThat(spec.rows())
                .as("a declared two rows grows to four to hold a slot in the fourth row")
                .isEqualTo(4);
    }

    @Test
    void aBareChestWithNoItemsIsOneRowNotZero() {
        MenuSpec spec = new MenuSpecLoader().parse("items {}");

        assertThat(spec.rows()).isEqualTo(1);
    }

    @Test
    void anExplicitRowsThatAlreadyFitsEverySlotIsUnchanged() {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 3\nitems { a { slot = 26, material = STONE } }");

        assertThat(spec.rows())
                .as("three declared rows already hold every slot below 27, so the count is untouched")
                .isEqualTo(3);
    }

    @Test
    void aPackedChestCapsAtSixRows() {
        MenuSpec spec = new MenuSpecLoader().parse("items { a { slot = 53, material = STONE } }");

        assertThat(spec.rows())
                .as("a slot in the last of six rows sizes the chest to the six-row ceiling")
                .isEqualTo(6);
    }

    @Test
    void aListTemplateSlotAlsoSizesTheChest() {
        MenuSpec spec = new MenuSpecLoader()
                .parse("items { grid { list { source = \"warps:all\", template { slot = 30, material = STONE } } } }");

        assertThat(spec.rows())
                .as("a paginated list's content slots count toward the auto-sized rows")
                .isEqualTo(4);
    }

    @Test
    void aChestSlotBeyondTheSixRowMaximumIsAFailFastConfigError() {
        // A chest renders at most six rows (54 slots); a slot past that can never be shown, so, consistent with the
        // loader's fail-fast slot check and the six-row ceiling the auto-sizer parses against: it is a loud error.
        assertThatThrownBy(() -> new MenuSpecLoader().parse("items { a { slot = 60, material = STONE } }"))
                .isInstanceOf(MenuSpecException.class);
    }

    private static final String PATTERNS = """
            rows = 1
            patterns {
              shop-button {
                material = "%mat%"
                name = "<gold>%label%"
                lore = ["<gray>Click to buy %label%", "<gray>Price: %price%"]
                click { left = ["open:%target%"] }
                defaults { mat = "STONE", price = "0" }
              }
            }
            items {
              diamonds {
                pattern = "shop-button"
                slots = [0]
                vars { mat = "DIAMOND", label = "Diamonds", target = "diamond-shop", price = "5" }
                name = "<aqua>%label% (deal)"
              }
            }
            """;

    @Test
    void aPatternResolvesItsVarsIntoTheItemAndTheItemFieldWins() {
        MenuItemSpec item = new MenuSpecLoader().parse(PATTERNS).items().get("diamonds");

        assertThat(item.material()).isEqualTo("DIAMOND");
        assertThat(item.name())
                .as("the item's own name overrides the pattern's, and its own %label% is filled too")
                .isEqualTo("<aqua>Diamonds (deal)");
        assertThat(item.lore()).containsExactly("<gray>Click to buy Diamonds", "<gray>Price: 5");
        assertThat(item.click().actionsFor(ClickKind.LEFT))
                .extracting(Ref::id, Ref::value)
                .containsExactly(tuple("open", "diamond-shop"));
        assertThat(item.slots().slots()).containsExactly(0);
    }

    @Test
    void anOmittedVarFallsBackToThePatternDefault() {
        String hocon = """
                rows = 1
                patterns { p { name = "Price: %price%", defaults { price = "0" } } }
                items { x { slot = 0, pattern = "p", vars { } } }
                """;
        MenuItemSpec item = new MenuSpecLoader().parse(hocon).items().get("x");

        assertThat(item.name())
                .as("price omitted from vars falls back to the pattern default")
                .isEqualTo("Price: 0");
    }

    @Test
    void anUnknownTokenIsLeftVerbatimForRenderTime() {
        String hocon = """
                rows = 1
                patterns { p { material = STONE, lore = ["<gray>%player_name%", "<gray>%label%"] } }
                items { x { slot = 0, pattern = "p", vars { label = "Hi" } } }
                """;
        MenuItemSpec item = new MenuSpecLoader().parse(hocon).items().get("x");

        assertThat(item.lore())
                .as("a declared var is filled now; an undeclared %placeholder% stays for the renderer")
                .containsExactly("<gray>%player_name%", "<gray>Hi");
    }

    @Test
    void substitutionRecursesIntoNestedMapsAndLists() {
        String hocon = """
                rows = 1
                patterns {
                  p {
                    material = "%mat%"
                    decor { potion { type = "%ptype%", effects = ["%effect%"] } }
                  }
                }
                items { x { slot = 0, pattern = "p",
                            vars { mat = "POTION", ptype = "STRENGTH", effect = "speed:1:600" } } }
                """;
        MenuItemSpec item = new MenuSpecLoader().parse(hocon).items().get("x");

        assertThat(item.material()).isEqualTo("POTION");
        assertThat(item.decor().meta().potion().type()).contains("STRENGTH");
        assertThat(item.decor().meta().potion().effects()).containsExactly("speed:1:600");
    }

    @Test
    void anItemClickOverrideReplacesTheTemplateClickWholesale() {
        String hocon = """
                rows = 1
                patterns { p { material = STONE, click { left = ["close"], right = ["open:a"] } } }
                items { x { slot = 0, pattern = "p", click { right = ["open:b"] } } }
                """;
        ClickSpec click = new MenuSpecLoader().parse(hocon).items().get("x").click();

        assertThat(click.actionsFor(ClickKind.RIGHT))
                .extracting(Ref::id, Ref::value)
                .containsExactly(tuple("open", "b"));
        assertThat(click.actionsFor(ClickKind.LEFT))
                .as("an item click block replaces the whole template click, so the template's left gesture is gone")
                .isEmpty();
    }

    @Test
    void anItemNamingAnUnknownPatternParsesFromItsOwnFields() {
        String hocon = "rows=1\nitems { x { slot = 0, pattern = \"nope\", material = EMERALD, name = \"Own\" } }";
        MenuItemSpec item = new MenuSpecLoader().parse(hocon).items().get("x");

        assertThat(item.material())
                .as("an unknown pattern name is warned about, not fatal; the item parses its own fields")
                .isEqualTo("EMERALD");
        assertThat(item.name()).isEqualTo("Own");
    }

    @Test
    void aPatternBlockDoesNotAffectAnItemThatDoesNotReferenceIt() {
        String hocon = """
                rows = 1
                patterns { p { material = DIAMOND } }
                items { plain { slot = 0, material = STONE, name = "Plain" } }
                """;
        MenuItemSpec item = new MenuSpecLoader().parse(hocon).items().get("plain");

        assertThat(item.material()).isEqualTo("STONE");
        assertThat(item.name()).isEqualTo("Plain");
        assertThat(item.slots().slots()).containsExactly(0);
    }

    @Test
    void aNestedPatternKeyOnATemplateIsIgnoredResolvingOnlyOneLevel() {
        String hocon = """
                rows = 1
                patterns {
                  base { material = STONE }
                  derived { pattern = "base", material = DIAMOND, name = "%who%" }
                }
                items { x { slot = 0, pattern = "derived", vars { who = "Steve" } } }
                """;
        MenuItemSpec item = new MenuSpecLoader().parse(hocon).items().get("x");

        // Resolution runs one level only: 'derived' is used as written, its own pattern="base" key is ignored, so
        // the material stays DIAMOND rather than being pulled down to STONE from the base template.
        assertThat(item.material()).isEqualTo("DIAMOND");
        assertThat(item.name()).isEqualTo("Steve");
    }

    @Test
    void aGlobalPatternResolvesForAMenuWithNoLocalPatterns() throws Exception {
        ConfigurationNode global = patternsNode("""
                patterns { hub-button { material = "%mat%", name = "<gold>%label%" } }
                """);
        String hocon = """
                rows = 1
                items { hub { slots = [0], pattern = "hub-button", vars { mat = "DIAMOND", label = "Hub" } } }
                """;
        MenuItemSpec item = new MenuSpecLoader().parse(hocon, global).items().get("hub");

        assertThat(item.material())
                .as("a pattern from the shared file resolves for a menu that declares none of its own")
                .isEqualTo("DIAMOND");
        assertThat(item.name()).isEqualTo("<gold>Hub");
    }

    @Test
    void aMenuLocalPatternOverridesAGlobalOfTheSameName() throws Exception {
        ConfigurationNode global = patternsNode("""
                patterns { button { material = STONE, name = "Global" } }
                """);
        String hocon = """
                rows = 1
                patterns { button { material = DIAMOND, name = "Local" } }
                items { x { slot = 0, pattern = "button" } }
                """;
        MenuItemSpec item = new MenuSpecLoader().parse(hocon, global).items().get("x");

        assertThat(item.material())
                .as("the menu's own template wins the name clash with the shared one")
                .isEqualTo("DIAMOND");
        assertThat(item.name()).isEqualTo("Local");
    }

    @Test
    void anUndefinedPatternWithEmptyGlobalsParsesItsOwnFieldsWithoutThrowing() throws Exception {
        String hocon = "rows=1\nitems { x { slot = 0, pattern = \"missing\", material = EMERALD } }";
        MenuItemSpec item =
                new MenuSpecLoader().parse(hocon, patternsNode("")).items().get("x");

        assertThat(item.material())
                .as("no shared patterns and an undefined name is the slice-A warn path, not a throw")
                .isEqualTo("EMERALD");
    }

    @Test
    void theNoGlobalsOverloadEqualsTheGlobalsOverloadWithAnEmptyNode() throws Exception {
        MenuSpec withoutGlobals = new MenuSpecLoader().parse(PATTERNS);
        MenuSpec withEmptyGlobals = new MenuSpecLoader().parse(PATTERNS, patternsNode(""));

        assertThat(withEmptyGlobals)
                .as("passing an empty node delegates byte-identically, so the pattern-free overload is unchanged")
                .isEqualTo(withoutGlobals);
    }

    @Test
    void aListTemplateResolvesAGlobalPattern() throws Exception {
        ConfigurationNode global = patternsNode("""
                patterns { row { material = "%mat%", name = "<gray>%label%" } }
                """);
        String hocon = """
                rows = 1
                items {
                  grid {
                    list {
                      source = "warps:all"
                      template { slots = [0], pattern = "row", vars { mat = "PAPER", label = "Warp" } }
                    }
                  }
                }
                """;
        MenuItemSpec template = new MenuSpecLoader()
                .parse(hocon, global)
                .items()
                .get("grid")
                .list()
                .orElseThrow()
                .template();

        assertThat(template.material())
                .as("a list template naming a shared pattern is expanded per entry from that shared pattern")
                .isEqualTo("PAPER");
        assertThat(template.name()).isEqualTo("<gray>Warp");
    }

    @Test
    void aListTemplateResolvesAMenuLocalPattern() {
        // Confirms slice-A already covers list-expansion: parseList feeds its template through parseItem with the
        // pattern map, so a list whose template names a (here menu-local) pattern is stamped from it per entry.
        String hocon = """
                rows = 1
                patterns { entry { material = "%mat%", name = "%label%" } }
                items {
                  grid {
                    list {
                      source = "warps:all"
                      template { slots = [0], pattern = "entry", vars { mat = "MAP", label = "Home" } }
                    }
                  }
                }
                """;
        MenuItemSpec template = new MenuSpecLoader()
                .parse(hocon)
                .items()
                .get("grid")
                .list()
                .orElseThrow()
                .template();

        assertThat(template.material()).isEqualTo("MAP");
        assertThat(template.name()).isEqualTo("Home");
    }

    /** Parse a HOCON document and hand back its {@code patterns} node, the shape a shared {@code patterns.conf} holds. */
    private static ConfigurationNode patternsNode(String hocon) throws Exception {
        return HoconConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(hocon)))
                .build()
                .load()
                .node("patterns");
    }

    private static final String BORDER_LAYOUT = """
            layout = [
              "GGGGGGGGG"
              "G.......G"
              "GGGGGGGGG"
            ]
            items {
              G { material = GRAY_STAINED_GLASS_PANE, name = " " }
            }
            """;

    @Test
    void aLayoutCharItemTakesItsSlotsFromTheGridDrawnAroundIt() {
        MenuSpec spec = new MenuSpecLoader().parse(BORDER_LAYOUT);

        assertThat(spec.items().get("G").slots().slots())
                .as("the char 'G' claims every border cell the layout paints, the '.' interior staying empty")
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26);
    }

    @Test
    void aLayoutCharWithNoMatchingItemLeavesThoseSlotsEmptyWithoutError() {
        // 'X' is drawn but no item declares it, so nothing occupies those cells and the parse still succeeds.
        MenuSpec spec = new MenuSpecLoader().parse("""
                        layout = [ "XXXXX" ]
                        items { }
                        """);

        assertThat(spec.items()).isEmpty();
    }

    @Test
    void aLayoutPositionWinsOverAnItemsOwnDeclaredSlots() {
        MenuSpec spec = new MenuSpecLoader().parse("""
                        layout = [ "G........" ]
                        items { G { material = STONE, slots = [40] } }
                        """);

        assertThat(spec.items().get("G").slots().slots())
                .as("a layout char ignores its own slot/slots; the grid position wins, so 40 is never used")
                .containsExactly(0);
    }

    @Test
    void spaceAndDotAreBothEmptyCellsAndAMultiRowGridMapsRowMajor() {
        MenuSpec spec = new MenuSpecLoader().parse("""
                        layout = [
                          "A A"
                          ".B."
                        ]
                        items {
                          A { material = STONE }
                          B { material = STONE }
                        }
                        """);

        assertThat(spec.items().get("A").slots().slots())
                .as("both a space and a dot are empty, so 'A' skips the gap between its two ends")
                .containsExactly(0, 2);
        assertThat(spec.items().get("B").slots().slots())
                .as("the second row's middle column is slot 9 + 1")
                .containsExactly(10);
    }

    @Test
    void aFillItemOccupiesEveryEmptySlotAtALowPriorityUnderAReservedId() {
        MenuSpec spec = new MenuSpecLoader().parse("""
                        rows = 3
                        items { real { slot = 13, material = DIAMOND } }
                        fill-item { material = BLACK_STAINED_GLASS_PANE, name = " " }
                        """);

        MenuItemSpec fill = spec.items().get("__fill__");
        assertThat(fill).as("a fill-item is added under the reserved id").isNotNull();
        assertThat(fill.priority())
                .as("the fill sits at the lowest priority so any real item wins a slot they share")
                .isEqualTo(Integer.MIN_VALUE);
        assertThat(fill.material()).isEqualTo("BLACK_STAINED_GLASS_PANE");
        assertThat(fill.slots().slots())
                .as("every slot of a three-row chest except the one the real item holds")
                .hasSize(26)
                .doesNotContain(13)
                .contains(0, 12, 14, 26);
    }

    @Test
    void aMenuWithoutAFillItemAddsNoReservedEntry() {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 1\nitems { a { slot = 0, material = STONE } }");

        assertThat(spec.items()).doesNotContainKey("__fill__");
    }

    @Test
    void aLayoutAndAFillItemTogetherCoverEverySlot() {
        MenuSpec spec = new MenuSpecLoader().parse("""
                        layout = [
                          "GGGGGGGGG"
                          "G.......G"
                          "GGGGGGGGG"
                        ]
                        items { G { material = GRAY_STAINED_GLASS_PANE, name = " " } }
                        fill-item { material = BLACK_STAINED_GLASS_PANE, name = " " }
                        """);

        assertThat(spec.items().get("__fill__").slots().slots())
                .as("the fill takes the seven interior cells the border layout leaves empty")
                .containsExactly(10, 11, 12, 13, 14, 15, 16);
        java.util.Set<Integer> covered =
                new java.util.HashSet<>(spec.items().get("G").slots().slots());
        covered.addAll(spec.items().get("__fill__").slots().slots());
        assertThat(covered)
                .as("border plus interior account for every one of the 27 cells")
                .hasSize(27);
    }

    @Test
    void aMenuWithNeitherLayoutNorFillItemParsesItsItemsUnchanged() {
        MenuSpec spec = new MenuSpecLoader()
                .parse("rows = 2\nitems { border { slots = [\"0-2\"], material = STONE }, go { slot = 4 } }");

        assertThat(spec.items().keySet()).containsExactlyInAnyOrder("border", "go");
        assertThat(spec.items().get("border").slots().slots()).containsExactly(0, 1, 2);
        assertThat(spec.items().get("go").slots().slots()).containsExactly(4);
    }

    @Test
    void bindsTheDropAndDoubleClickGestures() {
        String hocon = """
                rows = 1
                items { x { slot = 0, material = DIAMOND, name = "x", click {
                  drop = ["close"]
                  ctrl_drop = ["open:a"]
                  double_click = ["open:b"]
                } } }
                """;
        ClickSpec click = new MenuSpecLoader().parse(hocon).items().get("x").click();

        assertThat(click.actionsFor(ClickKind.DROP)).extracting(Ref::id).containsExactly("close");
        assertThat(click.actionsFor(ClickKind.CONTROL_DROP))
                .extracting(Ref::value)
                .containsExactly("a");
        assertThat(click.actionsFor(ClickKind.DOUBLE_CLICK))
                .extracting(Ref::value)
                .containsExactly("b");
    }

    @Test
    void acceptsBothKebabAndSnakeSpellingsOfTheNewGestures() {
        String hocon = """
                rows = 1
                items { x { slot = 0, material = DIAMOND, name = "x", click {
                  control-drop = ["open:a"]
                  double-click = ["open:b"]
                } } }
                """;
        ClickSpec click = new MenuSpecLoader().parse(hocon).items().get("x").click();

        assertThat(click.actionsFor(ClickKind.CONTROL_DROP))
                .extracting(Ref::value)
                .containsExactly("a");
        assertThat(click.actionsFor(ClickKind.DOUBLE_CLICK))
                .extracting(Ref::value)
                .containsExactly("b");
    }

    @Test
    void readsThePerMenuClickCooldown() {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 1\nclick-cooldown = 500\nitems {}");

        assertThat(spec.clickCooldownMs()).isEqualTo(500L);
    }

    @Test
    void aMenuWithNoClickCooldownKeyDefersToTheGlobalDefault() {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 1\nitems {}");

        assertThat(spec.clickCooldownMs())
                .as("no key means zero, i.e. inherit the server-wide default rather than set a per-menu window")
                .isZero();
    }

    @Test
    void parsesAnItemDragBlock() {
        String hocon = """
                rows = 1
                items { drop-slot {
                  slot = 0, material = CHEST, name = "Deposit",
                  item-drag {
                    rules { materials = ["diamond", "Emerald"], min-amount = 2, name-contains = "shiny" }
                    consume = true
                    actions = ["data-set:dropped %drag_material%"]
                  }
                } }
                """;
        ItemDragSpec drag = new MenuSpecLoader()
                .parse(hocon)
                .items()
                .get("drop-slot")
                .itemDrag()
                .orElseThrow();

        // Material names are upper-cased so the runtime can match them against Material.name() case-insensitively.
        assertThat(drag.rules().materials()).containsExactly("DIAMOND", "EMERALD");
        assertThat(drag.rules().minAmount()).isEqualTo(2);
        assertThat(drag.rules().nameContains()).isEqualTo("shiny");
        assertThat(drag.consume()).isTrue();
        assertThat(drag.actions()).extracting(Ref::id).containsExactly("data-set:dropped %drag_material%");
    }

    @Test
    void anItemDragBlockDefaultsMinAmountToOneAnyMaterialNoNameCheckAndNoConsume() {
        String hocon = """
                rows = 1
                items { drop-slot {
                  slot = 0, material = CHEST, name = "Deposit",
                  item-drag { actions = ["message:got it"] }
                } }
                """;
        ItemDragSpec drag = new MenuSpecLoader()
                .parse(hocon)
                .items()
                .get("drop-slot")
                .itemDrag()
                .orElseThrow();

        assertThat(drag.rules().materials())
                .as("an empty whitelist means any material passes")
                .isEmpty();
        assertThat(drag.rules().minAmount()).isEqualTo(1);
        assertThat(drag.rules().nameContains()).isEmpty();
        assertThat(drag.consume()).isFalse();
    }

    @Test
    void anItemWithNoItemDragBlockHasNone() {
        String hocon = """
                rows = 1
                items { plain { slot = 0, material = STONE, name = "x" } }
                """;
        MenuItemSpec item = new MenuSpecLoader().parse(hocon).items().get("plain");

        assertThat(item.itemDrag()).isEmpty();
    }

    @Test
    void parsesTheBottomInventoryFlagAndAcceptsABottomSlot() {
        String hocon = """
                bottom-inventory = true
                rows = 6
                items {
                  top { slot = 4, material = DIAMOND, name = "top" }
                  bot { slot = 54, material = EMERALD, name = "bottom" }
                }
                """;
        MenuSpec spec = new MenuSpecLoader().parse(hocon);

        assertThat(spec.bottomInventory())
                .as("the bottom-inventory flag is read from the spec")
                .isTrue();
        assertThat(spec.rows())
                .as("its top is the full six-row double chest the raw-slot geometry needs")
                .isEqualTo(6);
        assertThat(spec.items().get("bot").slots().slots())
                .as("a raw slot in the 54..89 bottom range is accepted only because the flag widens the ceiling")
                .containsExactly(54);
    }

    @Test
    void aBottomInventoryMenuIgnoresAndWarnsOnANonChestInventoryType() {
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(MenuSpecLoader.class.getName());
        java.util.List<java.util.logging.LogRecord> records = new java.util.ArrayList<>();
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        logger.addHandler(handler);
        try {
            MenuSpec spec = new MenuSpecLoader().parse("""
                            bottom-inventory = true
                            inventory-type = hopper
                            items { bot { slot = 60, material = EMERALD, name = "b" } }
                            """);

            assertThat(spec.bottomInventory()).isTrue();
            assertThat(spec.inventoryType())
                    .as("a bottom-inventory menu is chest-only, so a declared inventory-type is dropped")
                    .isEmpty();
            assertThat(records)
                    .as("dropping the inventory-type is warned so the operator sees why it was ignored")
                    .anyMatch(record -> record.getLevel() == java.util.logging.Level.WARNING
                            && String.valueOf(record.getMessage()).contains("bottom-inventory"));
        } finally {
            logger.removeHandler(handler);
        }
    }

    private static final String BEDROCK = """
            title = "Warps"
            rows = 1
            items { a { slot = 0, material = STONE, name = "A" } }
            bedrock {
              title = "Create Warp"
              content = "Fill in the details"
              widgets = [
                { type = label,    text = "<gold>New warp" }
                { type = input,    name = warpname, label = "Name", placeholder = "spawn", default = "" }
                { type = dropdown, name = category, label = "Category", options = ["PvP","Build","Hub"], default = 1 }
                { type = slider,   name = cost,     label = "Cost", min = 0, max = 1000, step = 50, default = 100 }
                { type = toggle,   name = public,   label = "Public?", default = true }
              ]
              on-submit = [ "record:%warpname%|%category%|%cost%", "message:done" ]
            }
            """;

    @Test
    void parsesTheBedrockCustomFormBlockIntoItsWidgetRecordsAndOnSubmitRefs() {
        BedrockFormSpec form = new MenuSpecLoader().parse(BEDROCK).bedrock().orElseThrow();

        assertThat(form.title()).isEqualTo("Create Warp");
        assertThat(form.content()).isEqualTo("Fill in the details");
        assertThat(form.widgets())
                .as("every widget parses into its matching record, in declared order, with strings kept verbatim")
                .containsExactly(
                        new BedrockWidget.Label("<gold>New warp"),
                        new BedrockWidget.Input("warpname", "Name", "spawn", ""),
                        new BedrockWidget.Dropdown("category", "Category", List.of("PvP", "Build", "Hub"), 1),
                        new BedrockWidget.Slider("cost", "Cost", 0, 1000, 50, 100),
                        new BedrockWidget.Toggle("public", "Public?", true));
        assertThat(form.onSubmit())
                .extracting(Ref::id)
                .as("the on-submit actions parse through the same ref parser the click actions use")
                .containsExactly("record:%warpname%|%category%|%cost%", "message");
    }

    @Test
    void aMenuWithoutABedrockBlockHasNoBedrockForm() {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 1\nitems { a { slot = 0, material = STONE } }");
        assertThat(spec.bedrock())
                .as("a menu that declares no bedrock {} block keeps the automatic Bedrock degradation")
                .isEmpty();
    }

    @Test
    void aBedrockWidgetWithAnUnknownTypeIsSkippedRatherThanAbortingTheMenu() {
        String hocon = """
                rows = 1
                items { a { slot = 0, material = STONE } }
                bedrock {
                  title = "T"
                  widgets = [ { type = mystery, name = x, label = "X" }, { type = toggle, name = ok, label = "OK" } ]
                }
                """;
        BedrockFormSpec form = new MenuSpecLoader().parse(hocon).bedrock().orElseThrow();
        assertThat(form.widgets())
                .as("a widget with an unknown type is skipped, the rest of the form parses")
                .containsExactly(new BedrockWidget.Toggle("ok", "OK", false));
    }
}
