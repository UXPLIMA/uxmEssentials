package com.uxplima.uxmessentials.migration.convert.litebans.parse;

import java.util.Objects;
import java.util.Optional;

/**
 * One raw row read from a LiteBans punishment table ({@code litebans_bans}, {@code litebans_mutes},
 * {@code litebans_warnings}). It carries the columns the importer reads, untranslated, a parse-layer value
 * with no domain meaning yet, exactly as {@code EssX*} records carry untranslated YAML. The three tables
 * share their column set; {@code warned} is meaningful only for warnings (empty elsewhere). Mapping these
 * columns to {@code :core} sanction types is the {@code map/} layer's job.
 *
 * <p>Column semantics, from the imported ban table's schema:
 * {@code uuid} is the target's UUID as text (sentinels {@code #offline#}/{@code #undefined#} possible),
 * {@code time} the creation epoch in milliseconds, {@code until} the expiry epoch in milliseconds where a
 * value {@code <= 0} means permanent, {@code ipban}/{@code ipbanWildcard}/{@code active}/{@code silent} the
 * BIT discriminators, and {@code bannedByUuid} the literal {@code CONSOLE} for a console-issued punishment.
 *
 * @param uuid the target UUID column, verbatim (may be a sentinel)
 * @param ip the recorded IP, if any
 * @param reason the free-text reason, if any
 * @param bannedByUuid the issuer UUID text or the literal {@code CONSOLE}
 * @param bannedByName the issuer display name, if any
 * @param time the creation epoch in milliseconds
 * @param until the expiry epoch in milliseconds ({@code <= 0} is permanent)
 * @param ipban true when this row is an IP ban rather than a UUID ban
 * @param ipbanWildcard true for a wildcard IP ban (range) the importer skips
 * @param active false once the punishment has been manually removed
 * @param warned the warnings-table {@code warned} flag, empty for bans/mutes
 */
public record LiteBansRow(
        String uuid,
        Optional<String> ip,
        Optional<String> reason,
        String bannedByUuid,
        Optional<String> bannedByName,
        long time,
        long until,
        boolean ipban,
        boolean ipbanWildcard,
        boolean active,
        Optional<Boolean> warned) {

    public LiteBansRow {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(ip, "ip");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(bannedByUuid, "bannedByUuid");
        Objects.requireNonNull(bannedByName, "bannedByName");
        Objects.requireNonNull(warned, "warned");
    }

    /** True when the expiry column marks a permanent punishment ({@code until <= 0}, LiteBans' convention). */
    public boolean isPermanent() {
        return until <= 0L;
    }
}
