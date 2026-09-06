package com.uxplima.uxmessentials.shared.adapter.outbound.currency;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Precision;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * The multi-currency façade the menu vocabulary spends through: it maps a spec string to the
 * {@link CurrencyProvider} that serves it, over the same {@link CurrencyBackend} set a warp fee or a
 * {@code /pay} routes through. A {@code give-money} click and a warp fee therefore move the same money, a
 * native currency now passes through the guarded debit and lands in the transaction ledger, which it never
 * did while this façade owned a parallel, {@code double}-surfaced set of providers.
 *
 * <p>Ordering: this façade is built while the menu engine wires, long before the economy module does, so it
 * cannot receive the registries at construction. It holds a supplier of {@link EconomyBackends} instead and
 * reads it on the first click that resolves a currency: by which point the economy wiring has filled it. A
 * resolve that somehow beats the wiring warns once and returns an unavailable provider <em>without caching
 * it</em>, so the façade heals the moment the economy is up instead of staying broken for the session.
 *
 * <p>Spec resolution, in order:
 *
 * <ul>
 *   <li>a configured {@link CurrencyId} → that {@link Currency} over its declared backend;
 *   <li>otherwise a registered {@link CurrencyBackend} id → a synthetic currency built for it, cached;
 *   <li>otherwise a no-op provider (never available), with one warning logged.
 * </ul>
 *
 * <p>A spec is normalised (trimmed, the backend head lower-cased, any sub-currency name left verbatim) so it
 * keys the cache stably; {@link #resolve(String)} returns the same provider instance for the same normalised
 * spec once the registries are present. No static state: the supplier and logger are constructor-injected.
 */
public final class Currencies {

    // A synthetic currency wraps a foreign backend that enforces its own balance ceiling; a local max clamp
    // here would wrongly reject a legitimate credit, so the synthetic currency is minted effectively unbounded.
    private static final BigDecimal UNBOUNDED_MAX = new BigDecimal("1000000000000000000000");

    private final Supplier<@Nullable EconomyBackends> backends;
    private final Logger log;
    private final String defaultSpec;
    private final ConcurrentMap<String, CurrencyProvider> cache = new ConcurrentHashMap<>();
    private final AtomicBoolean warnedUnwired = new AtomicBoolean();

    public Currencies(Supplier<@Nullable EconomyBackends> backends, Logger log, String defaultCurrency) {
        this.backends = Objects.requireNonNull(backends, "backends");
        this.log = Objects.requireNonNull(log, "log");
        Objects.requireNonNull(defaultCurrency, "defaultCurrency");
        // A blank configured default would otherwise recurse blank → default → blank; fall back to vault, the
        // out-of-the-box server economy, and document it so a misconfiguration reads as an obvious choice.
        String configured = normalise(defaultCurrency);
        this.defaultSpec = configured.isEmpty() ? "vault" : configured;
    }

    /** The provider for {@code spec}; never null. A blank spec resolves the configured default. Cached per spec. */
    public CurrencyProvider resolve(String spec) {
        Objects.requireNonNull(spec, "spec");
        String normalised = normalise(spec);
        String key = normalised.isEmpty() ? defaultSpec : normalised;
        EconomyBackends state = backends.get();
        if (state == null) {
            if (warnedUnwired.compareAndSet(false, true)) {
                log.warn("event=currency_unwired spec={} (economy not yet wired)", key);
            }
            // Deliberately uncached: caching this would keep the façade unavailable for the rest of the session,
            // long after the economy wires. The next resolve rebuilds against the now-present registries.
            return CurrencyProvider.unavailable(key);
        }
        return cache.computeIfAbsent(key, resolvedKey -> build(resolvedKey, state));
    }

    /** The configured default currency id (already normalised), e.g. {@code vault}. */
    public String defaultCurrency() {
        return defaultSpec;
    }

    private CurrencyProvider build(String spec, EconomyBackends state) {
        Optional<Currency> configured = currencyId(spec).flatMap(state.currencies()::find);
        if (configured.isPresent()) {
            return configuredCurrency(spec, state, configured.get());
        }
        Optional<CurrencyBackend> backend = state.backends().find(spec);
        if (backend.isPresent()) {
            return synthetic(spec, backend.get());
        }
        log.warn("event=currency_unknown spec={}", spec);
        return CurrencyProvider.unavailable(spec);
    }

    private CurrencyProvider configuredCurrency(String spec, EconomyBackends state, Currency currency) {
        Optional<CurrencyBackend> backend = state.backends().find(currency.backendId());
        if (backend.isPresent()) {
            return new BackedCurrencyProvider(spec, backend.get(), currency);
        }
        log.warn("event=currency_backend_missing spec={} backend={}", spec, currency.backendId());
        return CurrencyProvider.unavailable(spec);
    }

    private CurrencyProvider synthetic(String spec, CurrencyBackend backend) {
        // A backend id such as coinsengine:gold is not a legal currency id (the colon), so mint the synthetic
        // currency under a hyphenated, lower-cased form; the provider still answers to the original spec via id().
        Optional<CurrencyId> id = currencyId(spec.replace(':', '-'));
        if (id.isEmpty()) {
            log.warn("event=currency_unknown spec={}", spec);
            return CurrencyProvider.unavailable(spec);
        }
        int precision = backend.precision() == Precision.INTEGRAL ? 0 : 2;
        Currency currency = Currency.builder(id.get())
                .symbol("")
                .precision(precision)
                .max(UNBOUNDED_MAX)
                .build();
        return new BackedCurrencyProvider(spec, backend, currency);
    }

    private static Optional<CurrencyId> currencyId(String spec) {
        try {
            return Optional.of(CurrencyId.of(spec));
        } catch (IllegalArgumentException notACurrencyId) {
            return Optional.empty();
        }
    }

    /** Trim, lower-case the back-end head, and keep the sub-currency name verbatim (case can matter to a plugin). */
    private static String normalise(String spec) {
        String trimmed = spec.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int colon = trimmed.indexOf(':');
        if (colon < 0) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        String head = trimmed.substring(0, colon).toLowerCase(Locale.ROOT);
        return head + ":" + trimmed.substring(colon + 1);
    }
}
