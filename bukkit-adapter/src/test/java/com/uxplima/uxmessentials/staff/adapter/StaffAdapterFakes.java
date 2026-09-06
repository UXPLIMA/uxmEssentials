package com.uxplima.uxmessentials.staff.adapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.port.StaffFreeze;
import com.uxplima.uxmessentials.staff.application.port.StaffLoadoutRepository;
import com.uxplima.uxmessentials.staff.application.port.StaffTeleport;
import com.uxplima.uxmessentials.staff.application.port.StaffVanish;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;

/**
 * Shared in-memory fakes for the staff adapter MockBukkit tests: a no-op logger and an all-defaults config (so
 * {@link StaffSettings} parses its shipped defaults), an ordered-call recording loadout repository (the
 * item-loss-safe ordering assertions read its log), a recording vanish, an echoing notifier, and a recording
 * event publisher.
 */
final class StaffAdapterFakes {

    private StaffAdapterFakes() {}

    /** A {@link StaffSettings} parsed from all-default config: the shipped vanish+examine gadget hotbar. */
    static StaffSettings defaultSettings() {
        return new StaffSettings(new DefaultConfig(), new NoopLogger());
    }

    /** A logger that discards everything. */
    static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** A config that always returns the caller's fallback, so a settings parse sees its shipped defaults. */
    static final class DefaultConfig implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    /** A loadout repository that records the order of its save/load/delete calls for the ordering assertions. */
    static final class RecordingRepository implements StaffLoadoutRepository {
        final List<String> calls = new ArrayList<>();
        final Map<UUID, SavedLoadout> rows = new HashMap<>();

        @Override
        public void save(UUID owner, SavedLoadout loadout) {
            calls.add("save");
            rows.put(owner, loadout);
        }

        @Override
        public Optional<SavedLoadout> load(UUID owner) {
            calls.add("load");
            return Optional.ofNullable(rows.get(owner));
        }

        @Override
        public void delete(UUID owner) {
            calls.add("delete");
            rows.remove(owner);
        }
    }

    /** A vanish that records the absolute states it is asked to set and reports a settable current state. */
    static final class RecordingVanish implements StaffVanish {
        final List<Boolean> states = new ArrayList<>();
        boolean vanished = false;

        @Override
        public void setVanished(PlayerRef who, boolean vanished) {
            this.vanished = vanished;
            states.add(vanished);
        }

        @Override
        public boolean isVanished(PlayerRef who) {
            return vanished;
        }
    }

    /** A freeze that records the toggles it was asked for and returns a settable outcome. */
    static final class RecordingFreeze implements StaffFreeze {
        final List<PlayerRef> toggled = new ArrayList<>();
        FreezeOutcome outcome = FreezeOutcome.FROZEN;
        boolean frozen = false;

        @Override
        public FreezeOutcome toggle(PlayerRef actor, PlayerRef target) {
            toggled.add(target);
            return outcome;
        }

        @Override
        public boolean isFrozen(PlayerRef target) {
            return frozen;
        }
    }

    /** A teleport that records the targets it was asked to send to and returns a settable result. */
    static final class RecordingTeleport implements StaffTeleport {
        final List<PlayerRef> targets = new ArrayList<>();
        boolean ok = true;

        @Override
        public boolean teleportTo(PlayerRef staff, PlayerRef target) {
            targets.add(target);
            return ok;
        }
    }

    /** A notifier over echoing ports, so a test could read the keys sent (unused assertions aside). */
    static Notifier notifier() {
        return new Notifier(new EchoMessages(), new SilentSink());
    }

    /** A notifier whose delivered, rendered lines a test can read (key plus folded placeholders). */
    static Notifier notifier(StaffKeySink sink) {
        return new Notifier(new EchoMessages(), sink);
    }

    /** An event publisher that collects what it published. */
    static final class RecordingEvents implements DomainEventPublisher {
        final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    static final class EchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            // Echo the catalog key plus every placeholder value, so a test can assert the player + message were
            // folded in (the real catalog format has no literal {player}/{message} tokens to substitute into).
            StringBuilder rendered = new StringBuilder(key.key());
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                rendered.append('|').append(entry.getKey()).append('=').append(entry.getValue());
            }
            return rendered.toString();
        }
    }

    static final class SilentSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // Discard: the adapter tests assert on inventory/repository state, not on feedback text.
        }
    }

    static final class StaffKeySink implements MessageSink {
        final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }
}
