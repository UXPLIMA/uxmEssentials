package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.MoneyFormat;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.survival.application.port.SurvivalSales;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges the survival context's narrow {@link SurvivalSales} seam to the resolved {@link EconomyProvider}, so an
 * autosold drop's proceeds are credited in the default currency without the survival context ever importing an economy
 * type (mirrors {@link ProviderKitEconomy} and {@link ProviderNpcEconomy}). This is the adapter that flips the survival
 * {@code Optional<SurvivalSales>} from empty to present once economy is wired.
 *
 * <p>A sale amount is a bare {@link BigDecimal} in survival's own terms; this adapter denominates it in the configured
 * default {@link Currency} before crediting. {@code credit} is a single guarded deposit whose {@code isOk()} reports
 * whether the proceeds were banked, so a sale pays out exactly once, and a refused deposit (a balance cap) reports
 * {@code false} rather than silently dropping the coin.
 *
 * <p>{@code format} renders the proceeds for the sale notice through the same {@link MoneyFormat} every other economy
 * surface uses, so the figure in "sold for" reads exactly like the one {@code /balance} prints.
 */
@NullMarked
public final class ProviderSurvivalSales implements SurvivalSales {

    private final EconomyProvider economy;
    private final Currency currency;

    public ProviderSurvivalSales(EconomyProvider economy, Currency currency) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    @Override
    public boolean credit(PlayerRef who, BigDecimal amount) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        return economy.credit(who, Money.of(currency, amount)).isOk();
    }

    @Override
    public String format(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        return MoneyFormat.withSymbol(Money.of(currency, amount));
    }
}
