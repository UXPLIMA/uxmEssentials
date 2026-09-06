package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;

import com.google.common.base.Splitter;
import com.uxplima.uxmessentials.custommenus.adapter.convert.ZMenuConverter;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Pure coverage of the zMenu → uxmEssentials converter mapping. Each test feeds a zMenu inventory YAML fragment,
 * converts it, and re-parses the emitted HOCON so the assertions read the converted structure rather than a brittle
 * string. The name/size/items/action/requirement mappings, the typed-action map (with multi-value expansion), the
 * placeholder requirement operator map, the {@code math} value wrapping, the pattern-backed skip, the JavaScript
 * skip, and the size-to-rows clamp.
 */
class ZMenuConverterTest {

    /** A representative menu: an inline item with actions, plus a pattern-backed item that must be skipped. */
    private static final String MENU = """
            name: '&8Cookies'
            size: 36
            items:
              cookie:
                slot: 13
                item:
                  material: COOKIE
                  name: '#77ff77Cookies'
                  lore:
                    - '&fLine one'
                    - '&7Line two'
                actions:
                  - type: message
                    messages:
                      - hi
                  - type: console
                    commands:
                      - say x
                  - type: inventory
                    inventory: other
                  - type: data
                    action: ADD
                    key: k
                    value: 1
              grandma:
                pattern:
                  file-name: pattern_cookies
                  slot: 20
                  material: CAKE
            """;

    private final ZMenuConverter converter = new ZMenuConverter();

    @Test
    void mapsNameSizeAndTheInlineItemFields() throws ConfigurateException {
        ConfigurationNode out = convert(MENU);

        assertThat(out.node("title").getString()).isEqualTo("&8Cookies");
        assertThat(out.node("rows").getInt()).isEqualTo(4);
        assertThat(out.node("items", "cookie", "material").getString()).isEqualTo("COOKIE");
        assertThat(out.node("items", "cookie", "name").getString()).isEqualTo("#77ff77Cookies");
        assertThat(out.node("items", "cookie", "lore").getList(String.class))
                .containsExactly("&fLine one", "&7Line two");
        assertThat(out.node("items", "cookie", "slots").getList(String.class)).containsExactly("13");
    }

    @Test
    void mapsTheDefaultActionsOntoTheAnyGesture() throws ConfigurateException {
        ConfigurationNode out = convert(MENU);

        assertThat(out.node("items", "cookie", "click", "any", "actions").getList(String.class))
                .containsExactly("message:hi", "console:say x", "open:other", "data-add:k 1");
    }

    @Test
    void skipsAPatternBackedItemWithAWarning() throws ConfigurateException {
        ZMenuConverter.ConversionResult result = converter.convert(MENU);

        assertThat(load(result.hocon()).node("items").childrenMap()).containsOnlyKeys("cookie");
        assertThat(result.warnings()).anyMatch(w -> w.contains("grandma") && w.contains("pattern"));
    }

    @Test
    void translatesEveryTypedActionAndExpandsMultiValueActions() throws ConfigurateException {
        ConfigurationNode out =
                convert(itemWithActions("""
                - type: message
                  messages: [a, b]
                """, """
                - type: broadcast
                  messages: [c]
                """, """
                - type: player
                  commands: [home]
                """, """
                - type: data
                  action: SUBTRACT
                  key: m
                  value: 3
                """, """
                - type: data
                  action: SET
                  key: s
                  value: 5
                """, "- type: refresh", """
                - type: connect
                  server: lobby
                """, """
                - type: sound
                  sound: BLOCK_NOTE_BLOCK_PLING
                """, "- type: close"));

        assertThat(out.node("items", "x", "click", "any", "actions").getList(String.class))
                .containsExactly(
                        "message:a",
                        "message:b",
                        "broadcast:c",
                        "command:home",
                        "data-sub:m 3",
                        "data-set:s 5",
                        "refresh",
                        "connect:lobby",
                        "sound:BLOCK_NOTE_BLOCK_PLING",
                        "close");
    }

    @Test
    void wrapsAMathDataValueSoOurEvaluatorReadsIt() throws ConfigurateException {
        ConfigurationNode out = convert(itemWithActions("""
                - type: data
                  action: ADD
                  key: cookie
                  value: '1+2'
                  math: true
                """));

        assertThat(out.node("items", "x", "click", "any", "actions").getList(String.class))
                .containsExactly("data-add:cookie {math:1+2}");
    }

