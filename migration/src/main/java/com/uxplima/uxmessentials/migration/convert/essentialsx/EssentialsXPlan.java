package com.uxplima.uxmessentials.migration.convert.essentialsx;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.JailMuteMapper;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.KitMapper;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.UserdataMapper;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WarpMapper;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXUserdata;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.KitsParser;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.UserdataParser;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.WarpParser;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The EssentialsX source's lazy {@link ImportPlan}. It composes three sub-streams: userdata files,
 * warp files, and the kits file. Into one {@link ImportRecord} stream, each evaluated on demand as the
 * importer's bounded executor drains it (docs/12-migration §7). A malformed individual file is dropped
 * from the stream (the run continues and counts it as skipped/failed downstream), so one bad
 * {@code userdata/<uuid>.yml} never aborts the import (§4).
 */
@NullMarked
final class EssentialsXPlan implements ImportPlan {

    private final Path root;
    private final UserdataParser userdata;
    private final WarpParser warps;
    private final KitsParser kits;
    private final UserdataMapper userMapper;
    private final WarpMapper warpMapper;
    private final KitMapper kitMapper;
    private final JailMuteMapper jailMuteMapper;

    EssentialsXPlan(
            Path root,
            UserdataMapper userMapper,
            WarpMapper warpMapper,
            KitMapper kitMapper,
            JailMuteMapper jailMuteMapper) {
        this.root = Objects.requireNonNull(root, "root");
        this.userMapper = Objects.requireNonNull(userMapper, "userMapper");
        this.warpMapper = Objects.requireNonNull(warpMapper, "warpMapper");
        this.kitMapper = Objects.requireNonNull(kitMapper, "kitMapper");
        this.jailMuteMapper = Objects.requireNonNull(jailMuteMapper, "jailMuteMapper");
        this.userdata = new UserdataParser();
        this.warps = new WarpParser();
        this.kits = new KitsParser();
    }

    @Override
    public Stream<ImportRecord> records() {
        return Stream.concat(users(), Stream.concat(warpRecords(), kitRecords()));
    }

    private Stream<ImportRecord> users() {
        return ymlFiles(root.resolve("userdata")).flatMap(this::toUserRecord);
    }

    private Stream<ImportRecord> toUserRecord(Path file) {
        try {
            return userRecords(userdata.parse(file));
        } catch (IOException | RuntimeException badFile) {
            // One unreadable userdata file is skipped, never fatal (docs/12-migration §4).
            return Stream.empty();
        }
    }

    private Stream<ImportRecord> userRecords(EssXUserdata parsed) {
        // One userdata file yields the player record plus, when the player carries a mute or jail, a separate
        // moderation record: both keyed by the same uuid so a re-run upserts each idempotently (§6).
        ImportRecord user = new ImportRecord.UserRecord(userMapper.map(parsed));
        PlayerRef owner = new PlayerRef(parsed.uuid(), parsed.name());
        return jailMuteMapper
                .map(owner, parsed.moderation())
                .<ImportRecord>map(ImportRecord.ModerationRecord::new)
                .map(moderation -> Stream.of(user, moderation))
                .orElseGet(() -> Stream.of(user));
    }

    private Stream<ImportRecord> warpRecords() {
        return ymlFiles(root.resolve("warps")).flatMap(this::toWarpRecord);
    }

    private Stream<ImportRecord> toWarpRecord(Path file) {
        try {
            var parsed = warps.parse(file);
            if (parsed == null) {
                return Stream.empty();
            }
            return warpMapper.map(parsed).<ImportRecord>map(ImportRecord.WarpRecord::new).stream();
        } catch (IOException | RuntimeException badFile) {
            return Stream.empty();
        }
    }

    private Stream<ImportRecord> kitRecords() {
        Path file = root.resolve("kits.yml");
        if (!Files.isRegularFile(file)) {
            return Stream.empty();
        }
        try {
            return kits.parse(file).stream().map(kitMapper::map).map(ImportRecord.KitRecord::new);
        } catch (IOException | RuntimeException badFile) {
            return Stream.empty();
        }
    }

    private static Stream<Path> ymlFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return Stream.empty();
        }
        // The directory listing is collected eagerly inside try-with-resources so the directory handle is
        // released immediately; only the cheap path list is held. The expensive work: parsing each file's
        // content: still happens lazily as the records stream is drained downstream (docs/12-migration §7).
        try (Stream<Path> listing = Files.list(dir)) {
            return listing.filter(p -> p.getFileName().toString().endsWith(".yml")).toList().stream();
        } catch (IOException unreadableDir) {
            throw new UncheckedIOException("failed to list " + dir, unreadableDir);
        }
    }

    @Override
    public void close() {
        // Each sub-stream opens and closes its own reader per file; nothing is held open across records.
    }
}
