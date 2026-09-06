package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Locale;

import com.uxplima.uxmessentials.custommenus.adapter.convert.GuiPlusConverter;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Pure coverage of the GUIPlus → uxmEssentials converter mapping. Each test feeds a GUIPlus GUI YAML fragment, converts
 * it, and re-parses the emitted HOCON so the assertions read the converted structure rather than a brittle string, the
 * top-level title / rows / open-permission mapping, the scenes-first selection, the item fields, the click-event map
 * with its {@code clickType} → gesture grouping, the condition map with its {@code inverted} → {@code !} negation, and
 * the fail-soft skips (multi-scene, unsupported event/condition types, {@code money-set}, {@code %input%}).
 */
class GuiPlusConverterTest {

    /** A representative GUIPlus GUI: a slot-keyed item with a LEFT message, gesture-less console+close, and a perm gate. */
    private static final String MENU = """
            id: shop_menu
            type: chest
            rows: 3
            title: '&aShop'
            commandAlias: shop
            permission: shop.open
            scenes:
              '0':
                delay: 0
                items:
                  '1':
                    slot: 11
                    item: DIAMOND
                    item-name: '&bDiamond'
                    item-lore:
                      - '&7Line one'
                    click-events:
                      message:
                        clickType: LEFT
                        message: hi
                      console_command:
                        commands:
                          - 'say x'
                      close-inventory: {}
                    conditions:
                      has-permission:
                        permission: shop.use
            """;

    private final GuiPlusConverter converter = new GuiPlusConverter();

    @Test
    void mapsTitleRowsOpenPermissionAndTheItemFields() throws ConfigurateException {
        ConfigurationNode out = convert(MENU);

        assertThat(out.node("title").getString()).isEqualTo("&aShop");
        assertThat(out.node("rows").getInt()).isEqualTo(3);
        assertThat(out.node("open-requirement").getList(String.class)).containsExactly("perm:shop.open");
        assertThat(out.node("items", "1", "material").getString()).isEqualTo("DIAMOND");
        assertThat(out.node("items", "1", "name").getString()).isEqualTo("&bDiamond");
        assertThat(out.node("items", "1", "lore").getList(String.class)).containsExactly("&7Line one");
        assertThat(out.node("items", "1", "slots").getList(String.class)).containsExactly("11");
    }

    @Test
    void groupsClickEventsByClickTypeOntoGestures() throws ConfigurateException {
        ConfigurationNode out = convert(MENU);

        assertThat(out.node("items", "1", "click", "left", "actions").getList(String.class))
                .containsExactly("message:hi");
        assertThat(out.node("items", "1", "click", "any", "actions").getList(String.class))
                .containsExactly("console:say x", "close");
    }

    @Test
    void mapsAHasPermissionConditionOntoTheItemView() throws ConfigurateException {
        ConfigurationNode out = convert(MENU);

        assertThat(out.node("items", "1", "view", "requirements").getList(String.class))
                .containsExactly("perm:shop.use");
    }

    @Test
    void translatesTheCommonClickEventTypes() throws ConfigurateException {
        ConfigurationNode out = convert("""
                type: chest
                rows: 1
                title: t
                scenes:
                  '0':
                    items:
                      a:
                        slot: 0
                        item: STONE
                        click-events:
                          money-give:
                            amount: 100
                          sound-click-event:
                            sound: BLOCK_NOTE_BLOCK_PLING
                            volume: 1
                            pitch: 2
                          teleport:
                            location: 'world,1,64,2,0,0'
                          server-click-event:
                            server: lobby
                          back: {}
                """);

        assertThat(out.node("items", "a", "click", "any", "actions").getList(String.class))
                .containsExactly(
                        "give-points:100",
                        "sound:BLOCK_NOTE_BLOCK_PLING 1 2",
                        "teleport:world 1 64 2 0 0",
                        "connect:lobby",
                        "back");
    }

    @Test
    void routesEachClickTypeOntoItsOwnGesture() throws ConfigurateException {
        ConfigurationNode out = convert("""
                type: chest
                rows: 1
                title: t
                scenes:
                  '0':
                    items:
                      a:
                        slot: 0
                        item: STONE
                        click-events:
                          message:
                            clickType: RIGHT
                            message: right
                          console_command:
                            clickType: SHIFT_LEFT
                            commands: [ 'say sl' ]
                          back:
                            clickType: MIDDLE
                """);

        assertThat(out.node("items", "a", "click", "right", "actions").getList(String.class))
                .containsExactly("message:right");
        assertThat(out.node("items", "a", "click", "shift_left", "actions").getList(String.class))
                .containsExactly("console:say sl");
        assertThat(out.node("items", "a", "click", "middle", "actions").getList(String.class))
                .containsExactly("back");
    }

