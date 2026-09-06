package com.uxplima.uxmessentials.persistence.homes;

import static com.uxplima.uxmessentials.persistence.jooq.tables.HomeInvites.HOME_INVITES;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.homes.application.port.HomeInviteRepository;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link HomeInviteRepository} over the generated {@code home_invites} table. All
 * mutations use the {@code (owner, slot, invited)} primary key to keep invite grants idempotent, a
 * duplicate insert does nothing rather than failing. Reads return an immutable {@link Set}; ownership
 * of the guest list is with the caller's use-case.
 */
@NullMarked
public final class JooqHomeInviteRepository extends JooqRepository implements HomeInviteRepository {

    public JooqHomeInviteRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Set<UUID> invites(PlayerRef owner, HomeSlot slot) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        return read(dsl -> dsl
                .select(HOME_INVITES.INVITED)
                .from(HOME_INVITES)
                .where(HOME_INVITES.OWNER.eq(owner.uuid().toString()).and(HOME_INVITES.SLOT.eq(slot.index())))
                .fetch(HOME_INVITES.INVITED)
                .stream()
                .map(UUID::fromString)
                .collect(Collectors.toUnmodifiableSet()));
    }

    @Override
    public void addInvite(PlayerRef owner, HomeSlot slot, UUID invited) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(invited, "invited");
        write(dsl -> dsl.insertInto(HOME_INVITES)
                .set(HOME_INVITES.OWNER, owner.uuid().toString())
                .set(HOME_INVITES.SLOT, slot.index())
                .set(HOME_INVITES.INVITED, invited.toString())
                .onConflict(HOME_INVITES.OWNER, HOME_INVITES.SLOT, HOME_INVITES.INVITED)
                .doNothing()
                .execute());
    }

    @Override
    public void removeInvite(PlayerRef owner, HomeSlot slot, UUID invited) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(invited, "invited");
        write(dsl -> dsl.deleteFrom(HOME_INVITES)
                .where(HOME_INVITES
                        .OWNER
                        .eq(owner.uuid().toString())
                        .and(HOME_INVITES.SLOT.eq(slot.index()))
                        .and(HOME_INVITES.INVITED.eq(invited.toString())))
                .execute());
    }

    @Override
    public void removeAll(PlayerRef owner, HomeSlot slot) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        write(dsl -> dsl.deleteFrom(HOME_INVITES)
                .where(HOME_INVITES.OWNER.eq(owner.uuid().toString()).and(HOME_INVITES.SLOT.eq(slot.index())))
                .execute());
    }

    @Override
    public void removeAllForOwner(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        write(dsl -> dsl.deleteFrom(HOME_INVITES)
                .where(HOME_INVITES.OWNER.eq(owner.uuid().toString()))
                .execute());
    }
}
