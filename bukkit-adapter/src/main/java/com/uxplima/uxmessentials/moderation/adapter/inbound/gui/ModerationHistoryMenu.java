package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers a target's read-only disciplinary history (the same newest-first timeline the {@code /history} command
 * renders, and the detail screen's history button drills into) with the menu engine and opens it. A paginated grid
 * of one icon per {@link SanctionHistoryEntry} showing the action, the actor, the reason and the timestamp; a click
 * does nothing, because an audit row is immutable.
 *
 * <p>The history read is a DB query, so {@link #open} resolves it off the tick thread and hands the already-read,
 * already-bounded entries in as the menu subject; the {@code moderation:history} list source only reads that subject
 *: it touches no port off-thread. The {@code mod_history_action} / {@code mod_history_issuer} /
 * {@code mod_history_reason} / {@code mod_history_at} placeholders fill each icon from the bound entry, prefixed so
 * they never collide with the jailed-list tokens that carry the same field names. Every label resolves from the
 * moderation catalog, so no user-facing text lives here. The geometry mirrors the original paginated list: a grid
 * across the top five rows and the two nav arrows on the bottom row.
 */
@NullMarked
public final class ModerationHistoryMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "moderation-history";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/moderation/gui/moderation-history.conf";

    /** Mirrors the per-page cap the {@code /history} read uses, so the off-thread read stays bounded. */
    private static final int PAGE_LIMIT = 50;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Menus menus;
    private final Scheduler scheduler;
    private final SanctionHistory history;

    public ModerationHistoryMenu(Menus menus, Scheduler scheduler, SanctionHistory history) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.history = Objects.requireNonNull(history, "history");
    }

    /** Register the per-entry list source and placeholders the spec names and the spec itself; called once at wiring. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("moderation:history", ctx -> subject(ctx).entries());
        bindings.placeholder("mod_history_icon", ctx -> iconMaterial(entry(ctx)));
        bindings.placeholder("mod_history_action", ctx -> entry(ctx).action().name());
        bindings.placeholder("mod_history_issuer", ctx -> entry(ctx).actor().name());
        bindings.placeholder(
                "mod_history_reason",
                ctx -> entry(ctx).reason().filter(r -> !r.isBlank()).orElse(""));
        bindings.placeholder("mod_history_at", ctx -> STAMP.format(entry(ctx).at()));
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /** Resolve {@code target}'s history off-thread, then open the read-only list for {@code viewer}. */
    public void open(PlayerRef viewer, UUID target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        scheduler.async(() -> {
            HistoryView snapshot = new HistoryView(List.copyOf(history.recentForTarget(target, PAGE_LIMIT)));
            menus.open(viewer, SPEC_ID, snapshot);
        });
    }

    private HistoryView subject(MenuContext ctx) {
        return ctx.subject(HistoryView.class);
    }

    private SanctionHistoryEntry entry(MenuContext ctx) {
        return ctx.entry(SanctionHistoryEntry.class);
    }

    /** The per-action icon material name, mirroring the deleted view's switch so the grid renders identically. */
    private static String iconMaterial(SanctionHistoryEntry entry) {
        return switch (entry.action()) {
            case BAN -> "BARRIER";
            case UNBAN -> "LIME_DYE";
            case MUTE -> "BOOK";
            case UNMUTE -> "WRITABLE_BOOK";
            case WARN -> "PAPER";
            case KICK -> "LEATHER_BOOTS";
        };
    }

    /**
     * The subject of an open history grid: the already-read, already-bounded entries newest-first. The list source
     * reads this, so the menu carries no DB read of its own once it opens.
     *
     * @param entries the target's history rows, newest-first, capped at {@link #PAGE_LIMIT}
     */
    public record HistoryView(List<SanctionHistoryEntry> entries) {

        public HistoryView {
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }
    }
}
