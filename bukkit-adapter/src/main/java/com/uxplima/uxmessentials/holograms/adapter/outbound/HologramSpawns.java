package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.function.UnaryOperator;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.Rotation;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.hologram.HologramManager;
import com.uxplima.uxmlib.hologram.Holograms;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Turns a domain {@link Hologram} into a live uxmLib hologram by its type, on the renderer's behalf: a
 * {@code TEXT} hologram from the configured builder, an {@code ITEM} hologram from the resolved {@code Material},
 * a {@code BLOCK} hologram from the parsed BlockData. An ITEM with an unknown material or a BLOCK with an
 * unparseable BlockData string is failed soft. Logged and skipped (returns {@code null}) rather than thrown, so
 * one bad value never breaks the render of the others. Pure of any tracking or visibility, so the renderer keeps
 * the lifecycle and this helper keeps the per-type spawn so each stays small.
 */
@NullMarked
final class HologramSpawns {

    /** The vanilla display entity tracking range in blocks at view-range multiplier 1.0. */
    static final float VANILLA_VIEW_BLOCKS = 64.0f;

    private HologramSpawns() {}

    /** Spawn the live entity for {@code hologram} at {@code at}, or {@code null} when its model value is invalid. */
    static @Nullable RenderedHologram spawnFor(
            HologramManager manager,
            Logger log,
            Hologram hologram,
            Location at,
            UnaryOperator<String> placeholders,
            MiniMessage miniMessage,
            TagResolver globalTags) {
        return switch (hologram.type()) {
            case TEXT ->
                RenderedHologram.ofText(manager.spawn(builderFor(hologram, placeholders, miniMessage, globalTags), at));
            case ITEM -> spawnItem(manager, log, hologram, at);
            case BLOCK -> spawnBlock(manager, log, hologram, at);
            case HEAD -> spawnHead(manager, log, hologram, at);
            case ENTITY -> spawnEntity(log, hologram, at);
        };
    }

    private static @Nullable RenderedHologram spawnEntity(Logger log, Hologram hologram, Location at) {
        org.bukkit.entity.EntityType type = HologramModels.entityTypeOf(hologram.entityType());
        if (type == null) {
            log.warn(
                    "skipping entity hologram {}. Unknown or non-living entity type {}",
                    hologram.name().value(),
                    String.valueOf(hologram.entityType()));
            return null;
        }
        org.bukkit.entity.Entity entity = at.getWorld().spawnEntity(at, type);
        freeze(entity);
        return RenderedHologram.ofEntity(entity);
    }

    /** Make a spawned entity a static decoration: no AI, no gravity, invulnerable, silent, non-colliding, transient. */
    private static void freeze(org.bukkit.entity.Entity entity) {
        entity.setGravity(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setPersistent(false);
        if (entity instanceof org.bukkit.entity.LivingEntity living) {
            living.setAI(false);
            living.setCollidable(false);
            living.setRemoveWhenFarAway(false);
        }
    }

    private static @Nullable RenderedHologram spawnHead(
            HologramManager manager, Logger log, Hologram hologram, Location at) {
        ItemStack head = HologramModels.headOf(hologram.headTexture());
        if (head == null) {
            log.warn(
                    "skipping head hologram {}, unusable skin texture",
                    hologram.name().value());
            return null;
        }
        Holograms.ItemBuilder builder = Holograms.item(head);
        HologramAppearances.applyDisplay(builder, hologram.appearance());
        applyRotation(builder, hologram);
        return RenderedHologram.ofModel(manager.spawn(builder, at));
    }

    private static @Nullable RenderedHologram spawnItem(
            HologramManager manager, Logger log, Hologram hologram, Location at) {
        ItemStack item = HologramModels.itemOf(hologram.itemMaterial());
        if (item == null) {
            log.warn(
                    "skipping item hologram {}, unknown material {}",
                    hologram.name().value(),
                    String.valueOf(hologram.itemMaterial()));
            return null;
        }
        Holograms.ItemBuilder builder = Holograms.item(item);
        HologramAppearances.applyDisplay(builder, hologram.appearance());
        applyRotation(builder, hologram);
        return RenderedHologram.ofModel(manager.spawn(builder, at));
    }

    private static @Nullable RenderedHologram spawnBlock(
            HologramManager manager, Logger log, Hologram hologram, Location at) {
        BlockData block = HologramModels.blockOf(hologram.blockData());
        if (block == null) {
            log.warn(
                    "skipping block hologram {}, invalid block data {}",
                    hologram.name().value(),
                    String.valueOf(hologram.blockData()));
            return null;
        }
        Holograms.BlockBuilder builder = Holograms.block(block);
        HologramAppearances.applyDisplay(builder, hologram.appearance());
        applyRotation(builder, hologram);
        return RenderedHologram.ofModel(manager.spawn(builder, at));
    }

    /**
     * Apply a stored {@link Rotation} to a model (item/block) builder when it is non-zero. Only visible with a
     * FIXED billboard: applied regardless, so the operator sets the billboard separately as on the text path.
     */
    private static void applyRotation(Holograms.ModelBuilder<?> builder, Hologram hologram) {
        Rotation rotation = hologram.rotation();
        if (rotation.isRotated()) {
            builder.rotation(rotation.yaw(), rotation.pitch());
        }
    }

    /**
     * Build the configured uxmLib builder for {@code hologram}: each line's MiniMessage source run through
     * {@code placeholders} (the PlaceholderAPI {@code %token%} pre-parse transform) then deserialised with the
     * {@code globalTags} resolver (the MiniPlaceholders server-global {@code <tag>}s, or an empty resolver when
     * that plugin is absent), the appearance applied, and a finite visibility distance mapped onto the native view
     * range. Pure (no world, no entity), so the placeholder resolution, appearance mapping and distance-to-view-range
     * mapping are unit-testable through {@code builder.spec()}.
     */
    static Holograms.Builder builderFor(
            Hologram hologram, UnaryOperator<String> placeholders, MiniMessage miniMessage, TagResolver globalTags) {
        Holograms.Builder builder = Holograms.builder();
        for (HologramLine line : hologram.lines()) {
            builder.line(miniMessage.deserialize(placeholders.apply(line.value()), globalTags));
        }
        HologramAppearances.apply(builder, hologram.appearance());
        Rotation rotation = hologram.rotation();
        if (rotation.isRotated()) {
            // Only visible with a FIXED billboard; applied regardless so the operator sets the billboard separately.
            builder.rotation(rotation.yaw(), rotation.pitch());
        }
        Visibility visibility = hologram.visibility();
        if (visibility.hasDistanceLimit()) {
            // The lib view range is a multiplier on the vanilla tracking range, so a block radius maps to
            // blocks / 64; this overrides the appearance's own view-range multiplier when a distance is set.
            builder.viewRange(visibility.distance() / VANILLA_VIEW_BLOCKS);
        }
        return builder;
    }
}
