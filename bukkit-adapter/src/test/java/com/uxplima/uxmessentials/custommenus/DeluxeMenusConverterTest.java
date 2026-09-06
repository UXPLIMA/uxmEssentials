package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;

import com.uxplima.uxmessentials.custommenus.adapter.convert.DeluxeMenusConverter;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Pure coverage of the DeluxeMenus → uxmEssentials converter mapping. Each test feeds a DeluxeMenus YAML fragment,
 * converts it, and re-parses the emitted HOCON so the assertions read the converted structure rather than a brittle
 * string. The title/size/items/click/requirement mappings, the click-tag map, the requirement-type map, the
 * JavaScript skip, the unknown-tag fallback, and the size-to-rows clamp.
 */
class DeluxeMenusConverterTest {

    private static final String MENU = """
            menu_title: 'Test Menu'
            size: 9
            open_command: testmenu
            open_requirement:
              requirements:
                perm:
                  type: has permission
                  permission: test.open
            items:
              stone:
                material: STONE
                slot: 0
                display_name: '&aStone'
                lore:
                  - '&7line one'
                  - '&7line two'
                left_click_commands:
                  - '[message] hi'
                  - '[console] say x'
                  - '[openguimenu] other'
                view_requirement:
                  requirements:
                    vip:
                      type: has permission
                      permission: menu.vip
            """;

    private final DeluxeMenusConverter converter = new DeluxeMenusConverter();

    @Test
    void mapsTitleSizeAndTheCoreItemFields() throws ConfigurateException {
        ConfigurationNode out = convert(MENU);

        assertThat(out.node("title").getString()).isEqualTo("Test Menu");
        assertThat(out.node("rows").getInt()).isEqualTo(1);
        assertThat(out.node("items", "stone", "material").getString()).isEqualTo("STONE");
        assertThat(out.node("items", "stone", "name").getString()).isEqualTo("&aStone");
        assertThat(out.node("items", "stone", "slot").getInt()).isZero();
        assertThat(out.node("items", "stone", "lore").getList(String.class))
                .containsExactly("&7line one", "&7line two");
    }

    @Test
    void mapsClickCommandsOntoTheLeftGesture() throws ConfigurateException {
        ConfigurationNode out = convert(MENU);

        assertThat(out.node("items", "stone", "click", "left").getList(String.class))
                .containsExactly("message:hi", "console:say x", "open:other");
    }

    @Test
    void mapsTheOpenAndViewPermissionRequirements() throws ConfigurateException {
        ConfigurationNode out = convert(MENU);

        assertThat(out.node("open-requirement").getList(String.class)).containsExactly("perm:test.open");
        assertThat(out.node("items", "stone", "view", "requirements").getList(String.class))
                .containsExactly("perm:menu.vip");
    }

    @Test
    void translatesEveryKnownClickTag() throws ConfigurateException {
        ConfigurationNode out = convert(itemWithCommands(
                "'[message] hi'",
                "'[broadcast] all'",
                "'[console] say x'",
                "'[player] home'",
                "'[commandevent] warp spawn'",
                "'[openguimenu] shop'",
                "'[close]'",
                "'[refresh]'",
                "'[sound] BLOCK_NOTE'",
                "'[connect] lobby'",
                "'[chat] hello'",
                "'[givemoney] 100'",
                "'[takemoney] 50'"));

        assertThat(out.node("items", "x", "click", "left").getList(String.class))
                .containsExactly(
                        "message:hi",
                        "broadcast:all",
                        "console:say x",
                        "command:home",
                        "commandevent:warp spawn",
                        "open:shop",
                        "close",
                        "refresh",
                        "sound:BLOCK_NOTE",
                        "connect:lobby",
                        "chat:hello",
                        "give-points:100",
                        "take-points:50");
    }

    @Test
    void treatsARawCommandWithNoTagAsAPlayerCommand() throws ConfigurateException {
        ConfigurationNode out = convert(itemWithCommands("'spawn'"));

        assertThat(out.node("items", "x", "click", "left").getList(String.class))
                .containsExactly("command:spawn");
    }

    @Test
    void fallsBackToAConsoleCommandForAnUnknownTagAndWarns() {
        DeluxeMenusConverter.ConversionResult result = converter.convert(itemWithCommands("'[weirdtag] boom'"));

        assertThat(hoconList(result, "items", "x", "click", "left")).containsExactly("console:boom");
        assertThat(result.warnings()).anyMatch(w -> w.contains("[weirdtag]"));
    }

