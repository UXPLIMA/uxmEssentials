package com.uxplima.uxmessentials.kits.adapter.outbound;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.uxmessentials.kits.application.port.KitStockStore;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * A file-backed {@link KitStockStore}. The global per-kit claim tally lives in a single
 * {@code modules/kits/stock.properties} file, one {@code <kit-id>=<count>} line per stock-limited kit. Kits are
 * deliberately DB-free, so this global counter (which has no per-player holder a PDC could hang off) is kept on
 * disk rather than in the relational store.
 *
 * <p>The live counts are held in a {@link ConcurrentHashMap}; reservation is atomic through {@link Map#compute},
 * so two concurrent claims can never both push the count past the cap. Every change is flushed off the claim
 * thread through the kernel {@link Scheduler} (a full snapshot written to a uniquely-named temp file then
 * atomically moved over the target, so a flush never touches the file under a lock and never leaves a half-written
 * file); a write failure is logged, never swallowed. The file is read once on construction.
 */
@NullMarked
public final class FileKitStockStore implements KitStockStore {

    private final Map<String, Integer> counts = new ConcurrentHashMap<>();
    private final AtomicLong writeSequence = new AtomicLong();
    private final Scheduler scheduler;
    private final Logger log;
    private final Path file;

    public FileKitStockStore(Scheduler scheduler, Logger log, Path file) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.file = Objects.requireNonNull(file, "file");
        load();
    }

    @Override
    public boolean tryConsume(KitId kit, int limit) {
        Objects.requireNonNull(kit, "kit");
        if (limit <= 0) {
            return true;
        }
        boolean[] consumed = {false};
        counts.compute(kit.value(), (id, current) -> {
            int now = current == null ? 0 : current;
            if (now < limit) {
                consumed[0] = true;
                return now + 1;
            }
            return now;
        });
        if (consumed[0]) {
            persist();
        }
        return consumed[0];
    }

    @Override
    public void release(KitId kit) {
        Objects.requireNonNull(kit, "kit");
        boolean[] changed = {false};
        counts.computeIfPresent(kit.value(), (id, current) -> {
            if (current > 0) {
                changed[0] = true;
                return current - 1;
            }
            return current;
        });
        if (changed[0]) {
            persist();
        }
    }

    @Override
    public long claimed(KitId kit) {
        Objects.requireNonNull(kit, "kit");
        return counts.getOrDefault(kit.value(), 0);
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                parseLine(line);
            }
        } catch (IOException failure) {
            log.error("Failed to read the kit stock file " + file, failure);
        }
    }

    private void parseLine(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return;
        }
        int separator = trimmed.lastIndexOf('=');
        if (separator <= 0 || separator == trimmed.length() - 1) {
            return;
        }
        String id = trimmed.substring(0, separator).strip();
        try {
            int count = Integer.parseInt(trimmed.substring(separator + 1).strip());
            if (!id.isEmpty() && count > 0) {
                counts.put(id, count);
            }
        } catch (NumberFormatException notANumber) {
            // A malformed count line is skipped rather than failing the whole load.
        }
    }

    private void persist() {
        Map<String, Integer> snapshot = new TreeMap<>(counts);
        scheduler.async(() -> writeSnapshot(snapshot));
    }

    private void writeSnapshot(Map<String, Integer> snapshot) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, Integer> entry : snapshot.entrySet()) {
            body.append(entry.getKey()).append('=').append(entry.getValue()).append(System.lineSeparator());
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = file.resolveSibling(file.getFileName() + "." + writeSequence.incrementAndGet() + ".tmp");
            Files.writeString(temp, body.toString(), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            log.error("Failed to write the kit stock file " + file, failure);
        }
    }
}
