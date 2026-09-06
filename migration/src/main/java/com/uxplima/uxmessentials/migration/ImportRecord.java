package com.uxplima.uxmessentials.migration;

import java.util.Objects;

import com.uxplima.uxmessentials.migration.convert.map.ImportedHologram;
import com.uxplima.uxmessentials.migration.convert.map.ImportedKit;
import com.uxplima.uxmessentials.migration.convert.map.ImportedModeration;
import com.uxplima.uxmessentials.migration.convert.map.ImportedPlayerWarp;
import com.uxplima.uxmessentials.migration.convert.map.ImportedUser;
import com.uxplima.uxmessentials.migration.convert.map.ImportedWarp;
import com.uxplima.uxmessentials.migration.convert.map.ImportedWorld;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * One mapped record streamed out of a source's {@link com.uxplima.uxmessentials.migration.convert.Convert#plan
 * plan}, ready for the writer. A sealed family over the importable kinds (user, warp, kit, moderation, ban,
 * IP ban, warning, hologram, world) so the writer and the dry-run accumulator dispatch exhaustively with no default branch
 * adding a kind is a compile error until every site handles it. Every kind carries an already-mapped domain
 * aggregate; the record itself is platform-neutral and free of any foreign-format type. Mutes and jails ride
 * in {@link ModerationRecord}; bans, IP bans and warnings are their own kinds carrying the moderation
 * context's own sanction types, so a DB-backed source (LiteBans) can build them without an EssentialsX
 * wrapper.
 */
@NullMarked
public sealed interface ImportRecord {

    /** A coarse kind label for audit attribution and summary counting. */
    String kind();

    /** A mapped player: homes, balance, mailbox. */
    record UserRecord(ImportedUser user) implements ImportRecord {
        @Override
        public String kind() {
            return "user";
        }
    }

    /** A mapped server warp. */
    record WarpRecord(ImportedWarp warp) implements ImportRecord {
        @Override
        public String kind() {
            return "warp";
        }
    }

    /**
     * A mapped player-owned warp. The shape the player-warp importers (AxPlayerWarps, Athelion, Olzie) all
     * stream. Unlike {@link WarpRecord} it carries the pre-aggregate {@link ImportedPlayerWarp}, since the writer
     * still has to sanitise and globally de-collide the name and hash the password before an aggregate exists.
     */
    record PlayerWarpRecord(ImportedPlayerWarp warp) implements ImportRecord {
        public PlayerWarpRecord {
            Objects.requireNonNull(warp, "warp");
        }

        @Override
        public String kind() {
            return "player-warp";
        }
    }

    /** A mapped kit definition. */
    record KitRecord(ImportedKit kit) implements ImportRecord {
        @Override
        public String kind() {
            return "kit";
        }
    }

    /** A mapped player sanction state: mute and/or jail, as a {@code ModerationProfile}. */
    record ModerationRecord(ImportedModeration moderation) implements ImportRecord {
        @Override
        public String kind() {
            return "moderation";
        }
    }

    /**
     * A mapped UUID ban: permanent or timed. A permanent ban is a {@link TempbanState.Active} whose expiry
     * is a far-future sentinel ({@code Ban.PERMANENT_SPAN}), exactly the row the live {@code /ban} writes,
     * so the importer never needs a distinct permanent shape.
     */
    record BanRecord(PlayerRef target, TempbanState ban) implements ImportRecord {
        public BanRecord {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(ban, "ban");
        }

        @Override
        public String kind() {
            return "ban";
        }
    }

    /** A mapped IP ban, keyed by address: permanent or timed, with the banned UUID recorded when known. */
    record IpBanRecord(IpBan ban) implements ImportRecord {
        public IpBanRecord {
            Objects.requireNonNull(ban, "ban");
        }

        @Override
        public String kind() {
            return "ip-ban";
        }
    }

    /** A mapped warning appended to the target's append-only warning history: standing or timed. */
    record WarnRecord(PlayerRef target, Warn warn) implements ImportRecord {
        public WarnRecord {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(warn, "warn");
        }

        @Override
        public String kind() {
            return "warn";
        }
    }

    /** A mapped world registry entry, keyed by its folder name. */
    record WorldRecord(ImportedWorld world) implements ImportRecord {
        public WorldRecord {
            Objects.requireNonNull(world, "world");
        }

        @Override
        public String kind() {
            return "world";
        }
    }

    /** A mapped server-wide hologram, keyed by its name. */
    record HologramRecord(ImportedHologram hologram) implements ImportRecord {
        public HologramRecord {
            Objects.requireNonNull(hologram, "hologram");
        }

        @Override
        public String kind() {
            return "hologram";
        }
    }
}