    @Test
    void fallsBackToAConsoleCommandForAnUnknownActionWithACommandField() {
        ZMenuConverter.ConversionResult result = converter.convert(itemWithActions("""
                - type: mystery
                  commands: [say boom]
                """));

        assertThat(hoconList(result, "items", "x", "click", "any", "actions")).containsExactly("console:say boom");
        assertThat(result.warnings()).anyMatch(w -> w.contains("mystery"));
    }

    @Test
    void skipsAnUnknownActionWithNoCommandField() {
        ZMenuConverter.ConversionResult result = converter.convert(itemWithActions("- type: toast"));

        assertThat(result.warnings()).anyMatch(w -> w.contains("toast"));
    }

    @Test
    void mapsAClickRequirementOntoItsRestrictedGesture() throws ConfigurateException {
        ConfigurationNode out = convert("""
                items:
                  x:
                    slot: 0
                    item:
                      material: STONE
                    click-requirement:
                      gate:
                        clicks: [LEFT]
                        requirements:
                          - type: placeholder
                            placeholder: '%level%'
                            value: 5
                            action: SUPERIOR_OR_EQUAL
                        success:
                          - type: message
                            messages: [ok]
                        deny:
                          - type: message
                            messages: [nope]
                """);

        ConfigurationNode left = out.node("items", "x", "click", "left");
        assertThat(left.node("actions").getList(String.class)).containsExactly("message:ok");
        assertThat(left.node("requirements").getList(String.class)).containsExactly("expr:%level% >= 5");
        assertThat(left.node("deny").getList(String.class)).containsExactly("message:nope");
    }

    @Test
    void mapsThePlaceholderOperatorsAndPermissionRequirements() throws ConfigurateException {
        ConfigurationNode out = convert("""
                items:
                  x:
                    slot: 0
                    item:
                      material: STONE
                    click-requirement:
                      gate:
                        clicks: [RIGHT]
                        requirements:
                          - type: placeholder
                            placeholder: '%a%'
                            value: 1
                            action: SUPERIOR
                          - type: placeholder
                            placeholder: '%b%'
                            value: 2
                            action: INFERIOR_OR_EQUAL
                          - type: permission
                            permission: menu.vip
                          - type: permission
                            permission: '!menu.banned'
                """);

        assertThat(out.node("items", "x", "click", "right", "requirements").getList(String.class))
                .containsExactly("expr:%a% > 1", "expr:%b% <= 2", "perm:menu.vip", "!perm:menu.banned");
    }

    @Test
    void skipsAJavaScriptRequirementWithAWarning() {
        ZMenuConverter.ConversionResult result = converter.convert("""
                items:
                  x:
                    slot: 0
                    item:
                      material: STONE
                    click-requirement:
                      gate:
                        clicks: [LEFT]
                        requirements:
                          - type: javascript
                            expression: '1 == 1'
                """);

        assertThat(hoconList(result, "items", "x", "click", "left", "requirements"))
                .isEmpty();
        assertThat(result.warnings())
                .anyMatch(w -> w.toLowerCase(java.util.Locale.ROOT).contains("javascript"));
    }

    @Test
    void clampsAnOversizedMenuToSixRowsWithAWarning() throws ConfigurateException {
        ZMenuConverter.ConversionResult result = converter.convert("name: 'Big'\nsize: 108\n");

        assertThat(load(result.hocon()).node("rows").getInt()).isEqualTo(6);
        assertThat(result.warnings()).anyMatch(w -> w.contains("108"));
    }

    @Test
    void defaultsAnAbsentSizeToSixRows() throws ConfigurateException {
        ConfigurationNode out = convert("name: 'No Size'\n");

        assertThat(out.node("rows").getInt()).isEqualTo(6);
    }

    @Test
    void warnsOnceAboutLegacyColourCodes() {
        ZMenuConverter.ConversionResult result = converter.convert(MENU);

        assertThat(result.warnings()).anyMatch(w -> w.contains("legacy"));
    }

    private ConfigurationNode convert(String yaml) throws ConfigurateException {
        return load(converter.convert(yaml).hocon());
    }

    private List<String> hoconList(ZMenuConverter.ConversionResult result, Object... path) {
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

    /** A one-item menu whose {@code x} item carries the given typed-action YAML blocks as its {@code actions} list. */
    private static String itemWithActions(String... actionBlocks) {
        StringBuilder yaml = new StringBuilder("""
                items:
                  x:
                    slot: 0
                    item:
                      material: STONE
                    actions:
                """);
        for (String block : actionBlocks) {
            for (String line : Splitter.on('\n').split(block.strip())) {
                yaml.append("      ").append(line).append('\n');
            }
        }
        return yaml.toString();
    }
}
