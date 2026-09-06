package com.uxplima.uxmessentials.persistence.discordlink;

import static com.uxplima.uxmessentials.persistence.jooq.tables.DiscordLinkPending.DISCORD_LINK_PENDING;
import static com.uxplima.uxmessentials.persistence.jooq.tables.DiscordLinks.DISCORD_LINKS;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.discordlink.application.port.DiscordLinkStore;
import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.DiscordId;
import com.uxplima.uxmessentials.discordlink.domain.LinkCode;
import com.uxplima.uxmessentials.discordlink.domain.PendingLink;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link DiscordLinkStore} over the generated {@code DISCORD_LINK_PENDING} and
 * {@code DISCORD_LINKS} tables. A pending code is one row per player keyed on the player uuid, so
 * {@code savePending} upserts on that key. A fresh {@code /discordlink} replaces the player's previous code in
 * place. A confirmation upserts the binding and deletes the player's pending row in a single transaction so a
 * redeemed code is never redeemable twice. Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 *
 * <p>The store carries no Caffeine cache: linking is a rare, low-traffic operation, so a read-through cache
 * would add invalidation surface for no benefit. This matches the un-cached jail-location store rather than
 * the per-owner warp/home repositories.
 */
@NullMarked
public final class JooqDiscordLinkStore extends JooqRepository implements DiscordLinkStore {

    public JooqDiscordLinkStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void savePending(PendingLink pending) {
        Objects.requireNonNull(pending, "pending");
        write(dsl -> {
            upsertPending(dsl, pending);
            return null;
        });
    }

    @Override
    public Optional<PendingLink> findPendingByCode(LinkCode code) {
        Objects.requireNonNull(code, "code");
        return read(dsl -> dsl.selectFrom(DISCORD_LINK_PENDING)
                .where(DISCORD_LINK_PENDING.CODE.eq(code.value()))
                .fetchOptional()
                .map(DiscordLinkRows::toPending));
    }

    @Override
    public void deletePending(UUID player) {
        Objects.requireNonNull(player, "player");
        write(dsl -> dsl.deleteFrom(DISCORD_LINK_PENDING)
                .where(DISCORD_LINK_PENDING.PLAYER.eq(player.toString()))
                .execute());
    }

    @Override
    public void confirm(ConfirmedLink link) {
        Objects.requireNonNull(link, "link");
        write(dsl -> {
            upsertLink(dsl, link);
            dsl.deleteFrom(DISCORD_LINK_PENDING)
                    .where(DISCORD_LINK_PENDING.PLAYER.eq(link.player().toString()))
                    .execute();
            return null;
        });
    }

    @Override
    public Optional<ConfirmedLink> findByPlayer(UUID player) {
        Objects.requireNonNull(player, "player");
        return read(dsl -> dsl.selectFrom(DISCORD_LINKS)
                .where(DISCORD_LINKS.PLAYER.eq(player.toString()))
                .fetchOptional()
                .map(DiscordLinkRows::toConfirmed));
    }

    @Override
    public Optional<ConfirmedLink> findByDiscordId(DiscordId discordId) {
        Objects.requireNonNull(discordId, "discordId");
        return read(dsl -> dsl.selectFrom(DISCORD_LINKS)
                .where(DISCORD_LINKS.DISCORD_ID.eq(discordId.value()))
                .fetchOptional()
                .map(DiscordLinkRows::toConfirmed));
    }

    @Override
    public boolean unlink(UUID player) {
        Objects.requireNonNull(player, "player");
        return write(dsl -> dsl.deleteFrom(DISCORD_LINKS)
                        .where(DISCORD_LINKS.PLAYER.eq(player.toString()))
                        .execute()
                > 0);
    }

    private static void upsertPending(DSLContext dsl, PendingLink pending) {
        dsl.insertInto(DISCORD_LINK_PENDING)
                .set(DISCORD_LINK_PENDING.PLAYER, pending.player().toString())
                .set(DISCORD_LINK_PENDING.CODE, pending.code().value())
                .set(DISCORD_LINK_PENDING.EXPIRES_AT, pending.expiresAt().toEpochMilli())
                .onConflict(DISCORD_LINK_PENDING.PLAYER)
                .doUpdate()
                .set(DISCORD_LINK_PENDING.CODE, pending.code().value())
                .set(DISCORD_LINK_PENDING.EXPIRES_AT, pending.expiresAt().toEpochMilli())
                .execute();
    }

    private static void upsertLink(DSLContext dsl, ConfirmedLink link) {
        dsl.insertInto(DISCORD_LINKS)
                .set(DISCORD_LINKS.PLAYER, link.player().toString())
                .set(DISCORD_LINKS.DISCORD_ID, link.discordId().value())
                .set(DISCORD_LINKS.LINKED_AT, link.linkedAt().toEpochMilli())
                .onConflict(DISCORD_LINKS.PLAYER)
                .doUpdate()
                .set(DISCORD_LINKS.DISCORD_ID, link.discordId().value())
                .set(DISCORD_LINKS.LINKED_AT, link.linkedAt().toEpochMilli())
                .execute();
    }
}