    @Test
    void mapsTheCoreRequirementTypes() throws ConfigurateException {
        ConfigurationNode out = convert(itemWithRequirements(
                "money: { type: has money, amount: 100 }",
                "item: { type: has item, material: DIAMOND, amount: 2 }",
                "contains: { type: string contains, input: '%name%', output: 'a' }",
                "num: { type: '>', input: '%level%', output: '5' }",
                "no_perm: { type: '!has permission', permission: menu.deny }"));

        assertThat(out.node("items", "x", "view", "requirements").getList(String.class))
                .containsExactly(
                        "has-money:100",
                        "has-item:DIAMOND 2",
                        "contains:%name% a",
                        "expr:%level% > 5",
                        "!perm:menu.deny");
    }

    @Test
    void skipsAJavaScriptRequirementWithAWarning() {
        DeluxeMenusConverter.ConversionResult result =
                converter.convert(itemWithRequirements("js: { type: javascript, expression: '1 == 1' }"));

        assertThat(hoconList(result, "items", "x", "view", "requirements")).isEmpty();
        assertThat(result.warnings())
                .anyMatch(w -> w.toLowerCase(java.util.Locale.ROOT).contains("javascript"));
    }

    @Test
    void skipsAnUnknownRequirementTypeWithAWarning() {
        DeluxeMenusConverter.ConversionResult result =
                converter.convert(itemWithRequirements("mystery: { type: some future thing }"));

        assertThat(hoconList(result, "items", "x", "view", "requirements")).isEmpty();
        assertThat(result.warnings()).anyMatch(w -> w.contains("some future thing"));
    }

    @Test
    void clampsAnOversizedMenuToSixRowsWithAWarning() throws ConfigurateException {
        DeluxeMenusConverter.ConversionResult result = converter.convert("menu_title: 'Big'\nsize: 108\n");

        assertThat(load(result.hocon()).node("rows").getInt()).isEqualTo(6);
        assertThat(result.warnings()).anyMatch(w -> w.contains("108"));
    }

    @Test
    void defaultsAnAbsentSizeToSixRows() throws ConfigurateException {
        ConfigurationNode out = convert("menu_title: 'No Size'\n");

        assertThat(out.node("rows").getInt()).isEqualTo(6);
    }

    @Test
    void mapsAPerGestureRequirementIntoTheClickBlockWithItsDeny() throws ConfigurateException {
        ConfigurationNode out = convert("""
                items:
                  buy:
                    material: GOLD_BLOCK
                    slot: 0
                    left_click_commands:
                      - '[console] give %player_name% GOLD_BLOCK 1'
                    left_click_requirement:
                      requirements:
                        money:
                          type: has money
                          amount: 100
                      deny_commands:
                        - '[message] &cNot enough money'
                """);

        ConfigurationNode left = out.node("items", "buy", "click", "left");
        assertThat(left.node("actions").getList(String.class))
                .containsExactly("console:give %player_name% GOLD_BLOCK 1");
        assertThat(left.node("requirements").getList(String.class)).containsExactly("has-money:100");
        assertThat(left.node("deny").getList(String.class)).containsExactly("message:&cNot enough money");
    }

    @Test
    void warnsOnceAboutLegacyColourCodes() {
        DeluxeMenusConverter.ConversionResult result = converter.convert(MENU);

        assertThat(result.warnings()).anyMatch(w -> w.contains("legacy"));
    }

    private ConfigurationNode convert(String yaml) throws ConfigurateException {
        return load(converter.convert(yaml).hocon());
    }

    private List<String> hoconList(DeluxeMenusConverter.ConversionResult result, Object... path) {
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

    /** A one-item menu whose {@code x} item carries the given left-click command entries. */
    private static String itemWithCommands(String... commands) {
        String header = """
                items:
                  x:
                    material: STONE
                    slot: 0
                    left_click_commands:
                """;
        StringBuilder yaml = new StringBuilder(header);
        for (String command : commands) {
            yaml.append("      - ").append(command).append('\n');
        }
        return yaml.toString();
    }

    /** A one-item menu whose {@code x} item carries the given view requirements (each a flow-style YAML map). */
    private static String itemWithRequirements(String... requirements) {
        String header = """
                items:
                  x:
                    material: STONE
                    slot: 0
                    view_requirement:
                      requirements:
                """;
        StringBuilder yaml = new StringBuilder(header);
        for (String requirement : requirements) {
            yaml.append("        ").append(requirement).append('\n');
        }
        return yaml.toString();
    }
}
