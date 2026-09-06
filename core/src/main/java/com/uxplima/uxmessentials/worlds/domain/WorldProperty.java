package com.uxplima.uxmessentials.worlds.domain;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A single typed per-world property: its catalog key, default, string codec (the {@code decode}
 * doubles as the validator. Empty means "invalid" for the typed properties; the string properties
 * accept any value, treating {@code ""} as the explicit "unset / vanilla" link), and tab-completion
 * suggestions. One descriptor
 * is the single source of truth driving the {@code /worlds set} argument, the {@code world_setting}
 * (de)serialization, and the live-apply binding.
 */
public final class WorldProperty<T> {

    private final String key;
    private final T defaultValue;
    private final Function<String, Optional<T>> decode;
    private final Function<T, String> encode;
    private final List<String> suggestions;

    private WorldProperty(
            String key,
            T defaultValue,
            Function<String, Optional<T>> decode,
            Function<T, String> encode,
            List<String> suggestions) {
        this.key = Objects.requireNonNull(key, "key");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.decode = Objects.requireNonNull(decode, "decode");
        this.encode = Objects.requireNonNull(encode, "encode");
        this.suggestions = List.copyOf(suggestions);
    }

    public String key() {
        return key;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public Optional<T> decode(String raw) {
        return raw == null ? Optional.empty() : decode.apply(raw.strip());
    }

    public String encode(T value) {
        return encode.apply(Objects.requireNonNull(value, "value"));
    }

    public List<String> suggestions() {
        return suggestions;
    }

    static WorldProperty<Boolean> ofBoolean(String key, boolean def) {
        return new WorldProperty<>(
                key,
                def,
                raw -> switch (raw.toLowerCase(Locale.ROOT)) {
                    case "true" -> Optional.of(Boolean.TRUE);
                    case "false" -> Optional.of(Boolean.FALSE);
                    default -> Optional.empty();
                },
                String::valueOf,
                List.of("true", "false"));
    }

    static <E extends Enum<E>> WorldProperty<E> ofEnum(String key, E def, Class<E> type) {
        List<String> names =
                Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
        return new WorldProperty<>(
                key,
                def,
                raw -> {
                    for (E constant : type.getEnumConstants()) {
                        if (constant.name().equalsIgnoreCase(raw)) {
                            return Optional.of(constant);
                        }
                    }
                    return Optional.empty();
                },
                Enum::name,
                names);
    }

    static WorldProperty<Long> ofTicks(String key) {
        return new WorldProperty<>(
                key,
                0L,
                raw -> {
                    try {
                        long ticks = Long.parseLong(raw);
                        return ticks < 0 ? Optional.empty() : Optional.of(ticks);
                    } catch (NumberFormatException notANumber) {
                        return Optional.empty();
                    }
                },
                String::valueOf,
                List.of("0", "6000", "12000", "18000"));
    }

    static WorldProperty<Integer> ofInteger(String key, int def) {
        return new WorldProperty<>(
                key,
                def,
                raw -> {
                    try {
                        int value = Integer.parseInt(raw);
                        return value < 0 ? Optional.empty() : Optional.of(value);
                    } catch (NumberFormatException notANumber) {
                        return Optional.empty();
                    }
                },
                String::valueOf,
                List.of("0", "1", "10", "50"));
    }

    static WorldProperty<String> ofString(String key, String def) {
        // String properties (the portal-nether/end links) accept any value, including the empty
        // string. After the outer decode strips whitespace, "" is the explicit "unset / vanilla"
        // value: it clears a world-name link so portals fall back to vanilla routing.
        return new WorldProperty<>(key, def, Optional::of, Function.identity(), List.of());
    }

    /**
     * A void-rescue chain property: any value the {@link VoidRescueChain} grammar accepts, with {@code ""}
     * meaning the world is unmanaged. Validating here is what makes {@code /worlds set} refuse a typo at the
     * moment it is typed rather than storing a chain that never fires.
     */
    static WorldProperty<VoidRescueChain> ofChain(String key) {
        return new WorldProperty<>(
                key, VoidRescueChain.none(), VoidRescueChain::parse, VoidRescueChain::encode, List.of());
    }

    /**
     * A signed integer that may be left unset. {@code ""} decodes to an absent value and encodes back to
     * {@code ""}, so clearing the property in the GUI or with {@code /worlds set <world> <key> ""} restores
     * the "not configured" state rather than storing a number that happens to mean it.
     */
    static WorldProperty<Optional<Integer>> ofOptionalInteger(String key) {
        return new WorldProperty<>(
                key,
                Optional.empty(),
                raw -> {
                    if (raw.isEmpty()) {
                        return Optional.of(Optional.empty());
                    }
                    try {
                        return Optional.of(Optional.of(Integer.valueOf(raw)));
                    } catch (NumberFormatException notANumber) {
                        return Optional.empty();
                    }
                },
                value -> value.map(String::valueOf).orElse(""),
                List.of());
    }

    static WorldProperty<BigDecimal> ofDecimal(String key) {
        return new WorldProperty<>(
                key,
                BigDecimal.ZERO,
                raw -> {
                    try {
                        BigDecimal value = new BigDecimal(raw);
                        return value.signum() < 0 ? Optional.empty() : Optional.of(value);
                    } catch (NumberFormatException notANumber) {
                        return Optional.empty();
                    }
                },
                BigDecimal::toPlainString,
                List.of("0", "100", "500", "1000"));
    }
}
