package com.uxplima.uxmessentials.communication.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.shared.display.BroadcastChannel;

/**
 * The advancement-notification feature's full configuration: whether the server announces a player earning an
 * advancement at all, the filtering that decides which advancements qualify, and the template and delivery choices
 * applied when one does. It is pure operator data, parsed once from config and swapped atomically on reload (a new
 * {@code AtomicReference<AdvancementNoticeConfig>}); the adapter extracts the Bukkit advancement facts, asks
 * {@link AdvancementFilter} whether to announce, then renders {@link #template} (or a per-advancement override)
 * through MiniMessage and delivers it to the selected {@link #channels}.
 *
 * <p>An advancement is identified throughout this model by its namespaced key as a lowercase string, for example
 * {@code minecraft:end/kill_dragon} or {@code minecraft:recipes/building_blocks/oak_planks}. To keep matching
 * case-insensitive, every key the operator supplies in {@link #allow}, {@link #deny}, and the
 * {@link #perAdvancementTemplates} map is normalized to lowercase ({@link Locale#ROOT}) at construction; callers of
 * {@link AdvancementFilter} pass the live advancement key, which the filter lowercases before comparing.
 *
 * @param enabled whether the feature announces advancements at all; when false nothing is announced
 * @param excludeRecipes whether recipe-unlock advancements ({@code minecraft:recipes/…}) are skipped
 * @param onlyAnnounceable whether only advancements the vanilla display marks as announce-to-chat are announced
 * @param allow when non-empty, the exclusive set of advancement keys eligible to announce (allow-list); empty means
 *     every advancement is eligible subject to the other gates
 * @param deny the set of advancement keys never announced (deny-list), checked before the allow-list
 * @param template the global MiniMessage template rendered for an announced advancement; non-null, but may be blank
 *     (a blank template renders nothing, leaving the feature effectively inert even when {@code enabled})
 * @param perAdvancementTemplates per-key template overrides keyed by lowercase advancement key; a key present here
 *     uses its value instead of {@link #template}
 * @param channels the non-empty set of surfaces the rendered notice is pushed to; defaults to {@code {CHAT}} when an
 *     operator leaves it unset
 * @param sound an optional sound key played alongside the notice
 */
public record AdvancementNoticeConfig(
        boolean enabled,
        boolean excludeRecipes,
        boolean onlyAnnounceable,
        Set<String> allow,
        Set<String> deny,
        String template,
        Map<String, String> perAdvancementTemplates,
        Set<BroadcastChannel> channels,
        Optional<String> sound) {

    public AdvancementNoticeConfig {
        allow = lowercased(Objects.requireNonNull(allow, "allow"));
        deny = lowercased(Objects.requireNonNull(deny, "deny"));
        Objects.requireNonNull(template, "template");
        perAdvancementTemplates =
                lowercasedKeys(Objects.requireNonNull(perAdvancementTemplates, "perAdvancementTemplates"));
        Objects.requireNonNull(sound, "sound");
        Set<BroadcastChannel> requested = Set.copyOf(Objects.requireNonNull(channels, "channels"));
        channels = requested.isEmpty() ? Set.of(BroadcastChannel.CHAT) : requested;
    }

    /**
     * The feature configured off: nothing is announced. The other fields carry sensible defaults so a later flip of
     * {@code enabled} via the codec starts from a reasonable shape. Recipes excluded, only announceable
     * advancements considered, no allow/deny entries, a blank (inert) template, chat delivery, and no sound.
     */
    public static AdvancementNoticeConfig disabled() {
        return new AdvancementNoticeConfig(
                false, true, true, Set.of(), Set.of(), "", Map.of(), Set.of(BroadcastChannel.CHAT), Optional.empty());
    }

    private static Set<String> lowercased(Set<String> keys) {
        return keys.stream().map(key -> key.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    private static Map<String, String> lowercasedKeys(Map<String, String> templates) {
        Map<String, String> normalized = new LinkedHashMap<>();
        templates.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), value));
        return Map.copyOf(normalized);
    }
}
