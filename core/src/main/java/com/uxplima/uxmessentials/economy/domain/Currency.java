package com.uxplima.uxmessentials.economy.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A named unit of value and the per-currency policy an operator sets for it: the display symbol and plural
 * noun, the render {@code format}, the decimal {@code precision}, the {@code min}/{@code max} balance clamp
 * every credit and debit honours, the {@code starting} balance a fresh wallet receives, the smallest
 * {@code /pay} amount ({@code minPay}), and the {@code confirmThreshold} above which {@code /payconfirm} is
 * required. Value object. Equality by value, immutable, fixed at module start; the configured set is closed
 * once the economy module enables and a currency is never minted at runtime (the economy GLOSSARY).
 *
 * <p>The economy is multi-currency-capable with a single default: one {@code Currency} ships out of the box
 * and operators declare more in {@code economy.currencies.<id>}. {@link Money} is currency-bound, a figure
 * in one currency never adds to a figure in another, so a {@code Currency} is the unit every {@code Money},
 * every {@link Wallet} balance entry, and every port method is implicitly scoped to. The {@code precision}
 * carried here is what {@link Money} scales its {@link BigDecimal} amount to, so rounding is decided once,
 * per currency, and never drifts.
 */
public final class Currency {

    private final CurrencyId id;
    private final String symbol;
    private final String plural;
    private final String format;
    private final int precision;
    private final BigDecimal min;
    private final BigDecimal max;
    private final BigDecimal starting;
    private final BigDecimal minPay;
    private final @Nullable BigDecimal confirmThreshold;
    private final boolean physical;
    private final java.util.List<Denomination> denominations;
    private final @Nullable String iconMaterial;
    private final boolean transferAllowed;
    private final boolean exchangeAllowed;
    private final boolean leaderboardEnabled;
    private final boolean permissionRequired;
    private final String backendId;

    private Currency(Builder builder) {
        this.id = builder.id;
        this.symbol = builder.symbol;
        this.plural = builder.plural;
        this.format = builder.format;
        this.precision = builder.precision;
        this.min = scale(builder.min, builder.precision);
        this.max = scale(builder.max, builder.precision);
        this.starting = scale(builder.starting, builder.precision);
        this.minPay = scale(builder.minPay, builder.precision);
        this.confirmThreshold =
                builder.confirmThreshold == null ? null : scale(builder.confirmThreshold, builder.precision);
        this.physical = builder.physical;
        this.denominations = java.util.List.copyOf(builder.denominations);
        this.iconMaterial = builder.iconMaterial;
        this.transferAllowed = builder.transferAllowed;
        this.exchangeAllowed = builder.exchangeAllowed;
        this.leaderboardEnabled = builder.leaderboardEnabled;
        this.permissionRequired = builder.permissionRequired;
        this.backendId = builder.backendId;
        if (this.min.compareTo(this.max) > 0) {
            throw new IllegalArgumentException("min-balance must not exceed max-balance for currency " + id);
        }
        if (this.starting.compareTo(this.min) < 0 || this.starting.compareTo(this.max) > 0) {
            throw new IllegalArgumentException("starting balance is outside [min,max] for currency " + id);
        }
    }

    /** Start a currency definition for {@code id}; every other field defaults to a sane single-currency setup. */
    public static Builder builder(CurrencyId id) {
        return new Builder(Objects.requireNonNull(id, "id"));
    }

    /** The currency's stable identifier: the persisted {@code (uuid, currency)} key and the audit field. */
    public CurrencyId id() {
        return id;
    }

    /** The display symbol prepended/appended through the {@code format} when an amount is rendered. */
    public String symbol() {
        return symbol;
    }

    /** The plural noun for the unit (e.g. {@code coins}), referenced by the locale catalog. */
    public String plural() {
        return plural;
    }

    /** The render pattern applied to amounts (e.g. {@code $#,##0.00}); used through a {@code MessageKey}. */
    public String format() {
        return format;
    }

    /** Decimal places this currency is scaled to: the {@link Money} rounding scale. */
    public int precision() {
        return precision;
    }

    /** The lowest balance a wallet may hold in this currency (normally {@code 0}; negative permits overdraft). */
    public BigDecimal min() {
        return min;
    }

    /** The highest balance a wallet may hold; a credit that would exceed it is rejected, never clamped. */
    public BigDecimal max() {
        return max;
    }

    /** The balance a freshly opened wallet is credited with in this currency on first join. */
    public BigDecimal starting() {
        return starting;
    }

    /** The smallest amount a {@code /pay} may move in this currency. */
    public BigDecimal minPay() {
        return minPay;
    }

    /** True when a {@code /pay} of {@code amount} in this currency must route through {@code /payconfirm}. */
    public boolean requiresConfirm(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        return confirmThreshold != null && amount.compareTo(confirmThreshold) > 0;
    }

    /** True when this currency is physical (inventory-backed) instead of virtual. */
    public boolean isPhysical() {
        return physical;
    }

    /** The list of denominations (coins/items representing values) configured for this currency. */
    public java.util.List<Denomination> denominations() {
        return denominations;
    }

    /**
     * The operator-configured icon-material name for this currency in GUIs (e.g. {@code GOLD_INGOT}), or empty
     * when none is set. Held as a plain name here. The domain stays free of Bukkit's {@code Material}; the
     * adapter resolves and validates it once when it builds an icon, falling back to a sensible default.
     */
    public java.util.Optional<String> iconMaterial() {
        return java.util.Optional.ofNullable(iconMaterial);
    }

    /** Whether this currency may move between players via {@code /pay} (and {@code /payall}); default {@code true}. */
    public boolean transferAllowed() {
        return transferAllowed;
    }

