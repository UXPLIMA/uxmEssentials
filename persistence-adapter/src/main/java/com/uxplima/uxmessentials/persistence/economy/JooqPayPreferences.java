package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyPayPreferences.ECONOMY_PAY_PREFERENCES;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.PayPreferences;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link PayPreferences} over the {@code economy_pay_preferences} table. The accept-pay flag
 * is a first-class {@code (owner, accepts_pay)} row, not a JSON blob, so it is queryable, survives relog,
 * and is read without materialising a wallet ({@code docs/11-economy-integration.md} §9.1). A player with no
 * row has never run {@code /paytoggle} and takes the configured {@code economy.pay.toggle-default}, supplied
 * to the constructor.
 *
 * <p>The flag is stored as a portable {@code SMALLINT} (0/1). A {@link #toggle} flips the current value and
 * upserts it on the owner key, returning the new state for the command feedback.
 */
@NullMarked
public final class JooqPayPreferences extends JooqRepository implements PayPreferences {

    private final boolean toggleDefault;

    public JooqPayPreferences(DSLContext dsl, boolean toggleDefault) {
        super(dsl);
        this.toggleDefault = toggleDefault;
    }

    @Override
    public boolean acceptsPay(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        return read(dsl -> dsl.select(ECONOMY_PAY_PREFERENCES.ACCEPTS_PAY)
                .from(ECONOMY_PAY_PREFERENCES)
                .where(ECONOMY_PAY_PREFERENCES.OWNER.eq(target.uuid().toString()))
                .fetchOptional(ECONOMY_PAY_PREFERENCES.ACCEPTS_PAY)
                .map(flag -> flag != 0)
                .orElse(toggleDefault));
    }

    @Override
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return write(dsl -> {
            boolean next = !currentOrDefault(dsl, who);
            upsert(dsl, who, next);
            return next;
        });
    }

    private boolean currentOrDefault(DSLContext dsl, PlayerRef who) {
        return dsl.select(ECONOMY_PAY_PREFERENCES.ACCEPTS_PAY)
                .from(ECONOMY_PAY_PREFERENCES)
                .where(ECONOMY_PAY_PREFERENCES.OWNER.eq(who.uuid().toString()))
                .fetchOptional(ECONOMY_PAY_PREFERENCES.ACCEPTS_PAY)
                .map(flag -> flag != 0)
                .orElse(toggleDefault);
    }

    private void upsert(DSLContext dsl, PlayerRef who, boolean accepts) {
        short value = (short) (accepts ? 1 : 0);
        dsl.insertInto(ECONOMY_PAY_PREFERENCES)
                .set(ECONOMY_PAY_PREFERENCES.OWNER, who.uuid().toString())
                .set(ECONOMY_PAY_PREFERENCES.ACCEPTS_PAY, value)
                .onConflict(ECONOMY_PAY_PREFERENCES.OWNER)
                .doUpdate()
                .set(ECONOMY_PAY_PREFERENCES.ACCEPTS_PAY, value)
                .execute();
    }
}
