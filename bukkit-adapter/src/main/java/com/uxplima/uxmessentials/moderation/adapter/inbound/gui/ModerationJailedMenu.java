package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.Unjail;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.JailEntry;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the jailed-players release list (capability C of the {@code /jail} GUI, also the {@code /jailedplayers}
 * bare-root opener) with the menu engine and opens it. A paginated grid of one PLAYER_HEAD per currently-jailed
 * player showing the target, the jail, the issuer, the reason and the remaining time. A left click releases that
 * player through the same audited {@code Unjail} use case {@code /unjail} takes, then reopens the refreshed list so
 * the released row disappears.
 *
 * <p>The active-jail read is a DB query, and each target's display name resolves through the {@link PlayerLookup}
 * and each remaining time against the {@link Clock}: none of which belongs in an off-thread list source. So
 * {@link #open} runs the read off the tick thread, resolves every name and remaining string there, and hands the
 * fully-resolved rows in as the menu subject; the {@code moderation:jailed} list source only reads that subject.
 * The {@code mod_jailed_player} / {@code mod_jailed_jail} / {@code mod_jailed_issuer} / {@code mod_jailed_reason} /
 * {@code mod_jailed_remaining} placeholders fill each head from the bound row, prefixed so they never collide with
 * the history-list tokens that carry the same field names. A {@code moderation:release} left action runs the unjail
 * and reopens. Every label resolves from the moderation catalog, so no user-facing text lives here. The geometry
 * mirrors the original paginated list: a grid across the top five rows and the two nav arrows on the bottom row.
 */
@NullMarked
public final class ModerationJailedMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "moderation-jailed";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/moderation/gui/moderation-jailed.conf";

    /** Mirrors the per-list page cap the {@code /jailedplayers} read uses, so the off-thread read stays bounded. */
    private static final int PAGE_LIMIT = 50;

    private final Menus menus;
    private final Scheduler scheduler;
    private final Unjail unjail;
    private final ModerationRepository repository;
    private final PlayerLookup players;
    private final Clock clock;

    public ModerationJailedMenu(
            Menus menus,
            Scheduler scheduler,
            Unjail unjail,
            ModerationRepository repository,
            PlayerLookup players,
            Clock clock) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.unjail = Objects.requireNonNull(unjail, "unjail");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.players = Objects.requireNonNull(players, "players");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Register the per-row list source, placeholders, and the release action the spec names, plus the spec itself. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("moderation:jailed", ctx -> subject(ctx).rows());
        bindings.placeholder("mod_jailed_player", ctx -> row(ctx).player());
        bindings.placeholder("mod_jailed_jail", ctx -> row(ctx).jail());
        bindings.placeholder("mod_jailed_issuer", ctx -> row(ctx).issuer());
        bindings.placeholder("mod_jailed_reason", ctx -> row(ctx).reason());
        bindings.placeholder("mod_jailed_remaining", ctx -> row(ctx).remaining());
        bindings.action("moderation:release", this::release);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /** Resolve the active jails (and each name and remaining string) off-thread, then open the list for {@code viewer}. */
    public void open(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        scheduler.async(() -> menus.open(viewer, SPEC_ID, snapshot()));
    }

    /** Read the active jails and resolve every display name and remaining string on the off-tick thread. */
    private JailedView snapshot() {
        List<JailEntry> active = repository.activeJails(clock.instant(), PAGE_LIMIT);
        List<JailedRow> rows = new ArrayList<>(active.size());
        for (JailEntry entry : active) {
            rows.add(new JailedRow(
                    entry.target(),
                    targetName(entry),
                    entry.jail(),
                    entry.issuer().name(),
                    entry.reason().filter(r -> !r.isBlank()).orElse(""),
                    remaining(entry)));
        }
        return new JailedView(rows);
    }

    /** Left-click a head: release the clicked target through the audited unjail use case, then reopen the refreshed list. */
    private void release(MenuActionContext ctx) {
        JailedRow row = ctx.entry(JailedRow.class);
        PlayerRef target = new PlayerRef(row.target(), row.player());
        unjail.unjail(ctx.viewer(), target);
        open(ctx.viewer());
    }

    private JailedView subject(MenuContext ctx) {
        return ctx.subject(JailedView.class);
    }

    private JailedRow row(MenuContext ctx) {
        return ctx.entry(JailedRow.class);
    }

    private String remaining(JailEntry entry) {
        if (entry.until().isEmpty() && entry.remaining().isEmpty()) {
            return "permanent";
        }
        Duration left = entry.until()
                .map(until -> Duration.between(clock.instant(), until))
                .or(entry::remaining)
                .orElse(Duration.ZERO);
        return SanctionDuration.format(left.isNegative() ? Duration.ZERO : left);
    }

    private String targetName(JailEntry entry) {
        return players.findByUuid(entry.target())
                .map(PlayerRef::name)
                .orElseGet(() -> entry.target().toString());
    }

    /**
     * The subject of an open jailed list: the already-read, already-resolved rows. The list source reads this, so
     * the menu carries no DB read or name lookup of its own once it opens.
     *
     * @param rows the jailed players, each with its display name and remaining string already resolved
     */
    public record JailedView(List<JailedRow> rows) {

        public JailedView {
            rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        }
    }

    /**
     * One jailed player, fully resolved on the off-tick thread so the menu never touches the {@link PlayerLookup}
     * or the {@link Clock} again: the target UUID (for the release use case), the display name, the jail, the
     * issuer, the reason, and the formatted remaining time.
     *
     * @param target the jailed player's UUID, passed to the unjail use case on a release click
     * @param player the display name resolved through the player lookup, or the UUID string when unknown
     * @param jail the jail the player is held in
     * @param issuer who issued the jail
     * @param reason the free-text reason, or empty when none was given
     * @param remaining the formatted remaining time, or {@code permanent} for an indefinite jail
     */
    public record JailedRow(UUID target, String player, String jail, String issuer, String reason, String remaining) {

        public JailedRow {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(jail, "jail");
            Objects.requireNonNull(issuer, "issuer");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(remaining, "remaining");
        }
    }
}