    /** Whether this currency may take part in {@code /exchange} conversions; default {@code true}. */
    public boolean exchangeAllowed() {
        return exchangeAllowed;
    }

    /** Whether this currency appears in {@code /baltop} leaderboards; default {@code true}. */
    public boolean leaderboardEnabled() {
        return leaderboardEnabled;
    }

    /**
     * Whether using this currency requires the per-currency permission node
     * {@code uxmessentials.economy.currency.<id>}; default {@code false}. Lets a server ship a permission-gated
     * premium currency without bespoke code. The node check lives in the adapter (the domain stays Bukkit-free).
     */
    public boolean permissionRequired() {
        return permissionRequired;
    }

    /**
     * The {@code CurrencyBackend} id that holds this currency's balances. {@code native}, the default, is the
     * plugin's own DB-backed ledger, the only backend with a guarded compare-and-take debit. Anything else names
     * a foreign economy; see {@code docs/11-economy-integration.md}.
     */
    public String backendId() {
        return backendId;
    }

    /** Scale a raw amount to this currency's precision, half-up, so a stored figure never carries extra digits. */
    public BigDecimal normalize(BigDecimal amount) {
        return scale(Objects.requireNonNull(amount, "amount"), precision);
    }

    private static BigDecimal scale(BigDecimal value, int precision) {
        return value.setScale(precision, RoundingMode.HALF_UP);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Currency currency && id.equals(currency.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id.value();
    }

    /** Mutable assembler for a {@link Currency}; the canonical constructor stays private so invariants hold. */
    public static final class Builder {

        private final CurrencyId id;
        private String symbol = "$";
        private String plural;
        private String format = "#,##0.00";
        private int precision = 2;
        private BigDecimal min = BigDecimal.ZERO;
        private BigDecimal max = new BigDecimal("1000000000000");
        private BigDecimal starting = BigDecimal.ZERO;
        private BigDecimal minPay = new BigDecimal("0.01");
        private @Nullable BigDecimal confirmThreshold;
        private boolean physical = false;
        private final java.util.List<Denomination> denominations = new java.util.ArrayList<>();
        private @Nullable String iconMaterial;
        private boolean transferAllowed = true;
        private boolean exchangeAllowed = true;
        private boolean leaderboardEnabled = true;
        private boolean permissionRequired = false;
        private String backendId = "native";

        private Builder(CurrencyId id) {
            this.id = id;
            this.plural = id.value();
        }

        public Builder symbol(String symbol) {
            this.symbol = Objects.requireNonNull(symbol, "symbol");
            return this;
        }

        public Builder plural(String plural) {
            this.plural = Objects.requireNonNull(plural, "plural");
            return this;
        }

        public Builder format(String format) {
            this.format = Objects.requireNonNull(format, "format");
            return this;
        }

        public Builder precision(int precision) {
            if (precision < 0) {
                throw new IllegalArgumentException("precision must not be negative: " + precision);
            }
            this.precision = precision;
            return this;
        }

        public Builder min(BigDecimal min) {
            this.min = Objects.requireNonNull(min, "min");
            return this;
        }

        public Builder max(BigDecimal max) {
            this.max = Objects.requireNonNull(max, "max");
            return this;
        }

        public Builder starting(BigDecimal starting) {
            this.starting = Objects.requireNonNull(starting, "starting");
            return this;
        }

        public Builder minPay(BigDecimal minPay) {
            this.minPay = Objects.requireNonNull(minPay, "minPay");
            return this;
        }

        /** Set the {@code /payconfirm} threshold; a {@code null} (the default) disables confirmation. */
        public Builder confirmThreshold(BigDecimal confirmThreshold) {
            this.confirmThreshold = confirmThreshold;
            return this;
        }

        public Builder physical(boolean physical) {
            this.physical = physical;
            return this;
        }

        public Builder denominations(java.util.List<Denomination> denominations) {
            this.denominations.clear();
            this.denominations.addAll(Objects.requireNonNull(denominations, "denominations"));
            return this;
        }

        /** Set the GUI icon-material name; a {@code null} or blank (the default) leaves the icon to the view's default. */
        public Builder iconMaterial(@Nullable String iconMaterial) {
            this.iconMaterial = iconMaterial == null || iconMaterial.isBlank() ? null : iconMaterial.strip();
            return this;
        }

        /** Whether {@code /pay} may move this currency between players (default {@code true}). */
        public Builder transferAllowed(boolean transferAllowed) {
            this.transferAllowed = transferAllowed;
            return this;
        }

        /** Whether {@code /exchange} may convert this currency (default {@code true}). */
        public Builder exchangeAllowed(boolean exchangeAllowed) {
            this.exchangeAllowed = exchangeAllowed;
            return this;
        }

        /** Whether this currency appears in {@code /baltop} leaderboards (default {@code true}). */
        public Builder leaderboardEnabled(boolean leaderboardEnabled) {
            this.leaderboardEnabled = leaderboardEnabled;
            return this;
        }

        /** Whether using this currency requires {@code uxmessentials.economy.currency.<id>} (default {@code false}). */
        public Builder permissionRequired(boolean permissionRequired) {
            this.permissionRequired = permissionRequired;
            return this;
        }

        /** The backend that holds this currency. Defaults to the native ledger. */
        public Builder backendId(String backendId) {
            Objects.requireNonNull(backendId, "backendId");
            if (backendId.isBlank()) {
                throw new IllegalArgumentException("backendId must not be blank");
            }
            this.backendId = backendId.strip();
            return this;
        }

        public Currency build() {
            return new Currency(this);
        }
    }
}
