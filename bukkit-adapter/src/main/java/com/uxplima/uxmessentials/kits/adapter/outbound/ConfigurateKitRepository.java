package com.uxplima.uxmessentials.kits.adapter.outbound;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The {@link KitRepository} backed by one HOCON file per kit under {@code modules/kits/kits/}: each kit lives
 * in its own {@code <id>.conf}, whose root node is the kit (cooldown, one-time, permission, cost, items).
 * Definitions are loaded once on construction into an immutable id-keyed map held in an {@link AtomicReference},
 * so reads (the hot {@code /kit} / {@code /kit list} paths) are lock-free against the snapshot they fetch. The rare
 * authoring operations ({@code /kit create}, {@code /kit del}, {@code /kit editor}) touch only the one kit's file
 * and then swap the in-memory snapshot, so the catalog and the folder never drift.
 *
 * <p>Parsing each file is delegated to {@link KitCodec}; a malformed or unreadable file is logged and skipped
 * at load so one bad kit never strands the rest. A save writes the kit to a sibling temp file and then moves
 * it into place ({@link StandardCopyOption#ATOMIC_MOVE} where the filesystem supports it), so a crash mid-write
 * never leaves a half-written kit file. Writes are serialized by {@code synchronized} on this repository
 * authoring is single-operator and rare, so the coarse lock is fine.
 *
 * <p>A pre-existing single {@code kits.conf} monolith (the previous layout) is split into per-kit files on the
 * first load and renamed to {@code kits.conf.migrated}, so existing servers keep their kits and the monolith is
 * imported exactly once.
 */
@NullMarked
public final class ConfigurateKitRepository implements KitRepository {

    private static final String SUFFIX = ".conf";

    private final Path dir;
    private final Logger log;
    private final AtomicReference<Map<String, KitDefinition>> kits;

    private ConfigurateKitRepository(Path dir, Logger log, Map<String, KitDefinition> initial) {
        this.dir = dir;
        this.log = log;
        this.kits = new AtomicReference<>(Map.copyOf(initial));
    }

    /**
     * Load every kit file under {@code dir}. If a legacy {@code legacyMonolith} {@code kits.conf} exists it is
     * split into per-kit files first and renamed aside, so an upgraded server keeps its kits.
     */
    public static ConfigurateKitRepository load(Path dir, Path legacyMonolith, Logger log) {
        Objects.requireNonNull(dir, "dir");
        Objects.requireNonNull(legacyMonolith, "legacyMonolith");
        Objects.requireNonNull(log, "log");
        migrateMonolith(dir, legacyMonolith, log);
        return new ConfigurateKitRepository(dir, log, readAll(dir, log));
    }

    @Override
    public Optional<KitDefinition> find(KitId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(current().get(id.value()));
    }

    @Override
    public List<KitDefinition> all() {
        return List.copyOf(current().values());
    }

    @Override
    public boolean exists(KitId id) {
        Objects.requireNonNull(id, "id");
        return current().containsKey(id.value());
    }

    @Override
    public synchronized void save(KitDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        try {
            writeKitFile(definition);
        } catch (IOException failure) {
            log.error("failed to save kit " + definition.id().value() + " under " + dir, failure);
            return;
        }
        Map<String, KitDefinition> next = new LinkedHashMap<>(current());
        next.put(definition.id().value(), definition);
        kits.set(Map.copyOf(next));
    }

    @Override
    public synchronized void delete(KitId id) {
        Objects.requireNonNull(id, "id");
        try {
            Files.deleteIfExists(fileFor(id.value()));
        } catch (IOException failure) {
            log.error("failed to delete kit " + id.value() + " under " + dir, failure);
            return;
        }
        Map<String, KitDefinition> next = new LinkedHashMap<>(current());
        if (next.remove(id.value()) != null) {
            kits.set(Map.copyOf(next));
        }
    }

    private Map<String, KitDefinition> current() {
        return Objects.requireNonNull(kits.get(), "kits");
    }

    private Path fileFor(String id) {
        return dir.resolve(id + SUFFIX);
    }

    private void writeKitFile(KitDefinition definition) throws IOException {
        Files.createDirectories(dir);
        Path target = fileFor(definition.id().value());
        Path temp = dir.resolve(definition.id().value() + SUFFIX + ".tmp");
        CommentedConfigurationNode root = CommentedConfigurationNode.root();
        KitCodec.write(root, definition);
        HoconConfigurationLoader.builder().path(temp).build().save(root);
        atomicMove(temp, target);
    }

    private static void atomicMove(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException atomicUnsupported) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Map<String, KitDefinition> readAll(Path dir, Logger log) {
        Map<String, KitDefinition> loaded = new LinkedHashMap<>();
        if (!Files.isDirectory(dir)) {
            return loaded;
        }
        for (Path file : sortedConfFiles(dir, log)) {
            readFile(file, log).ifPresent(kit -> loaded.put(kit.id().value(), kit));
        }
        return loaded;
    }

    private static List<Path> sortedConfFiles(Path dir, Logger log) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + SUFFIX)) {
            stream.forEach(files::add);
        } catch (IOException failure) {
            log.error("failed to list kit files under " + dir, failure);
            return List.of();
        }
        files.sort(
                (a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
        return files;
    }

    private static Optional<KitDefinition> readFile(Path file, Logger log) {
        String name = file.getFileName().toString();
        String id = name.substring(0, name.length() - SUFFIX.length());
        try {
            ConfigurationNode root =
                    HoconConfigurationLoader.builder().path(file).build().load();
            Optional<KitDefinition> kit = KitCodec.read(id, root, log);
            if (kit.isEmpty()) {
                log.warn("skipping malformed kit file: " + file);
            }
            return kit;
        } catch (ConfigurateException failure) {
            log.error("failed to read kit file " + file + "; skipping it", failure);
            return Optional.empty();
        }
    }

    private static void migrateMonolith(Path dir, Path legacyMonolith, Logger log) {
        if (!Files.isRegularFile(legacyMonolith)) {
            return;
        }
        try {
            Files.createDirectories(dir);
            int split = splitMonolith(dir, legacyMonolith, log);
            Files.move(legacyMonolith, legacyMonolith.resolveSibling(legacyMonolith.getFileName() + ".migrated"));
            log.info("split legacy kits.conf into {} per-kit file(s) under " + dir, split);
        } catch (IOException failure) {
            log.error("failed to migrate legacy kits.conf at " + legacyMonolith, failure);
        }
    }

    private static int splitMonolith(Path dir, Path legacyMonolith, Logger log) throws IOException {
        ConfigurationNode kitsNode = HoconConfigurationLoader.builder()
                .path(legacyMonolith)
                .build()
                .load()
                .node("kits");
        int split = 0;
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                kitsNode.childrenMap().entrySet()) {
            String id = String.valueOf(entry.getKey());
            Optional<KitDefinition> kit = KitCodec.read(id, entry.getValue(), log);
            if (kit.isPresent()) {
                CommentedConfigurationNode root = CommentedConfigurationNode.root();
                KitCodec.write(root, kit.get());
                HoconConfigurationLoader.builder()
                        .path(dir.resolve(kit.get().id().value() + SUFFIX))
                        .build()
                        .save(root);
                split++;
            }
        }
        return split;
    }
}
