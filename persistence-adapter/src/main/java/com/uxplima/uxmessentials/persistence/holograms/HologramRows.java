package com.uxplima.uxmessentials.persistence.holograms;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.domain.Appearance;
import com.uxplima.uxmessentials.holograms.domain.Billboard;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.HologramPage;
import com.uxplima.uxmessentials.holograms.domain.HologramType;
import com.uxplima.uxmessentials.holograms.domain.LeaderboardSpec;
import com.uxplima.uxmessentials.holograms.domain.Rotation;
import com.uxplima.uxmessentials.holograms.domain.TextAlignment;
import com.uxplima.uxmessentials.holograms.domain.Transform;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.persistence.jooq.tables.Holograms;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.HologramsRecord;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import com.uxplima.uxmessentials.shared.domain.action.ClickActionType;
import com.uxplima.uxmessentials.shared.domain.action.ClickTrigger;
import org.jooq.Record;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption mapping between a {@code holograms} row (plus its ordered {@code hologram_lines}
 * child rows) and the domain {@link Hologram}. The world uuid is stored as its canonical 36-character text
 * and the creation time as epoch milliseconds, so the column shape is identical on every backend. The V35
 * appearance columns and V36 visibility columns are nullable: an absent value reads back as the matching
 * {@link Appearance} default (or a static interval), and as {@link Visibility#everyone()}, so a pre-V35 row
 * keeps its current look and a pre-V36 row stays visible to everyone. The V37 type columns are likewise
 * nullable: a NULL type reads back as {@link HologramType#TEXT}, so a pre-V37 row keeps rendering its lines.
 * The click-action chain lives in the child {@code hologram_action} table (V60) and is passed in already ordered
 *: each row's {@code click_trigger}/{@code type} are the enum names and {@code value} the raw operator payload.
 * This class is the single place that translation lives.
 */
final class HologramRows {

    private static final Holograms HOLOGRAMS = Holograms.HOLOGRAMS;

    private HologramRows() {}

    /**
     * Rebuild a {@link Hologram} from a name row, its already-ordered page-0 line texts, the extra pages 1..n
     * (each an already-ordered list of line texts, in page order) and its already-ordered click-action rows. A
     * hologram with no extra pages reads back as an ordinary single-page hologram, and one with no action rows
     * reads back with an empty action chain.
     */
    static Hologram toHologram(
            Record row,
            List<String> orderedLineTexts,
            List<List<String>> extraPageTexts,
            List<ClickAction> orderedActions) {
        WorldRef world = new WorldRef(UUID.fromString(row.get(HOLOGRAMS.WORLD)), row.get(HOLOGRAMS.WORLD_NAME));
        Position position = new Position(
                world,
                row.get(HOLOGRAMS.X),
                row.get(HOLOGRAMS.Y),
                row.get(HOLOGRAMS.Z),
                row.get(HOLOGRAMS.YAW),
                row.get(HOLOGRAMS.PITCH));
        List<HologramLine> lines =
                orderedLineTexts.stream().map(HologramLine::new).toList();
        Hologram hologram = new Hologram(
                HologramName.of(row.get(HOLOGRAMS.NAME)),
                position,
                typeOf(row.get(HOLOGRAMS.TYPE)),
                lines,
                row.get(HOLOGRAMS.ITEM_MATERIAL),
                row.get(HOLOGRAMS.BLOCK_DATA),
                row.get(HOLOGRAMS.HEAD_TEXTURE),
                row.get(HOLOGRAMS.ENTITY_TYPE),
                appearanceOf(row),
                visibilityOf(row),
                rotationOf(row),
                intOr(row.get(HOLOGRAMS.REFRESH_INTERVAL_TICKS), Hologram.STATIC),
                Instant.ofEpochMilli(row.get(HOLOGRAMS.CREATED_AT)));
        // A pre-V50 row (NULL linked_npc_name), and any row that never linked, reads back unlinked, so an
        // existing hologram keeps anchoring to its own coordinates with no data migration. The V54 click command
        // is layered on the same way: a pre-V54 / never-clickable row (NULL) reads back without a click action.
        // The V57 extra pages and the V60 action chain are applied last: a row with no hologram_pages rows stays a
        // single-page hologram, and a row with no hologram_action rows reads back with an empty action chain.
        return actionsOf(
                pagesOf(
                        growUpOf(row, leaderboardOf(row, clickCommandOf(row, linkedNpcOf(row, hologram)))),
                        extraPageTexts),
                orderedActions);
    }

    private static Hologram actionsOf(Hologram hologram, List<ClickAction> orderedActions) {
        Hologram withActions = hologram;
        for (ClickAction action : orderedActions) {
            withActions = withActions.withActionAdded(action);
        }
        return withActions;
    }

    /**
     * Build a domain {@link ClickAction} from a stored row's trigger/type/value, or {@code null} when the trigger
     * or type enum name no longer parses (a forward-incompatible row is skipped on load rather than crashing the
     * whole hologram set). The caller filters the nulls out. Mirrors the NPC action read.
     */
    static @Nullable ClickAction toAction(String trigger, String type, String value) {
        ClickTrigger clickTrigger = enumOrNull(ClickTrigger.class, trigger);
        ClickActionType actionType = enumOrNull(ClickActionType.class, type);
        if (clickTrigger == null || actionType == null) {
            return null;
        }
        return new ClickAction(clickTrigger, actionType, value);
    }

    private static <E extends Enum<E>> @Nullable E enumOrNull(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static Hologram growUpOf(Record row, Hologram hologram) {
        // A pre-V58 row (NULL grow_up) reads back growing downward, the existing layout.
        Short stored = row.get(HOLOGRAMS.GROW_UP);
        return stored != null && stored != 0 ? hologram.withGrowUp(true) : hologram;
    }

    private static Hologram pagesOf(Hologram hologram, List<List<String>> extraPageTexts) {
        // Pages are a text-hologram feature, so page 0 always has at least one line; guard the empty edge so a
        // hand-edited item/block row that somehow carries page rows reads back single-page rather than throwing.
        if (extraPageTexts.isEmpty() || hologram.lines().isEmpty()) {
            return hologram;
        }
        List<HologramPage> pages = new ArrayList<>();
        pages.add(HologramPage.of(hologram.lines()));
        for (List<String> pageTexts : extraPageTexts) {
            pages.add(HologramPage.of(pageTexts.stream().map(HologramLine::new).toList()));
        }
        return hologram.withPages(pages);
    }

    private static Hologram linkedNpcOf(Record row, Hologram hologram) {
        String linkedNpcName = row.get(HOLOGRAMS.LINKED_NPC_NAME);
        return linkedNpcName == null ? hologram : hologram.linkedTo(linkedNpcName);
    }

    private static Hologram clickCommandOf(Record row, Hologram hologram) {
        String clickCommand = row.get(HOLOGRAMS.CLICK_COMMAND);
        return clickCommand == null ? hologram : hologram.withClickCommand(clickCommand);
    }

    private static Hologram leaderboardOf(Record row, Hologram hologram) {
        String provider = row.get(HOLOGRAMS.LEADERBOARD_PROVIDER);
        if (provider == null) {
            return hologram;
        }
        // Clamp a stored limit into the spec's accepted range so a hand-edited row never throws on load.
        int limit = Math.min(LeaderboardSpec.MAX_LIMIT, Math.max(1, intOr(row.get(HOLOGRAMS.LEADERBOARD_LIMIT), 10)));
        return hologram.withLeaderboard(new LeaderboardSpec(provider, limit));
    }

    /** Populate a {@link HologramsRecord} from a domain {@link Hologram} for an upsert (the name row only). */
    static void apply(HologramsRecord record, Hologram hologram) {
        Position location = hologram.location();
        Appearance appearance = hologram.appearance();
        Visibility visibility = hologram.visibility();
        Rotation rotation = hologram.rotation();
        record.setName(hologram.name().value())
                .setType(hologram.type().name())
                .setItemMaterial(hologram.itemMaterial())
                .setBlockData(hologram.blockData())
                .setHeadTexture(hologram.headTexture())
                .setEntityType(hologram.entityType())
                .setWorld(location.world().uid().toString())
                .setWorldName(location.world().name())
                .setX(location.x())
                .setY(location.y())
                .setZ(location.z())
                .setYaw(location.yaw())
                .setPitch(location.pitch())
                .setCreatedAt(hologram.createdAt().toEpochMilli())
                .setBillboard(appearance.billboard().name())
                .setBackgroundArgb(appearance.hasBackground() ? appearance.backgroundArgb() : null)
                .setTextShadow((short) (appearance.textShadow() ? 1 : 0))
                .setBrightnessBlock(brightnessColumn(appearance.brightnessBlock()))
                .setBrightnessSky(brightnessColumn(appearance.brightnessSky()))
                .setScale(appearance.scale())
                .setScaleY(appearance.transform().scaleY())
                .setScaleZ(appearance.transform().scaleZ())
                .setTranslationX(appearance.transform().translationX())
                .setTranslationY(appearance.transform().translationY())
                .setTranslationZ(appearance.transform().translationZ())
                .setSeeThrough((short) (appearance.seeThrough() ? 1 : 0))
                .setTextAlignment(appearance.alignment().name())
                .setShadowRadius(shadowColumn(appearance.shadowRadius()))
                .setShadowStrength(shadowColumn(appearance.shadowStrength()))
                .setGlowArgb(appearance.hasGlow() ? appearance.glowArgb() : null)
                .setTextOpacity(appearance.hasTextOpacity() ? appearance.textOpacity() : null)
                .setLineWidth(appearance.lineWidth())
                .setViewRange(appearance.viewRange())
                .setVisibilityMode(visibility.mode().name())
                .setVisibilityPermission(visibility.permission())
                .setVisibilityDistance(visibility.distance())
                .setRotationYaw(rotation.yaw())
                .setRotationPitch(rotation.pitch())
                .setRefreshIntervalTicks(hologram.refreshIntervalTicks())
                .setLinkedNpcName(hologram.linkedNpcName())
                .setClickCommand(hologram.clickCommand())
                .setLeaderboardProvider(
                        hologram.leaderboard() == null
                                ? null
                                : hologram.leaderboard().providerId())
                .setLeaderboardLimit(
                        hologram.leaderboard() == null
                                ? null
                                : hologram.leaderboard().limit())
                .setGrowUp((short) (hologram.growUp() ? 1 : 0));
    }

    private static Appearance appearanceOf(Record row) {
        Appearance defaults = Appearance.defaults();
        // The V35 uniform scale is the X axis and the per-axis fallback: a pre-V48 row (NULL scale_y/z) reads
        // back as a uniform scale, so an existing scaled hologram keeps its size on every axis.
        float scaleX = floatOr(row.get(HOLOGRAMS.SCALE), defaults.scale());
        Transform transform = new Transform(
                floatOr(row.get(HOLOGRAMS.TRANSLATION_X), 0f),
                floatOr(row.get(HOLOGRAMS.TRANSLATION_Y), 0f),
                floatOr(row.get(HOLOGRAMS.TRANSLATION_Z), 0f),
                scaleX,
                floatOr(row.get(HOLOGRAMS.SCALE_Y), scaleX),
                floatOr(row.get(HOLOGRAMS.SCALE_Z), scaleX));
        return new Appearance(
                billboardOf(row.get(HOLOGRAMS.BILLBOARD)),
                intOr(row.get(HOLOGRAMS.BACKGROUND_ARGB), Appearance.DEFAULT_BACKGROUND),
                shadowOf(row.get(HOLOGRAMS.TEXT_SHADOW)),
                brightnessOf(row.get(HOLOGRAMS.BRIGHTNESS_BLOCK)),
                brightnessOf(row.get(HOLOGRAMS.BRIGHTNESS_SKY)),
                transform,
                intOr(row.get(HOLOGRAMS.LINE_WIDTH), defaults.lineWidth()),
                floatOr(row.get(HOLOGRAMS.VIEW_RANGE), defaults.viewRange()),
                shadowOf(row.get(HOLOGRAMS.SEE_THROUGH)),
                alignmentOf(row.get(HOLOGRAMS.TEXT_ALIGNMENT)),
                shadowValueOf(row.get(HOLOGRAMS.SHADOW_RADIUS)),
                shadowValueOf(row.get(HOLOGRAMS.SHADOW_STRENGTH)),
                intOr(row.get(HOLOGRAMS.GLOW_ARGB), Appearance.DEFAULT_GLOW),
                intOr(row.get(HOLOGRAMS.TEXT_OPACITY), Appearance.DEFAULT_OPACITY));
    }

    private static TextAlignment alignmentOf(@Nullable String stored) {
        // A pre-V48 row (NULL alignment), and any unknown token, reads back as CENTER, so a row never resolves
        // to an invalid alignment and an existing hologram keeps its centred lines.
        if (stored == null) {
            return TextAlignment.CENTER;
        }
        return TextAlignment.parse(stored).orElse(TextAlignment.CENTER);
    }

    private static float shadowValueOf(@Nullable Float stored) {
        return stored == null ? Appearance.DEFAULT_SHADOW : stored;
    }

    private static @Nullable Float shadowColumn(float shadow) {
        return Appearance.isDefaultShadow(shadow) ? null : shadow;
    }

    private static Visibility visibilityOf(Record row) {
        String mode = row.get(HOLOGRAMS.VISIBILITY_MODE);
        String permission = row.get(HOLOGRAMS.VISIBILITY_PERMISSION);
        int distance = intOr(row.get(HOLOGRAMS.VISIBILITY_DISTANCE), Visibility.UNLIMITED);
        // A pre-V36 row (NULL mode), and a PERMISSION mode that somehow lost its node, both read back as
        // "visible to everyone", so a row never resolves to an invalid Visibility.
        if ("PERMISSION".equalsIgnoreCase(mode) && permission != null && !permission.isBlank()) {
            return new Visibility(Visibility.Mode.PERMISSION, permission, distance);
        }
        if ("MANUAL".equalsIgnoreCase(mode)) {
            return new Visibility(Visibility.Mode.MANUAL, null, distance);
        }
        return new Visibility(Visibility.Mode.ALL, null, distance);
    }

    private static Rotation rotationOf(Record row) {
        // The V43 columns are NOT NULL DEFAULT 0, so an existing pre-V43 row reads back as 0/0 = Rotation.NONE.
        return Rotation.of(
                floatOr(row.get(HOLOGRAMS.ROTATION_YAW), 0f), floatOr(row.get(HOLOGRAMS.ROTATION_PITCH), 0f));
    }

    private static HologramType typeOf(@Nullable String stored) {
        // A pre-V37 row (NULL type), and any unknown token, reads back as TEXT, so a row never resolves to
        // an invalid type and an existing hologram keeps rendering its lines.
        return HologramType.parse(stored).orElse(HologramType.TEXT);
    }

    private static Billboard billboardOf(@Nullable String stored) {
        if (stored == null) {
            return Billboard.CENTER;
        }
        return Billboard.parse(stored.toUpperCase(Locale.ROOT)).orElse(Billboard.CENTER);
    }

    private static boolean shadowOf(@Nullable Short stored) {
        return stored != null && stored != 0;
    }

    private static int brightnessOf(@Nullable Integer stored) {
        return stored == null ? Appearance.DEFAULT_BRIGHTNESS : stored;
    }

    private static @Nullable Integer brightnessColumn(int brightness) {
        return Appearance.isDefaultBrightness(brightness) ? null : brightness;
    }

    private static int intOr(@Nullable Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static float floatOr(@Nullable Float value, float fallback) {
        return value == null ? fallback : value;
    }
}