    @Test
    void translatesTheConditionMapWithInversionAndOperators() throws ConfigurateException {
        ConfigurationNode out = convert("""
                type: chest
                rows: 1
                title: t
                scenes:
                  '0':
                    items:
                      a:
                        slot: 0
                        item: STONE
                        conditions:
                          has-permission:
                            permission: vip
                            inverted: true
                          conditional-placeholder:
                            conditional_condition: '%rank% (=) gold'
                          cooldown:
                            id: daily
                            cooldown: 5000
                          level-required:
                            level: 10
                """);

        assertThat(out.node("items", "a", "view", "requirements").getList(String.class))
                .containsExactly("!perm:vip", "expr:%rank% == gold", "cooldown:daily", "has-level:10");
    }

    @Test
    void mapsAConditionFailMessageOntoTheViewDeny() throws ConfigurateException {
        ConfigurationNode out = convert("""
                type: chest
                rows: 1
                title: t
                scenes:
                  '0':
                    items:
                      a:
                        slot: 0
                        item: STONE
                        conditionFailMessage: '&cNope'
                        conditions:
                          has-money:
                            required-balance: 50
                """);

        assertThat(out.node("items", "a", "view", "requirements").getList(String.class))
                .containsExactly("has-money:50");
        assertThat(out.node("items", "a", "view", "deny").getList(String.class)).containsExactly("message:&cNope");
    }

    @Test
    void convertsOnlyTheFirstSceneWhenAMenuHasSeveral() {
        GuiPlusConverter.ConversionResult result = converter.convert("""
                type: chest
                rows: 1
                title: t
                scenes:
                  '0':
                    items:
                      a:
                        slot: 0
                        item: STONE
                  '1':
                    items:
                      b:
                        slot: 1
                        item: DIRT
                """);

        assertThat(hocon(result).node("items").childrenMap()).containsOnlyKeys("a");
        assertThat(result.warnings()).anyMatch(w -> w.contains("scenes"));
    }

    @Test
    void skipsUnsupportedClickEventTypesWithAWarning() {
        GuiPlusConverter.ConversionResult result = converter.convert("""
                type: chest
                rows: 1
                title: t
                scenes:
                  '0':
                    items:
                      a:
                        slot: 0
                        item: STONE
                        click-events:
                          next-scene-click: {}
                          take-items:
                            items: 'serialized'
                          money-set:
                            amount: 50
                """);

        assertThat(hocon(result).node("items", "a", "click").virtual()).isTrue();
        assertThat(result.warnings()).anyMatch(w -> w.contains("next-scene-click"));
        assertThat(result.warnings()).anyMatch(w -> w.contains("money-set"));
    }

    @Test
    void warnsAboutTheInputPlaceholderButLeavesItVerbatim() throws ConfigurateException {
        GuiPlusConverter.ConversionResult result = converter.convert("""
                type: chest
                rows: 1
                title: t
                scenes:
                  '0':
                    items:
                      a:
                        slot: 0
                        item: STONE
                        click-events:
                          message:
                            message: 'hello %input%'
                """);

        assertThat(load(result.hocon())
                        .node("items", "a", "click", "any", "actions")
                        .getList(String.class))
                .containsExactly("message:hello %input%");
        assertThat(result.warnings()).anyMatch(w -> w.toLowerCase(Locale.ROOT).contains("%input%"));
    }

    @Test
    void mapsANonChestTypeOntoInventoryType() throws ConfigurateException {
        ConfigurationNode out = convert("type: hopper\ntitle: t\nrows: 1\nscenes:\n  '0':\n    items: {}\n");

        assertThat(out.node("inventory-type").getString()).isEqualTo("hopper");
    }

    @Test
    void clampsAnOversizedRowCountWithAWarning() throws ConfigurateException {
        GuiPlusConverter.ConversionResult result = converter.convert("type: chest\ntitle: Big\nrows: 9\n");

        assertThat(load(result.hocon()).node("rows").getInt()).isEqualTo(6);
        assertThat(result.warnings()).anyMatch(w -> w.contains("9"));
    }

    @Test
    void warnsOnceAboutLegacyColourCodes() {
        GuiPlusConverter.ConversionResult result = converter.convert(MENU);

        assertThat(result.warnings()).anyMatch(w -> w.contains("legacy"));
    }

    private ConfigurationNode convert(String yaml) throws ConfigurateException {
        return load(converter.convert(yaml).hocon());
    }

    private ConfigurationNode hocon(GuiPlusConverter.ConversionResult result) {
        try {
            return load(result.hocon());
        } catch (ConfigurateException failure) {
            throw new AssertionError("emitted HOCON did not parse", failure);
        }
    }

    private static ConfigurationNode load(String hocon) throws ConfigurateException {
        return HoconConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(hocon)))
                .build()
                .load();
    }
}
