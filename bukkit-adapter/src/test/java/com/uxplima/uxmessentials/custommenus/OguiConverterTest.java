package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import java.util.Locale;

import com.google.common.base.Splitter;
import com.uxplima.uxmessentials.custommenus.adapter.convert.OguiConverter;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Pure coverage of the OGUI → uxmEssentials converter mapping. Each test feeds an OGUI GUI YAML fragment, converts it,
 * and re-parses the emitted HOCON so the assertions read the converted structure rather than a brittle string, the
 * single-root GUI, the title/rows/size mapping, the two action styles (the legacy {@code commands} list and the typed
 * {@code actions} list), the {@code {player}} → {@code %player%} rewrite, the {@code close}/type navigation, the typed
 * action-type map, the condition-type map (WORLD single/whitelist/blacklist, PERMISSION, PLACEHOLDER), and the SCRIPT
 * / unknown-type fail-soft.
 */
class OguiConverterTest {

    /** A representative OGUI GUI: a slot-keyed item with legacy commands + a WORLD gate, and a typed-action item. */
    private static final String MENU = """
            shop:
              title: '&cShop'
              rows: 4
              commands: [ shop, menu ]
              items:
                '10':
                  material: GRASS_BLOCK
                  name: '&aOverworld'
                  lore:
                    - '&7Line one'
                    - '&7Line two'
                  commands:
                    - 'give {player} diamond 1'
                  close: true
                  conditions:
                    - type: WORLD
                      worlds:
                        - world
                        - lobby
                gift:
                  slot: 12
                  material: CHEST
                  name: '&bGift'
                  actions:
                    - type: PLAYER_MESSAGE
                      message: hi
                    - type: OPEN_GUI
                      gui: other
            """;

    private final OguiConverter converter = new OguiConverter();

    @Test
    void mapsTitleRowsAndTheSlotKeyedItemFields() throws ConfigurateException {
        ConfigurationNode out = convert(MENU);

        assertThat(out.node("title").getString()).isEqualTo("&cShop");
        assertThat(out.node("rows").getInt()).isEqualTo(4);
        assertThat(out.node("items", "10", "material").getString()).isEqualTo("GRASS_BLOCK");
        assertThat(out.node("items", "10", "name").getString()).isEqualTo("&aOverworld");
        assertThat(out.node("items", "10", "lore").getList(String.class)).containsExactly("&7Line one", "&7Line two");
        assertThat(out.node("items", "10", "slots").getList(String.class)).containsExactly("10");
    }

    @Test
    void mapsLegacyCommandsAndCloseOntoTheAnyGestureRewritingThePlayerToken() throws ConfigurateException {
        ConfigurationNode out = convert(MENU);

        assertThat(out.node("items", "10", "click", "any", "actions").getList(String.class))
                .containsExactly("console:give %player% diamond 1", "close");
    }

    @Test
    void mapsTypedActionsOntoTheAnyGesture() throws ConfigurateException {
        ConfigurationNode out = convert(MENU);

        assertThat(out.node("items", "gift", "slots").getList(String.class)).containsExactly("12");
        assertThat(out.node("items", "gift", "click", "any", "actions").getList(String.class))
                .containsExactly("message:hi", "open:other");
    }

    @Test
    void mapsAMultiWorldWhitelistOntoAnOrGroupView() throws ConfigurateException {
        ConfigurationNode out = convert(MENU);

        assertThat(out.node("items", "10", "view", "requirements").getList(String.class))
                .containsExactly("world:world", "world:lobby");
        assertThat(out.node("items", "10", "view", "minimum").getInt()).isEqualTo(1);
    }

    @Test
    void mapsASingleWorldShorthandAndABlacklistAndAPermission() throws ConfigurateException {
        ConfigurationNode out = convert("""
                shop:
                  title: t
                  rows: 1
                  items:
                    a:
                      slot: 0
                      material: STONE
                      conditions:
                        - type: WORLD
                          world: world_nether
                        - type: PERMISSION
                          permission: shop.use
                    b:
                      slot: 1
                      material: TNT
                      conditions:
                        - type: WORLD
                          blacklist: true
                          worlds: [ spawn, lobby ]
                """);

        assertThat(out.node("items", "a", "view", "requirements").getList(String.class))
                .containsExactly("world:world_nether", "perm:shop.use");
        assertThat(out.node("items", "b", "view", "requirements").getList(String.class))
                .containsExactly("!world:spawn", "!world:lobby");
    }

    @Test
    void mapsThePlaceholderConditionOperators() throws ConfigurateException {
        ConfigurationNode out = convert("""
                shop:
                  title: t
                  rows: 1
                  items:
                    a:
                      slot: 0
                      material: STONE
                      conditions:
                        - type: PLACEHOLDER
                          placeholder: '%vault_eco_balance%'
                          operator: '>='
                          value: '1000'
                        - type: PLACEHOLDER_CONTAINS
                          placeholder: '%player_name%'
                          value: a
                        - type: EXPRESSION
                          expression: '%player_level% >= 10'
                """);

        assertThat(out.node("items", "a", "view", "requirements").getList(String.class))
                .containsExactly(
                        "expr:%vault_eco_balance% >= 1000", "contains:%player_name% a", "expr:%player_level% >= 10");
    }

