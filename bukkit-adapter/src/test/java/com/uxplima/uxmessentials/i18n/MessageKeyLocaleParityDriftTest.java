package com.uxplima.uxmessentials.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.communication.application.CommunicationMessageKey;
import com.uxplima.uxmessentials.customcommands.application.CustomCommandsMessageKey;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.holograms.application.HologramsMessageKey;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.invrollback.application.InvrollbackMessageKey;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.messaging.application.MessagingMessageKey;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
import com.uxplima.uxmessentials.poses.application.PosesMessageKey;
import com.uxplima.uxmessentials.presence.application.PresenceMessageKey;
import com.uxplima.uxmessentials.ranks.application.RanksMessageKey;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKeyCatalog;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.skin.application.SkinMessageKey;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import com.uxplima.uxmessentials.survival.application.SurvivalMessageKey;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import com.uxplima.uxmessentials.vaults.application.VaultsMessageKey;
import com.uxplima.uxmessentials.villagers.application.VillagersMessageKey;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * The authoritative locale-parity matrix (docs/13-i18n §10.2, docs/05-testing §16).
 *
 * <p>Asserts bidirectionally that the {@link MessageKey} constant set equals the key set of every
 * bundled {@code messages_<lang>.conf}, with {@code messages_en.conf} as the authority: every enum
 * constant has an {@code en} entry, and every shipped locale carries exactly {@code en}'s key set, no
 * holes, no stale extras. Per-module key ownership is asserted by prefix so a context owns its key
 * namespace and a misfiled key fails loudly. The Gradle {@code localeParityCheck} gate runs the same
 * comparison as a fast pre-test fail; this is the comprehensive guard.
 */
class MessageKeyLocaleParityDriftTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_-]*)}");

    /** Each owning module's key enum and the prefixes it is allowed to declare (docs/13-i18n §6). */
    private static final Map<MessageKey[], List<String>> OWNERSHIP = ownershipTable();

    @Test
    void everyEnumConstantHasAnEnglishEntry() {
        Set<String> enumKeys = MessageKeyCatalog.allKeys();
        Set<String> en = CatalogKeys.read("en");
        assertThat(en)
                .as("messages_en.conf must carry exactly the MessageKey enum constant set")
                .isEqualTo(enumKeys);
    }

    @TestFactory
    Iterable<DynamicTest> everyShippedLocaleCarriesEnglishesExactKeySet() {
        Set<String> en = CatalogKeys.read("en");
        return CatalogKeys.shippedLanguages().stream()
                .filter(language -> !language.equals("en"))
                .map(language -> DynamicTest.dynamicTest("messages_" + language + ".conf == en key set", () -> {
                    Locale locale = Locale.forLanguageTag(language);
                    assertThat(CatalogKeys.read(language))
                            .as("%s must carry exactly en's key set (no holes, no stale extras)", locale)
                            .isEqualTo(en);
                }))
                .collect(Collectors.toList());
    }

    @Test
    void enKeysAreKebabCaseDotSeparated() {
        for (String key : CatalogKeys.read("en")) {
            assertThat(key)
                    .as("catalog key '%s' must be lower kebab-case, dot-separated", key)
                    .matches("[a-z0-9]+(?:[.-][a-z0-9]+)*");
        }
    }

    @TestFactory
    Iterable<DynamicTest> remainingAttemptsIsAPlaceholderRatherThanAnUnknownMiniMessageTag() {
        return CatalogKeys.shippedLanguages().stream()
                .map(language -> DynamicTest.dynamicTest("messages_" + language + ".conf remaining", () -> {
                    String value = CatalogKeys.value(language, "security.verify.failed-subtitle");
                    assertThat(value).contains("{remaining}").doesNotContain("<remaining>");
                }))
                .collect(Collectors.toList());
    }

    @TestFactory
    Iterable<DynamicTest> translationsPreserveEveryRuntimePlaceholder() {
        Map<String, Set<String>> english = placeholderSignature("en");
        return CatalogKeys.shippedLanguages().stream()
                .filter(language -> !language.equals("en"))
                .map(language -> DynamicTest.dynamicTest(
                        "messages_" + language + ".conf placeholders",
                        () -> assertThat(placeholderSignature(language))
                                .as("translations must neither drop nor invent runtime placeholders")
                                .isEqualTo(english)))
                .collect(Collectors.toList());
    }

    private static Map<String, Set<String>> placeholderSignature(String language) {
        return CatalogKeys.values(language).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> placeholdersIn(entry.getValue()),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));
    }

    private static Set<String> placeholdersIn(String value) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        Set<String> found = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    @TestFactory
    Iterable<DynamicTest> eachModuleOwnsItsKeyNamespaceByPrefix() {
        return OWNERSHIP.entrySet().stream()
                .map(entry -> DynamicTest.dynamicTest(ownerName(entry.getKey()), () -> {
                    List<String> allowedPrefixes = entry.getValue();
                    for (MessageKey key : entry.getKey()) {
                        assertThat(allowedPrefixes)
                                .as(
                                        "key '%s' must start with one of its module's prefixes %s",
                                        key.key(), allowedPrefixes)
                                .anyMatch(prefix ->
                                        key.key().equals(prefix) || key.key().startsWith(prefix + "."));
                    }
                }))
                .collect(Collectors.toList());
    }

    private static String ownerName(MessageKey[] block) {
        return block.length == 0 ? "empty" : block[0].getClass().getSimpleName();
    }

    private static Map<MessageKey[], List<String>> ownershipTable() {
        return Map.ofEntries(
                Map.entry(
                        SharedMessageKey.values(), List.of("command", "cooldown", "warmup", "common", "lang", "help")),
                Map.entry(TeleportMessageKey.values(), List.of("teleport", "tpa", "back", "rtp", "spawn")),
                Map.entry(HomesMessageKey.values(), List.of("home")),
                Map.entry(
                        EconomyMessageKey.values(),
                        List.of("wallet", "pay", "currency", "baltop", "eco", "loan", "bank")),
                Map.entry(WarpsMessageKey.values(), List.of("warp")),
                Map.entry(KitsMessageKey.values(), List.of("kit")),
                Map.entry(PlayerstateMessageKey.values(), List.of("playerstate")),
                Map.entry(
                        MessagingMessageKey.values(),
                        List.of("msg", "ignore", "socialspy", "mail", "helpop", "messaging")),
                Map.entry(PresenceMessageKey.values(), List.of("afk", "vanish", "presence", "list")),
                Map.entry(ModerationMessageKey.values(), List.of("moderation", "mute", "jail")),
                Map.entry(ItemworldMessageKey.values(), List.of("itemworld", "give", "time", "weather")),
                Map.entry(VaultsMessageKey.values(), List.of("vault", "vaults")),
                Map.entry(CommunicationMessageKey.values(), List.of("communication")),
                Map.entry(HologramsMessageKey.values(), List.of("hologram")),
                Map.entry(StaffMessageKey.values(), List.of("staff")),
                Map.entry(CustomMenusMessageKey.values(), List.of("menu")),
                Map.entry(CustomCommandsMessageKey.values(), List.of("customcommand")),
                Map.entry(PosesMessageKey.values(), List.of("poses")),
                Map.entry(SurvivalMessageKey.values(), List.of("survival")),
                Map.entry(RanksMessageKey.values(), List.of("ranks")),
                Map.entry(SecurityMessageKey.values(), List.of("security")),
                Map.entry(VillagersMessageKey.values(), List.of("villagers")),
                Map.entry(InvrollbackMessageKey.values(), List.of("invrollback")),
                Map.entry(SkinMessageKey.values(), List.of("skin")));
    }

    @Test
    void everyEnumKeyIsRegisteredInTheCatalogAggregate() {
        // The aggregate MessageKeyCatalog must enumerate every per-module enum, a context whose enum
        // is forgotten here would silently drop out of the parity matrix and the translatable wiring.
        Set<String> aggregate = MessageKeyCatalog.allKeys();
        for (MessageKey[] block : OWNERSHIP.keySet()) {
            for (MessageKey key : block) {
                assertThat(aggregate)
                        .as("MessageKeyCatalog must include %s", key.key())
                        .contains(key.key());
            }
        }
    }

    @Test
    void enumConstantNamesAndCatalogKeysAreUnique() {
        List<String> keys = OWNERSHIP.keySet().stream()
                .flatMap(block -> Arrays.stream(block))
                .map(MessageKey::key)
                .collect(Collectors.toList());
        assertThat(keys).doesNotHaveDuplicates();
    }
}
