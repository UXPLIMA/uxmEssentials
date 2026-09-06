package com.uxplima.uxmessentials.persistence.holograms;

import static com.uxplima.uxmessentials.persistence.jooq.tables.HologramAction.HOLOGRAM_ACTION;
import static com.uxplima.uxmessentials.persistence.jooq.tables.HologramBlacklist.HOLOGRAM_BLACKLIST;
import static com.uxplima.uxmessentials.persistence.jooq.tables.HologramLines.HOLOGRAM_LINES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.HologramManualViewer.HOLOGRAM_MANUAL_VIEWER;
import static com.uxplima.uxmessentials.persistence.jooq.tables.HologramPages.HOLOGRAM_PAGES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.Holograms.HOLOGRAMS;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.HologramsRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import org.jooq.DSLContext;
import org.jooq.Record;

/**
 * The jOOQ-backed {@link HologramRepository} over the generated {@code HOLOGRAMS} and {@code HOLOGRAM_LINES}
 * tables. Holograms are server-wide and keyed by name alone, so a lookup is a single-row {@code SELECT} on
 * the name primary key plus its ordered lines, the list reads every row in stored creation order, and a
 * {@code save} upserts the name row then rewrites that hologram's lines in one transaction (so a line edit
 * never leaves a stale row behind). Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
public final class JooqHologramRepository extends JooqRepository implements HologramRepository {

    public JooqHologramRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Optional<Hologram> find(HologramName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> dsl.selectFrom(HOLOGRAMS)
                .where(HOLOGRAMS.NAME.eq(name.value()))
                .fetchOptional()
                .map(row -> HologramRows.toHologram(
                        row, lines(dsl, name.value()), extraPages(dsl, name.value()), actions(dsl, name.value()))));
    }

    @Override
    public List<Hologram> all() {
        return read(dsl -> {
            Map<String, List<String>> linesByName = allLines(dsl);
            Map<String, List<List<String>>> pagesByName = allExtraPages(dsl);
            Map<String, List<ClickAction>> actionsByName = allActions(dsl);
            return dsl.selectFrom(HOLOGRAMS)
                    .orderBy(HOLOGRAMS.CREATED_AT.asc(), HOLOGRAMS.NAME.asc())
                    .fetch()
                    .map(row -> HologramRows.toHologram(
                            row,
                            linesByName.getOrDefault(row.get(HOLOGRAMS.NAME), List.of()),
                            pagesByName.getOrDefault(row.get(HOLOGRAMS.NAME), List.of()),
                            actionsByName.getOrDefault(row.get(HOLOGRAMS.NAME), List.of())));
        });
    }

    @Override
    public boolean exists(HologramName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> dsl.fetchExists(HOLOGRAMS, HOLOGRAMS.NAME.eq(name.value())));
    }

    @Override
    public void save(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        write(dsl -> {
            upsertNameRow(dsl, hologram);
            rewriteLines(dsl, hologram);
            rewritePages(dsl, hologram);
            rewriteActions(dsl, hologram);
            return null;
        });
    }

    @Override
    public void delete(HologramName name) {
        Objects.requireNonNull(name, "name");
        write(dsl -> {
            dsl.deleteFrom(HOLOGRAM_LINES)
                    .where(HOLOGRAM_LINES.HOLOGRAM.eq(name.value()))
                    .execute();
            dsl.deleteFrom(HOLOGRAM_PAGES)
                    .where(HOLOGRAM_PAGES.HOLOGRAM.eq(name.value()))
                    .execute();
            dsl.deleteFrom(HOLOGRAM_ACTION)
                    .where(HOLOGRAM_ACTION.HOLOGRAM_NAME.eq(name.value()))
                    .execute();
            dsl.deleteFrom(HOLOGRAM_MANUAL_VIEWER)
                    .where(HOLOGRAM_MANUAL_VIEWER.HOLOGRAM_NAME.eq(name.value()))
                    .execute();
            dsl.deleteFrom(HOLOGRAM_BLACKLIST)
                    .where(HOLOGRAM_BLACKLIST.HOLOGRAM_NAME.eq(name.value()))
                    .execute();
            return dsl.deleteFrom(HOLOGRAMS)
                    .where(HOLOGRAMS.NAME.eq(name.value()))
                    .execute();
        });
    }

    @Override
    public Set<UUID> manualViewers(HologramName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> {
            Set<UUID> viewers = new LinkedHashSet<>();
            for (String stored : dsl.select(HOLOGRAM_MANUAL_VIEWER.PLAYER_UUID)
                    .from(HOLOGRAM_MANUAL_VIEWER)
                    .where(HOLOGRAM_MANUAL_VIEWER.HOLOGRAM_NAME.eq(name.value()))
                    .fetch(HOLOGRAM_MANUAL_VIEWER.PLAYER_UUID)) {
                viewers.add(UUID.fromString(stored));
            }
            return viewers;
        });
    }

    @Override
    public void showTo(HologramName name, UUID viewer) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(viewer, "viewer");
        write(dsl -> dsl.insertInto(HOLOGRAM_MANUAL_VIEWER)
                .set(HOLOGRAM_MANUAL_VIEWER.HOLOGRAM_NAME, name.value())
                .set(HOLOGRAM_MANUAL_VIEWER.PLAYER_UUID, viewer.toString())
                .onConflict(HOLOGRAM_MANUAL_VIEWER.HOLOGRAM_NAME, HOLOGRAM_MANUAL_VIEWER.PLAYER_UUID)
                .doNothing()
                .execute());
    }

    @Override
    public void hideFrom(HologramName name, UUID viewer) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(viewer, "viewer");
        write(dsl -> dsl.deleteFrom(HOLOGRAM_MANUAL_VIEWER)
                .where(HOLOGRAM_MANUAL_VIEWER.HOLOGRAM_NAME.eq(name.value()))
                .and(HOLOGRAM_MANUAL_VIEWER.PLAYER_UUID.eq(viewer.toString()))
                .execute());
    }

    @Override
    public Set<UUID> blacklisted(HologramName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> {
            Set<UUID> blacklisted = new LinkedHashSet<>();
            for (String stored : dsl.select(HOLOGRAM_BLACKLIST.PLAYER_UUID)
                    .from(HOLOGRAM_BLACKLIST)
                    .where(HOLOGRAM_BLACKLIST.HOLOGRAM_NAME.eq(name.value()))
                    .fetch(HOLOGRAM_BLACKLIST.PLAYER_UUID)) {
                blacklisted.add(UUID.fromString(stored));
            }
            return blacklisted;
        });
    }

    @Override
    public void addToBlacklist(HologramName name, UUID viewer) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(viewer, "viewer");
        write(dsl -> dsl.insertInto(HOLOGRAM_BLACKLIST)
                .set(HOLOGRAM_BLACKLIST.HOLOGRAM_NAME, name.value())
                .set(HOLOGRAM_BLACKLIST.PLAYER_UUID, viewer.toString())
                .onConflict(HOLOGRAM_BLACKLIST.HOLOGRAM_NAME, HOLOGRAM_BLACKLIST.PLAYER_UUID)
                .doNothing()
                .execute());
    }

    @Override
    public void removeFromBlacklist(HologramName name, UUID viewer) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(viewer, "viewer");
        write(dsl -> dsl.deleteFrom(HOLOGRAM_BLACKLIST)
                .where(HOLOGRAM_BLACKLIST.HOLOGRAM_NAME.eq(name.value()))
                .and(HOLOGRAM_BLACKLIST.PLAYER_UUID.eq(viewer.toString()))
                .execute());
    }

    private static List<String> lines(DSLContext dsl, String name) {
        return dsl.select(HOLOGRAM_LINES.TEXT)
                .from(HOLOGRAM_LINES)
                .where(HOLOGRAM_LINES.HOLOGRAM.eq(name))
                .orderBy(HOLOGRAM_LINES.IDX.asc())
                .fetch(HOLOGRAM_LINES.TEXT);
    }

    private static Map<String, List<String>> allLines(DSLContext dsl) {
        Map<String, List<String>> byName = new LinkedHashMap<>();
        for (Record row : dsl.select(HOLOGRAM_LINES.HOLOGRAM, HOLOGRAM_LINES.TEXT)
                .from(HOLOGRAM_LINES)
                .orderBy(HOLOGRAM_LINES.HOLOGRAM.asc(), HOLOGRAM_LINES.IDX.asc())
                .fetch()) {
            byName.computeIfAbsent(row.get(HOLOGRAM_LINES.HOLOGRAM), key -> new ArrayList<>())
                    .add(row.get(HOLOGRAM_LINES.TEXT));
        }
        return byName;
    }

    /** The extra pages 1..n of one hologram, each an ordered list of line texts, in page order. */
    private static List<List<String>> extraPages(DSLContext dsl, String name) {
        List<List<String>> pages = new ArrayList<>();
        int currentPage = Integer.MIN_VALUE;
        List<String> page = null;
        for (Record row : dsl.select(HOLOGRAM_PAGES.PAGE_INDEX, HOLOGRAM_PAGES.TEXT)
                .from(HOLOGRAM_PAGES)
                .where(HOLOGRAM_PAGES.HOLOGRAM.eq(name))
                .orderBy(HOLOGRAM_PAGES.PAGE_INDEX.asc(), HOLOGRAM_PAGES.IDX.asc())
                .fetch()) {
            int pageIndex = row.get(HOLOGRAM_PAGES.PAGE_INDEX);
            if (page == null || pageIndex != currentPage) {
                page = new ArrayList<>();
                pages.add(page);
                currentPage = pageIndex;
            }
            page.add(row.get(HOLOGRAM_PAGES.TEXT));
        }
        return pages;
    }

    /** Every hologram's extra pages, keyed by name: the {@link #all()} counterpart of {@link #extraPages}. */
    private static Map<String, List<List<String>>> allExtraPages(DSLContext dsl) {
        Map<String, Map<Integer, List<String>>> byName = new LinkedHashMap<>();
        for (Record row : dsl.select(HOLOGRAM_PAGES.HOLOGRAM, HOLOGRAM_PAGES.PAGE_INDEX, HOLOGRAM_PAGES.TEXT)
                .from(HOLOGRAM_PAGES)
                .orderBy(HOLOGRAM_PAGES.HOLOGRAM.asc(), HOLOGRAM_PAGES.PAGE_INDEX.asc(), HOLOGRAM_PAGES.IDX.asc())
                .fetch()) {
            byName.computeIfAbsent(row.get(HOLOGRAM_PAGES.HOLOGRAM), key -> new LinkedHashMap<>())
                    .computeIfAbsent(row.get(HOLOGRAM_PAGES.PAGE_INDEX), key -> new ArrayList<>())
                    .add(row.get(HOLOGRAM_PAGES.TEXT));
        }
        Map<String, List<List<String>>> result = new LinkedHashMap<>();
        byName.forEach((name, pagesByIndex) -> result.put(name, new ArrayList<>(pagesByIndex.values())));
        return result;
    }

    /** One hologram's click-action chain, already ordered by {@code ordinal} and stripped of unparseable rows. */
    private static List<ClickAction> actions(DSLContext dsl, String name) {
        List<ClickAction> actions = new ArrayList<>();
        for (Record row : dsl.select(HOLOGRAM_ACTION.CLICK_TRIGGER, HOLOGRAM_ACTION.TYPE, HOLOGRAM_ACTION.VALUE)
                .from(HOLOGRAM_ACTION)
                .where(HOLOGRAM_ACTION.HOLOGRAM_NAME.eq(name))
                .orderBy(HOLOGRAM_ACTION.ORDINAL.asc())
                .fetch()) {
            addAction(actions, row);
        }
        return actions;
    }

    /** Every hologram's action chain, keyed by name: the {@link #all()} counterpart of {@link #actions}. */
    private static Map<String, List<ClickAction>> allActions(DSLContext dsl) {
        Map<String, List<ClickAction>> byName = new LinkedHashMap<>();
        for (Record row : dsl.select(
                        HOLOGRAM_ACTION.HOLOGRAM_NAME,
                        HOLOGRAM_ACTION.CLICK_TRIGGER,
                        HOLOGRAM_ACTION.TYPE,
                        HOLOGRAM_ACTION.VALUE)
                .from(HOLOGRAM_ACTION)
                .orderBy(HOLOGRAM_ACTION.HOLOGRAM_NAME.asc(), HOLOGRAM_ACTION.ORDINAL.asc())
                .fetch()) {
            addAction(byName.computeIfAbsent(row.get(HOLOGRAM_ACTION.HOLOGRAM_NAME), key -> new ArrayList<>()), row);
        }
        return byName;
    }

    private static void addAction(List<ClickAction> target, Record row) {
        ClickAction action = HologramRows.toAction(
                row.get(HOLOGRAM_ACTION.CLICK_TRIGGER), row.get(HOLOGRAM_ACTION.TYPE), row.get(HOLOGRAM_ACTION.VALUE));
        if (action != null) {
            target.add(action);
        }
    }

    private static void rewriteActions(DSLContext dsl, Hologram hologram) {
        String name = hologram.name().value();
        dsl.deleteFrom(HOLOGRAM_ACTION)
                .where(HOLOGRAM_ACTION.HOLOGRAM_NAME.eq(name))
                .execute();
        List<ClickAction> actions = hologram.actions();
        for (int ordinal = 0; ordinal < actions.size(); ordinal++) {
            ClickAction action = actions.get(ordinal);
            dsl.insertInto(HOLOGRAM_ACTION)
                    .set(HOLOGRAM_ACTION.HOLOGRAM_NAME, name)
                    .set(HOLOGRAM_ACTION.ORDINAL, ordinal)
                    .set(HOLOGRAM_ACTION.CLICK_TRIGGER, action.trigger().name())
                    .set(HOLOGRAM_ACTION.TYPE, action.type().name())
                    .set(HOLOGRAM_ACTION.VALUE, action.value())
                    .execute();
        }
    }

    private static void upsertNameRow(DSLContext dsl, Hologram hologram) {
        HologramsRecord record = dsl.newRecord(HOLOGRAMS);
        HologramRows.apply(record, hologram);
        dsl.insertInto(HOLOGRAMS)
                .set(record)
                .onConflict(HOLOGRAMS.NAME)
                .doUpdate()
                .set(HOLOGRAMS.TYPE, record.getType())
                .set(HOLOGRAMS.ITEM_MATERIAL, record.getItemMaterial())
                .set(HOLOGRAMS.BLOCK_DATA, record.getBlockData())
                .set(HOLOGRAMS.WORLD, record.getWorld())
                .set(HOLOGRAMS.WORLD_NAME, record.getWorldName())
                .set(HOLOGRAMS.X, record.getX())
                .set(HOLOGRAMS.Y, record.getY())
                .set(HOLOGRAMS.Z, record.getZ())
                .set(HOLOGRAMS.YAW, record.getYaw())
                .set(HOLOGRAMS.PITCH, record.getPitch())
                .set(HOLOGRAMS.CREATED_AT, record.getCreatedAt())
                .set(HOLOGRAMS.BILLBOARD, record.getBillboard())
                .set(HOLOGRAMS.BACKGROUND_ARGB, record.getBackgroundArgb())
                .set(HOLOGRAMS.TEXT_SHADOW, record.getTextShadow())
                .set(HOLOGRAMS.BRIGHTNESS_BLOCK, record.getBrightnessBlock())
                .set(HOLOGRAMS.BRIGHTNESS_SKY, record.getBrightnessSky())
                .set(HOLOGRAMS.SCALE, record.getScale())
                .set(HOLOGRAMS.SCALE_Y, record.getScaleY())
                .set(HOLOGRAMS.SCALE_Z, record.getScaleZ())
                .set(HOLOGRAMS.TRANSLATION_X, record.getTranslationX())
                .set(HOLOGRAMS.TRANSLATION_Y, record.getTranslationY())
                .set(HOLOGRAMS.TRANSLATION_Z, record.getTranslationZ())
                .set(HOLOGRAMS.SEE_THROUGH, record.getSeeThrough())
                .set(HOLOGRAMS.TEXT_ALIGNMENT, record.getTextAlignment())
                .set(HOLOGRAMS.SHADOW_RADIUS, record.getShadowRadius())
                .set(HOLOGRAMS.SHADOW_STRENGTH, record.getShadowStrength())
                .set(HOLOGRAMS.LINE_WIDTH, record.getLineWidth())
                .set(HOLOGRAMS.VIEW_RANGE, record.getViewRange())
                .set(HOLOGRAMS.VISIBILITY_MODE, record.getVisibilityMode())
                .set(HOLOGRAMS.VISIBILITY_PERMISSION, record.getVisibilityPermission())
                .set(HOLOGRAMS.VISIBILITY_DISTANCE, record.getVisibilityDistance())
                .set(HOLOGRAMS.ROTATION_YAW, record.getRotationYaw())
                .set(HOLOGRAMS.ROTATION_PITCH, record.getRotationPitch())
                .set(HOLOGRAMS.REFRESH_INTERVAL_TICKS, record.getRefreshIntervalTicks())
                .set(HOLOGRAMS.LINKED_NPC_NAME, record.getLinkedNpcName())
                // The V52-V56 columns are updated here too, so a setting applied to an already-saved hologram
                // (head/entity model, glow/opacity, click command, leaderboard) survives a restart rather than
                // only persisting on the row's first insert.
                .set(HOLOGRAMS.HEAD_TEXTURE, record.getHeadTexture())
                .set(HOLOGRAMS.ENTITY_TYPE, record.getEntityType())
                .set(HOLOGRAMS.GLOW_ARGB, record.getGlowArgb())
                .set(HOLOGRAMS.TEXT_OPACITY, record.getTextOpacity())
                .set(HOLOGRAMS.CLICK_COMMAND, record.getClickCommand())
                .set(HOLOGRAMS.LEADERBOARD_PROVIDER, record.getLeaderboardProvider())
                .set(HOLOGRAMS.LEADERBOARD_LIMIT, record.getLeaderboardLimit())
                .set(HOLOGRAMS.GROW_UP, record.getGrowUp())
                .execute();
    }

    private static void rewriteLines(DSLContext dsl, Hologram hologram) {
        String name = hologram.name().value();
        dsl.deleteFrom(HOLOGRAM_LINES).where(HOLOGRAM_LINES.HOLOGRAM.eq(name)).execute();
        List<HologramLine> lines = hologram.lines();
        for (int idx = 0; idx < lines.size(); idx++) {
            dsl.insertInto(HOLOGRAM_LINES)
                    .set(HOLOGRAM_LINES.HOLOGRAM, name)
                    .set(HOLOGRAM_LINES.IDX, idx)
                    .set(HOLOGRAM_LINES.TEXT, lines.get(idx).value())
                    .execute();
        }
    }

    private static void rewritePages(DSLContext dsl, Hologram hologram) {
        // Page 0 lives in hologram_lines (rewritten above); only the extra pages 1..n are stored here. A
        // single-page hologram clears any stale page rows so a removed page never lingers.
        String name = hologram.name().value();
        dsl.deleteFrom(HOLOGRAM_PAGES).where(HOLOGRAM_PAGES.HOLOGRAM.eq(name)).execute();
        if (!hologram.isMultiPage()) {
            return;
        }
        for (int pageIndex = 1; pageIndex < hologram.pageCount(); pageIndex++) {
            List<HologramLine> lines = hologram.pageLines(pageIndex);
            for (int idx = 0; idx < lines.size(); idx++) {
                dsl.insertInto(HOLOGRAM_PAGES)
                        .set(HOLOGRAM_PAGES.HOLOGRAM, name)
                        .set(HOLOGRAM_PAGES.PAGE_INDEX, pageIndex)
                        .set(HOLOGRAM_PAGES.IDX, idx)
                        .set(HOLOGRAM_PAGES.TEXT, lines.get(idx).value())
                        .execute();
            }
        }
    }
}
