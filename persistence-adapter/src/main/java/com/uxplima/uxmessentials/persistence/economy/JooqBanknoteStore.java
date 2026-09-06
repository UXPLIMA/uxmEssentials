package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyActiveBanknotes.ECONOMY_ACTIVE_BANKNOTES;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.BanknoteStore;
import com.uxplima.uxmessentials.economy.domain.Banknote;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link BanknoteStore} over the generated {@code ECONOMY_ACTIVE_BANKNOTES} table. Every
 * statement is typed jOOQ DSL: no string-concatenated SQL. The store is the anti-dupe registry: a banknote
 * is redeemable only while its token row exists, and {@link #redeem} deletes that row, so two attempts to
 * cash a duplicated item race on the single {@code DELETE} and only the one that removes the row wins.
 *
 * <p>The credit that pays out a redeemed banknote is the caller's responsibility (the {@link BanknoteStore}
 * port returns only whether the token was valid); the wallet credit is applied in the economy adapter that
 * owns the {@code Money}, not here.
 */
@NullMarked
public final class JooqBanknoteStore extends JooqRepository implements BanknoteStore {

    public JooqBanknoteStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void register(Banknote banknote) {
        Objects.requireNonNull(banknote, "banknote");
        write(dsl -> {
            dsl.insertInto(ECONOMY_ACTIVE_BANKNOTES)
                    .set(ECONOMY_ACTIVE_BANKNOTES.TOKEN, banknote.token().toString())
                    .set(ECONOMY_ACTIVE_BANKNOTES.AMOUNT, banknote.money().amount())
                    .set(
                            ECONOMY_ACTIVE_BANKNOTES.CURRENCY,
                            banknote.money().currency().id().value())
                    .set(ECONOMY_ACTIVE_BANKNOTES.CREATED_AT, banknote.createdAt())
                    .execute();
            return null;
        });
    }

    @Override
    public boolean redeem(UUID token) {
        Objects.requireNonNull(token, "token");
        // The redeem is the single guarded DELETE: the boolean is keyed off the rows it removed, so a token
        // that was already redeemed (no row) returns false and cannot be cashed twice.
        return write(dsl -> dsl.deleteFrom(ECONOMY_ACTIVE_BANKNOTES)
                        .where(ECONOMY_ACTIVE_BANKNOTES.TOKEN.eq(token.toString()))
                        .execute()
                > 0);
    }
}