    @Test
    void translatesEveryTypedActionType() throws ConfigurateException {
        ConfigurationNode out = convert(
                itemWithActions("""
                - type: BROADCAST
                  message: 'hi {player}'
                """, """
                - type: ACTION_BAR
                  message: bar
                """, """
                - type: CONSOLE_COMMAND
                  command: 'say {player}'
                """, """
                - type: PLAYER_COMMAND
                  command: home
                """, """
                - type: OPEN_INVENTORY
                  inventory: other
                """, "- type: CLOSE_INVENTORY", "- type: BACK", """
                - type: SERVER_CONNECT
                  server: lobby
                """, """
                - type: SOUND
                  sound: BLOCK_NOTE_BLOCK_PLING
                """, """
                - type: TELEPORT
                  world: world
                  x: 1
                  y: 64
                  z: 2
                """));

        assertThat(out.node("items", "x", "click", "any", "actions").getList(String.class))
                .containsExactly(
                        "broadcast:hi %player%",
                        "action-bar:bar",
                        "console:say %player%",
                        "command:home",
                        "open:other",
                        "close",
                        "back",
                        "connect:lobby",
                        "sound:BLOCK_NOTE_BLOCK_PLING",
                        "teleport:world 1 64 2 0 0");
    }

    @Test
    void fallsBackToAConsoleCommandForAnUnknownActionWithACommandField() {
        OguiConverter.ConversionResult result = converter.convert(itemWithActions("""
                - type: MYSTERY
                  command: 'say boom'
                """));

        assertThat(hoconList(result, "items", "x", "click", "any", "actions")).containsExactly("console:say boom");
        assertThat(result.warnings()).anyMatch(w -> w.contains("MYSTERY"));
    }

    @Test
    void skipsAScriptActionWithAWarning() {
        OguiConverter.ConversionResult result = converter.convert(itemWithActions("""
                - type: SCRIPT
                  script: 'player.sendMessage("x")'
                """));

        assertThat(result.warnings()).anyMatch(w -> w.toLowerCase(Locale.ROOT).contains("script"));
    }

    @Test
    void skipsAScriptConditionWithAWarning() {
        OguiConverter.ConversionResult result = converter.convert("""
                shop:
                  title: t
                  rows: 1
                  items:
                    a:
                      slot: 0
                      material: STONE
                      conditions:
                        - type: SCRIPT
                          script: 'player.isOp()'
                """);

        assertThat(hoconList(result, "items", "a", "view", "requirements")).isEmpty();
        assertThat(result.warnings()).anyMatch(w -> w.toLowerCase(Locale.ROOT).contains("script"));
    }

    @Test
    void mapsSlotRangesFromAListOntoSlots() throws ConfigurateException {
        ConfigurationNode out = convert("""
                shop:
                  title: t
                  rows: 3
                  items:
                    border:
                      slots: [ 0-8, 18-26 ]
                      material: GRAY_STAINED_GLASS_PANE
                      name: ' '
                """);

        assertThat(out.node("items", "border", "slots").getList(String.class)).containsExactly("0-8", "18-26");
    }

    @Test
    void mapsBackAndMainMenuButtonTypesOntoNavigationActions() throws ConfigurateException {
        ConfigurationNode out = convert("""
                shop:
                  title: t
                  rows: 3
                  main_menu: hub
                  items:
                    back:
                      type: BACK
                      slot: 0
                      material: ARROW
                    home:
                      type: MAIN_MENU
                      slot: 1
                      material: COMPASS
                """);

        assertThat(out.node("items", "back", "click", "any", "actions").getList(String.class))
                .containsExactly("back");
        assertThat(out.node("items", "home", "click", "any", "actions").getList(String.class))
                .containsExactly("open:hub");
    }

    @Test
    void clampsAnOversizedRowCountWithAWarning() throws ConfigurateException {
        OguiConverter.ConversionResult result = converter.convert("shop:\n  title: Big\n  rows: 9\n");

        assertThat(load(result.hocon()).node("rows").getInt()).isEqualTo(6);
        assertThat(result.warnings()).anyMatch(w -> w.contains("9"));
    }

    @Test
    void derivesRowsFromSizeWhenNoRowsIsGiven() throws ConfigurateException {
        ConfigurationNode out = convert("shop:\n  title: Sized\n  size: 27\n");

        assertThat(out.node("rows").getInt()).isEqualTo(3);
    }

    @Test
    void convertsOnlyTheFirstGuiWhenAFileHasSeveral() throws ConfigurateException {
        OguiConverter.ConversionResult result = converter.convert("""
                one:
                  title: First
                  rows: 1
                  items:
                    a:
                      slot: 0
                      material: STONE
                two:
                  title: Second
                  rows: 1
                """);

        assertThat(load(result.hocon()).node("title").getString()).isEqualTo("First");
        assertThat(result.warnings()).anyMatch(w -> w.contains("2 GUIs"));
    }

    @Test
    void warnsOnceAboutLegacyColourCodes() {
        OguiConverter.ConversionResult result = converter.convert(MENU);

        assertThat(result.warnings()).anyMatch(w -> w.contains("legacy"));
    }

    private ConfigurationNode convert(String yaml) throws ConfigurateException {
        return load(converter.convert(yaml).hocon());
    }

    private List<String> hoconList(OguiConverter.ConversionResult result, Object... path) {
        try {
            return load(result.hocon()).node(path).getList(String.class);
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

    /** A one-item GUI whose {@code x} item carries the given typed-action YAML blocks as its {@code actions} list. */
    private static String itemWithActions(String... actionBlocks) {
        StringBuilder yaml = new StringBuilder("""
                shop:
                  title: t
                  rows: 1
                  items:
                    x:
                      slot: 0
                      material: STONE
                      actions:
                """);
        for (String block : actionBlocks) {
            for (String line : Splitter.on('\n').split(block.strip())) {
                yaml.append("          ").append(line).append('\n');
            }
        }
        return yaml.toString();
    }
}
