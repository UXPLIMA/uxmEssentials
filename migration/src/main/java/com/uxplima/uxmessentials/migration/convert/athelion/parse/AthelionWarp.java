package com.uxplima.uxmessentials.migration.convert.athelion.parse;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One warp read from Athelion's {@code data.yml}, in the source's own shape. Athelion serialises every warp as a
 * {@code ConfigurationSerializable} under a top-level {@code warps:} map keyed by the warp uuid; this record is the parsed
 * form of one such entry, before any translation to the domain. Foreign detail Athelion carries but we cannot land, the
 * serialised menu {@code item}, {@code need-verification}, {@code last-activity}, {@code featured}, is not read here; the
 * owner <em>name</em> is not serialised at all, so it is resolved to a placeholder by the mapper.
 *
 * <p>The world is a bare name (Athelion keeps no uid), so a warp whose world the live server does not know is dropped by
 * the mapper, not here. {@code ratingSum} is Athelion's accumulated star total (its {@code ratings} field) and
 * {@code reviewers} the players who cast those stars, so the per-warp count is {@code reviewers.size()} and the average is
 * {@code ratingSum / reviewers.size()}: Athelion stores no per-vote breakdown.
 *
 * @param owner the warp owner's uuid
 * @param name the warp's id-name
 * @param displayName the warp's display label (falls back to {@code name} when Athelion stored none)
 * @param description the {@code lore} blurb, if any
 * @param location the raw destination
 * @param password the plaintext password, if the warp is password-gated (never logged; hashed at write)
 * @param status the raw {@code WarpStatus} token ({@code OPENED} / {@code CLOSED} / {@code PASSWORD_PROTECTED}), if present
 * @param admission the entry fee
 * @param visits the total recorded visits
 * @param ratingSum the accumulated star total across all reviewers
 * @param reviewers the players who rated the warp
 * @param blockedPlayers the players Athelion blocked from the warp
 * @param category the raw category token, if filed under one
 * @param dateCreated the creation timestamp in epoch millis
 */
@NullMarked
public record AthelionWarp(
        UUID owner,
        String name,
        String displayName,
        @Nullable String description,
        AthelionLocation location,
        @Nullable String password,
        @Nullable String status,
        int admission,
        int visits,
        int ratingSum,
        List<UUID> reviewers,
        List<UUID> blockedPlayers,
        @Nullable String category,
        long dateCreated) {

    public AthelionWarp {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(location, "location");
        reviewers = List.copyOf(Objects.requireNonNull(reviewers, "reviewers"));
        blockedPlayers = List.copyOf(Objects.requireNonNull(blockedPlayers, "blockedPlayers"));
    }
}
