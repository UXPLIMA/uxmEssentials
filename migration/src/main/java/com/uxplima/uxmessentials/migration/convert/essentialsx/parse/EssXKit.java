package com.uxplima.uxmessentials.migration.convert.essentialsx.parse;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * One parsed EssentialsX kit entry from {@code kits.yml}: the kit name, the claim {@code delay} in
 * seconds, and the raw item descriptor lines exactly as the file holds them (e.g. {@code "276 1"} or
 * {@code "diamond_sword 1"}). The item strings stay opaque here, turning a descriptor into a concrete
 * stack is the bukkit-side writer's job through Bukkit's item codec, so the parser claims no knowledge
 * of Minecraft materials (docs/12-migration §5.1).
 *
 * @param name the kit name as the entry declares it
 * @param delaySeconds the claim cooldown in seconds; {@code 0} when the entry sets none
 * @param items the raw item descriptor lines in definition order
 */
@NullMarked
public record EssXKit(String name, long delaySeconds, List<String> items) {

    public EssXKit {
        Objects.requireNonNull(name, "name");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (delaySeconds < 0) {
            throw new IllegalArgumentException("kit delay must not be negative: " + delaySeconds);
        }
    }
}
