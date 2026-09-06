package com.uxplima.uxmessentials.holograms.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * What a {@link Hologram} displays: its {@link HologramType} and the content that type renders, ordered text
 * {@link HologramLine}s for {@link HologramType#TEXT}, an item-material name for {@link HologramType#ITEM}, or a
 * BlockData string for {@link HologramType#BLOCK}. Grouping the content fields into one immutable value object
 * keeps the {@link Hologram} aggregate small while leaving its public surface unchanged, {@code Hologram}
 * delegates every content transition and accessor here.
 *
 * <p>The type invariants live here so they hold wherever content is built: a {@code TEXT} hologram always carries
 * at least one line (an empty text hologram would render nothing and is rejected; the line-removal op refuses to
 * drop the last line), while an {@code ITEM}/{@code BLOCK} hologram needs its model string and may carry no line.
 * The line list is copied defensively on construction so a stored snapshot is immutable.
 *
 * @param type whether the hologram renders text lines, a floating item, or a floating block
 * @param lines the ordered text lines (at least one for TEXT; may be empty for ITEM/BLOCK)
 * @param itemMaterial the {@code Material} name shown by an ITEM hologram, or {@code null} for other types
 * @param blockData the BlockData string shown by a BLOCK hologram, or {@code null} for other types
 * @param headTexture the base64 skin-texture shown by a HEAD hologram, or {@code null} for other types
 * @param entityType the entity-type name shown by an ENTITY hologram, or {@code null} for other types
 */
public record HologramContent(
        HologramType type,
        List<HologramLine> lines,
        @Nullable String itemMaterial,
        @Nullable String blockData,
        @Nullable String headTexture,
        @Nullable String entityType) {

    public HologramContent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(lines, "lines");
        lines = List.copyOf(lines);
        if (type.requiresLines() && lines.isEmpty()) {
            throw new IllegalArgumentException("a text hologram needs at least one line");
        }
        if (type == HologramType.ITEM && (itemMaterial == null || itemMaterial.isBlank())) {
            throw new IllegalArgumentException("an item hologram needs an item material");
        }
        if (type == HologramType.BLOCK && (blockData == null || blockData.isBlank())) {
            throw new IllegalArgumentException("a block hologram needs block data");
        }
        if (type == HologramType.HEAD && (headTexture == null || headTexture.isBlank())) {
            throw new IllegalArgumentException("a head hologram needs a skin texture");
        }
        if (type == HologramType.ENTITY && (entityType == null || entityType.isBlank())) {
            throw new IllegalArgumentException("an entity hologram needs an entity type");
        }
    }

    /** Text content with the given ordered lines (at least one). */
    static HologramContent text(List<HologramLine> lines) {
        return new HologramContent(HologramType.TEXT, lines, null, null, null, null);
    }

    /** Item content showing {@code itemMaterial}, with no lines. */
    static HologramContent item(String itemMaterial) {
        return new HologramContent(
                HologramType.ITEM, List.of(), Objects.requireNonNull(itemMaterial, "itemMaterial"), null, null, null);
    }

    /** Block content showing {@code blockData}, with no lines. */
    static HologramContent block(String blockData) {
        return new HologramContent(
                HologramType.BLOCK, List.of(), null, Objects.requireNonNull(blockData, "blockData"), null, null);
    }

    /** Head content showing the player head with base64 {@code headTexture}, with no lines. */
    static HologramContent head(String headTexture) {
        return new HologramContent(
                HologramType.HEAD, List.of(), null, null, Objects.requireNonNull(headTexture, "headTexture"), null);
    }

    /** Entity content showing the frozen mob {@code entityType}, with no lines. */
    static HologramContent entity(String entityType) {
        return new HologramContent(
                HologramType.ENTITY, List.of(), null, null, null, Objects.requireNonNull(entityType, "entityType"));
    }

    /** Replace the text lines wholesale, keeping the type and model content. The type invariants still apply. */
    HologramContent withLines(List<HologramLine> newLines) {
        return new HologramContent(type, newLines, itemMaterial, blockData, headTexture, entityType);
    }

    HologramContent withLineAppended(HologramLine line) {
        Objects.requireNonNull(line, "line");
        List<HologramLine> next = new ArrayList<>(lines);
        next.add(line);
        return new HologramContent(type, next, itemMaterial, blockData, headTexture, entityType);
    }

    HologramContent withLineReplaced(int index, HologramLine line) {
        Objects.requireNonNull(line, "line");
        requireInRange(index);
        List<HologramLine> next = new ArrayList<>(lines);
        next.set(index, line);
        return new HologramContent(type, next, itemMaterial, blockData, headTexture, entityType);
    }

    HologramContent withLineInserted(int index, HologramLine line) {
        Objects.requireNonNull(line, "line");
        if (index < 0) {
            throw new IndexOutOfBoundsException("line index must not be negative: " + index);
        }
        List<HologramLine> next = new ArrayList<>(lines);
        next.add(Math.min(index, next.size()), line);
        return new HologramContent(type, next, itemMaterial, blockData, headTexture, entityType);
    }

    HologramContent withLineRemoved(int index) {
        requireInRange(index);
        if (type.requiresLines() && lines.size() == 1) {
            throw new IllegalStateException("a text hologram must keep at least one line");
        }
        List<HologramLine> next = new ArrayList<>(lines);
        next.remove(index);
        return new HologramContent(type, next, itemMaterial, blockData, headTexture, entityType);
    }

    /** Switch to an ITEM hologram showing {@code newItemMaterial} (lines kept for a future label rendering). */
    HologramContent asItem(String newItemMaterial) {
        Objects.requireNonNull(newItemMaterial, "newItemMaterial");
        return new HologramContent(HologramType.ITEM, lines, newItemMaterial, null, null, null);
    }

    /** Switch to a BLOCK hologram showing {@code newBlockData} (lines kept for a future label rendering). */
    HologramContent asBlock(String newBlockData) {
        Objects.requireNonNull(newBlockData, "newBlockData");
        return new HologramContent(HologramType.BLOCK, lines, null, newBlockData, null, null);
    }

    /** Switch to a HEAD hologram showing the player head with base64 {@code newHeadTexture} (lines kept). */
    HologramContent asHead(String newHeadTexture) {
        Objects.requireNonNull(newHeadTexture, "newHeadTexture");
        return new HologramContent(HologramType.HEAD, lines, null, null, newHeadTexture, null);
    }

    /** Switch to an ENTITY hologram showing the frozen mob {@code newEntityType} (lines kept). */
    HologramContent asEntity(String newEntityType) {
        Objects.requireNonNull(newEntityType, "newEntityType");
        return new HologramContent(HologramType.ENTITY, lines, null, null, null, newEntityType);
    }

    int lineCount() {
        return lines.size();
    }

    private void requireInRange(int index) {
        if (index < 0 || index >= lines.size()) {
            throw new IndexOutOfBoundsException("line index out of range: " + index);
        }
    }
}
