package com.uxplima.uxmessentials.moderation.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.JailDirectory;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /jails}: list the jails {@code /jail <player> <jail>} will accept. The named jails an operator
 * configured in {@code moderation.conf} merged with the DB-backed jails defined in-game with {@code /setjail}.
 * A read-only query against the directory seam. Without it an operator has to inspect both sources by hand to
 * learn the valid names. Renders a header with the count then one entry per name, or an empty notice when no
 * jails exist. The directory hands the names back sorted; because it reads the DB-backed store the adapter
 * runs this off the tick thread.
 */
public final class ListJails {

    private final JailDirectory directory;
    private final Notifier notifier;

    public ListJails(JailDirectory directory, Notifier notifier) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Render the configured jail names to {@code actor}, sorted, or the empty notice when none exist. */
    public void list(PlayerRef actor) {
        Objects.requireNonNull(actor, "actor");
        List<String> names = directory.names();
        if (names.isEmpty()) {
            notifier.send(actor, ModerationMessageKey.JAILS_EMPTY);
            return;
        }
        notifier.send(actor, ModerationMessageKey.JAILS_HEADER, Map.of("count", Integer.toString(names.size())));
        names.forEach(name -> notifier.send(actor, ModerationMessageKey.JAILS_ENTRY, Map.of("jail", name)));
    }

    /**
     * Config-only jail names for tab-completion. Reads the non-blocking config seam ({@code peekNames()}), not
     * the DB-backed union {@link #list} renders, so it is safe to call on the tick thread per keystroke.
     */
    public List<String> suggestionNames() {
        return directory.peekNames();
    }

    /**
     * The full jail-name union (config jails merged with the DB-backed {@code /setjail} jails, sorted) for the
     * management GUI's jail list to render as clickable rows. Reads {@link com.uxplima.uxmessentials.moderation.application.port.JailDirectory#names()},
     * which can touch the database, so the adapter resolves it off the tick thread exactly as {@link #list} is run.
     */
    public List<String> names() {
        return directory.names();
    }
}
