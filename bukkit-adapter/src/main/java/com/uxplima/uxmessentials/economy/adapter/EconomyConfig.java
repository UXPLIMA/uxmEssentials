package com.uxplima.uxmessentials.economy.adapter;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.plugin.ServicePriority;

import com.uxplima.uxmessentials.economy.application.AmountFormat;
import com.uxplima.uxmessentials.economy.application.Worth;
import com.uxplima.uxmessentials.economy.application.WorthTable;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Denomination;
import com.uxplima.uxmessentials.economy.domain.ExchangeRate;
import com.uxplima.uxmessentials.economy.domain.ExchangeRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * Reads the economy module's configuration subtree into the typed values the wiring needs: the
 * {@link CurrencyRegistry} (the closed currency set with its single default), the
 * {@code ServicePriority} for register-or-defer, the {@code /pay} confirm timeout and toggle default, the
 * baltop page size / cache TTL / exempt node, and the persistence debounce/flush windows.
 *
 * <p>A fresh install ships exactly one currency. The configured default with a sensible symbol/format/
 * precision, and every command that omits {@code [currency]} resolves to it. Operators declare more under
 * {@code currencies.<id>}; this reader builds only the default currency from the well-known keys, with the
 * additional-currency map a documented follow-up that needs a structured-list config read not on the narrow
 * {@link ConfigStore} contract (the registry, port, and commands already carry a multi-currency set, so
 * adding more is a config-read change, not a model change).
 */
@NullMarked
public final class EconomyConfig {

    private final ConfigStore config;

    public EconomyConfig(ConfigStore config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Build the closed currency registry by loading all configured currencies. */
    public CurrencyRegistry currencies() {
        CurrencyId defaultId = CurrencyId.of(config.getString("wallet.default-currency", "coins"));
        List<String> keys = config.getKeys("currencies");
        if (keys.isEmpty()) {
            keys = List.of(defaultId.value());
        }
        List<Currency> list = new java.util.ArrayList<>();
        for (String key : keys) {
            CurrencyId id = CurrencyId.of(key);
            Currency.Builder builder = Currency.builder(id)
                    .symbol(config.getString("currencies." + key + ".symbol", "$"))
                    .plural(config.getString("currencies." + key + ".plural", key))
                    .format(config.getString("currencies." + key + ".format", "#,##0.00"))
                    .precision(config.getInt("currencies." + key + ".precision", 2))
                    .starting(decimal("currencies." + key + ".starting", "wallet.starting-balance", "0"))
                    .min(decimal("currencies." + key + ".min-balance", "wallet.min-balance", "0"))
                    .max(decimal("currencies." + key + ".max-balance", "wallet.max-balance", "1000000000000"))
                    .minPay(decimal("currencies." + key + ".min-pay", "pay.min-pay", "0.01"))
                    .iconMaterial(config.getString("currencies." + key + ".icon-material", ""))
                    .transferAllowed(config.getBoolean("currencies." + key + ".transfer-allowed", true))
                    .exchangeAllowed(config.getBoolean("currencies." + key + ".exchange-allowed", true))
                    .leaderboardEnabled(config.getBoolean("currencies." + key + ".leaderboard-enabled", true))
                    .permissionRequired(config.getBoolean("currencies." + key + ".permission-required", false))
                    .backendId(config.getString("currencies." + key + ".backend", "native"));
            BigDecimal confirm = optionalConfirmThreshold(id);
            if (confirm != null) {
                builder.confirmThreshold(confirm);
            }

            boolean physical = config.getBoolean("currencies." + key + ".physical", false);
            builder.physical(physical);

            if (physical) {
                java.util.List<Denomination> denominations = new java.util.ArrayList<>();
                for (String denomKey : config.getKeys("currencies." + key + ".denominations")) {
                    String pathPrefix = "currencies." + key + ".denominations." + denomKey;
                    BigDecimal val = new BigDecimal(
                            config.getString(pathPrefix + ".value", "1").strip());
                    String material = config.getString(pathPrefix + ".material", "EMERALD");
                    String displayName = config.getString(pathPrefix + ".display-name", denomKey);
                    int customModelDataVal = config.getInt(pathPrefix + ".custom-model-data", -1);
                    Integer customModelData = customModelDataVal >= 0 ? customModelDataVal : null;

                    denominations.add(new Denomination(val, material, displayName, customModelData));
                }
                builder.denominations(denominations);
            }

            list.add(builder.build());
        }
        return CurrencyRegistry.of(list, defaultId);
    }

    /** Parses all active exchange rates configured under the exchange.rates section. */
    public ExchangeRegistry exchangeRegistry() {
        List<ExchangeRate> rates = new java.util.ArrayList<>();
        for (String token : config.getStringList("exchange.rates", List.of())) {
            parseExchangeRate(token).ifPresent(rates::add);
        }
        return new ExchangeRegistry(rates);
    }

    private static java.util.Optional<ExchangeRate> parseExchangeRate(String token) {
        List<String> parts = com.google.common.base.Splitter.on(':').splitToList(token);
        if (parts.size() < 3 || parts.size() > 4) {
            return java.util.Optional.empty();
        }
        try {
            CurrencyId source = CurrencyId.of(parts.get(0).strip());
            CurrencyId target = CurrencyId.of(parts.get(1).strip());
            BigDecimal rate = new BigDecimal(parts.get(2).strip());
            BigDecimal feePercent =
                    parts.size() == 4 ? new BigDecimal(parts.get(3).strip()) : BigDecimal.ZERO;
            return java.util.Optional.of(new ExchangeRate(source, target, rate, feePercent));
        } catch (Exception ignored) {
            return java.util.Optional.empty();
        }
    }

    /**
     * The per-item sell value table {@code /worth} reads and {@code /sell} credits against, parsed from the
     * {@code worth.items} string list. An entry is {@code "MATERIAL:price"} (paid in the default currency) or
     * {@code "MATERIAL:price:currencyId"} (paid in that currency), so an operator can price an item in any
     * configured currency from the config file just as {@code /setworth} lets them do in-game. A blank,
     * malformed, or non-positive entry is skipped so a typo never poisons the whole table; an entry whose
     * currency is not configured is loaded in the default currency and logged as a warning so the operator
     * notices the typo. An empty list yields an empty table where every material reads as not-sellable.
     */
    public WorthTable worthTable(
            CurrencyRegistry currencies, com.uxplima.uxmessentials.shared.application.port.Logger log) {
        String defaultCurrency = currencies.defaultCurrency().id().value();
        Map<String, Worth> prices = new HashMap<>();
        for (String token : config.getStringList("worth.items", List.of())) {
            parseWorth(token, defaultCurrency, currencies, log)
                    .ifPresent(entry -> prices.put(entry.material(), entry.worth()));
        }
        return new WorthTable(prices);
    }

    private static java.util.Optional<WorthEntry> parseWorth(
            String token,
            String defaultCurrency,
            CurrencyRegistry currencies,
            com.uxplima.uxmessentials.shared.application.port.Logger log) {
        // A material id has no colon, so the first field is the material and the rest is the worth: the second
        // field is the price and an optional third field is the pay-out currency.
        int firstSplit = token.indexOf(':');
        if (firstSplit <= 0 || firstSplit == token.length() - 1) {
            return java.util.Optional.empty();
        }
        String material = token.substring(0, firstSplit).strip();
        if (material.isEmpty()) {
            return java.util.Optional.empty();
        }
        try {
            // The remainder is "price" or "price:currency"; the legacy "price currency" space form still parses.
            java.util.List<String> worthFields =
                    com.google.common.base.Splitter.on(':').trimResults().splitToList(token.substring(firstSplit + 1));
            java.util.List<String> priceAndCurrency = com.google.common.base.Splitter.on(' ')
                    .omitEmptyStrings()
                    .trimResults()
                    .splitToList(worthFields.get(0));
            if (priceAndCurrency.isEmpty()) {
                return java.util.Optional.empty();
            }
            BigDecimal amount = new BigDecimal(priceAndCurrency.get(0));
            if (amount.signum() <= 0) {
                return java.util.Optional.empty();
            }
            String currency = pickCurrency(worthFields, priceAndCurrency, defaultCurrency);
            currency = resolveCurrency(material, currency, defaultCurrency, currencies, log);
            return java.util.Optional.of(new WorthEntry(material, Worth.of(amount, currency)));
        } catch (Exception notANumber) {
            return java.util.Optional.empty();
        }
    }

    /** The chosen currency id from the colon-third field or the legacy space form, defaulting when absent. */
    private static String pickCurrency(
            java.util.List<String> worthFields, java.util.List<String> priceAndCurrency, String defaultCurrency) {
        if (worthFields.size() > 1 && !worthFields.get(1).isBlank()) {
            return worthFields.get(1).toLowerCase(java.util.Locale.ROOT);
        }
        if (priceAndCurrency.size() > 1) {
            return priceAndCurrency.get(1).toLowerCase(java.util.Locale.ROOT);
        }
        return defaultCurrency;
    }

    /** Keep the chosen currency when it is configured; otherwise warn and fall back to the default. */
    private static String resolveCurrency(
            String material,
            String currency,
            String defaultCurrency,
            CurrencyRegistry currencies,
            com.uxplima.uxmessentials.shared.application.port.Logger log) {
        if (currency.equalsIgnoreCase(defaultCurrency)
                || currencies.find(CurrencyId.of(currency)).isPresent()) {
            return currency;
        }
        log.warn(
                "Worth entry for {} names unknown currency '{}'; pricing it in the default currency '{}' instead",
                material,
                currency,
                defaultCurrency);
        return defaultCurrency;
    }

    private record WorthEntry(String material, Worth worth) {}

    /** The {@code ServicePriority} the native provider registers at; {@code Normal} unless raised on purpose. */
    public ServicePriority registerPriority() {
        String raw = config.getString("provider.priority", "Normal");
        try {
            return ServicePriority.valueOf(capitalise(raw));
        } catch (IllegalArgumentException unknown) {
            return ServicePriority.Normal;
        }
    }

    /** Whether to register the native provider at all (the register-or-defer entry condition). */
    public boolean registerProvider() {
        return config.getBoolean("provider.register", true);
    }

    /**
     * Whether the operator accepts a scheduled charge against a currency whose backend cannot guard its take.
     * Off by default so a recurring charge on such a currency is refused at startup rather than risking a
     * double charge on the pass after a debit that reported failure but had in fact succeeded.
     */
    public boolean allowNonAtomicRecurring() {
        return config.getBoolean("allow-nonatomic-recurring", false);
    }

    /** The default accept-pay flag a player takes before they ever run {@code /paytoggle}. */
    public boolean payToggleDefault() {
        return config.getBoolean("pay.toggle-default", true);
    }

    /** How balances render: {@code full} (grouped, the default) or {@code compact} (suffix-abbreviated). */
    public AmountFormat amountFormat() {
        return AmountFormat.fromConfig(config.getString("amount-format", AmountFormat.FULL.token()));
    }

    /** The {@code /payconfirm} prompt timeout. */
    public Duration confirmTimeout() {
        return Duration.ofMillis(Math.max(1_000L, config.getLong("pay.confirm-timeout-ms", 30_000L)));
    }

    /** The {@code /baltop} page size, clamped to the use case's ceiling by the use case itself. */
    public int baltopPageSize() {
        return Math.max(1, config.getInt("baltop.page-size", 10));
    }

    /** The per-currency baltop snapshot refresh interval. */
    public Duration baltopCacheTtl() {
        return Duration.ofMillis(Math.max(1_000L, config.getLong("baltop.cache-ttl-ms", 60_000L)));
    }

    /** The number of rows each baltop snapshot retains. */
    public int baltopCapacity() {
        return Math.max(baltopPageSize(), config.getInt("baltop.snapshot-capacity", 100));
    }

    /** The permission node whose holders are excluded from every leaderboard. */
    public String baltopExemptNode() {
        return config.getString("baltop.exempt-permission", "uxmessentials.economy.baltop.exempt");
    }

    /** Whether banned players are dropped from every {@code /baltop} (default off). */
    public boolean baltopExcludeBanned() {
        return config.getBoolean("baltop.exclude-banned", false);
    }

    /** The minimum balance a row needs to appear in {@code /baltop}; {@code 0} (default) keeps everyone. */
    public BigDecimal baltopMinBalance() {
        return bigDecimal("baltop.min-balance", BigDecimal.ZERO).max(BigDecimal.ZERO);
    }

    /** The debounced-settle window. */
    public Duration writeDebounce() {
        return Duration.ofMillis(Math.max(50L, config.getLong("persistence.write-debounce-ms", 250L)));
    }

    /** The append-only telemetry batch-flush interval. */
    public Duration batchFlush() {
        return Duration.ofMillis(Math.max(100L, config.getLong("persistence.batch-flush-ms", 1_000L)));
    }

    private @org.jspecify.annotations.Nullable BigDecimal optionalConfirmThreshold(CurrencyId id) {
        String raw = config.getString("currencies." + id.value() + ".confirm-threshold", "");
        String fallback = config.getString("pay.confirm-threshold", "");
        String chosen = raw.isEmpty() ? fallback : raw;
        if (chosen.isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(chosen.strip());
            // A configured -1 disables confirmation for the currency (the doc's sentinel).
            return value.signum() < 0 ? null : value;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private BigDecimal decimal(String specificPath, String fallbackPath, String hardDefault) {
        String raw = config.getString(specificPath, "");
        if (raw.isEmpty()) {
            raw = config.getString(fallbackPath, hardDefault);
        }
        try {
            return new BigDecimal(raw.strip());
        } catch (NumberFormatException notANumber) {
            return new BigDecimal(hardDefault);
        }
    }

    private static String capitalise(String value) {
        String trimmed = value.strip();
        return trimmed.isEmpty() ? trimmed : Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    /** Whether banknotes (/withdraw, /deposit) are enabled. */
    public boolean banknotesEnabled() {
        return config.getBoolean("banknotes.enabled", true);
    }

    /** Whether virtual bank accounts (/bank) are enabled. */
    public boolean bankEnabled() {
        return config.getBoolean("bank.enabled", true);
    }

    /** Whether the loan and debt system (/loan) is enabled. */
    public boolean loansEnabled() {
        return config.getBoolean("loans.enabled", true);
    }

    /** How often the background sweep applies due auto-repayments; floored at one minute. */
    public Duration loanSweepInterval() {
        return Duration.ofSeconds(Math.max(60L, config.getLong("loans.auto-repay-sweep-seconds", 600L)));
    }

    /**
     * The operator-tunable loan economics. Every decimal is built from its configured string so no figure
     * passes through a {@code double}; a malformed entry falls back to the shipped default for that field.
     */
    public com.uxplima.uxmessentials.economy.application.LoanPolicy loanPolicy() {
        com.uxplima.uxmessentials.economy.application.LoanPolicy defaults =
                com.uxplima.uxmessentials.economy.application.LoanPolicy.defaults();
        BigDecimal limitMultiplier = bigDecimal("loans.limit-multiplier", defaults.limitMultiplier());
        BigDecimal interestMax = bigDecimal("loans.interest-max", defaults.interestMax());
        BigDecimal interestSpan = bigDecimal("loans.interest-span", defaults.interestSpan());
        long cycleHours = Math.max(1L, config.getLong("loans.installment-cycle-hours", 24L));
        int onTimeManual = config.getInt("loans.score-on-time-manual", defaults.onTimeManualDelta());
        int onTimeAuto = config.getInt("loans.score-on-time-auto", defaults.onTimeAutoDelta());
        int missed = config.getInt("loans.score-missed", defaults.missedDelta());
        int scoreFloor = Math.max(0, config.getInt("loans.score-floor", defaults.scoreFloor()));
        int scoreCeiling = Math.max(scoreFloor, config.getInt("loans.score-ceiling", defaults.scoreCeiling()));
        return new com.uxplima.uxmessentials.economy.application.LoanPolicy(
                limitMultiplier,
                interestMax,
                interestSpan,
                java.time.Duration.ofHours(cycleHours),
                onTimeManual,
                onTimeAuto,
                missed,
                scoreFloor,
                scoreCeiling);
    }

    /** The operator-tunable pay-tax rule; disabled by default so an untouched config leaves {@code /pay} untaxed. */
    public com.uxplima.uxmessentials.economy.application.TaxPolicy taxPolicy() {
        boolean enabled = config.getBoolean("pay.tax.enabled", false);
        BigDecimal percent = bigDecimal("pay.tax.percent", BigDecimal.ZERO).max(BigDecimal.ZERO);
        BigDecimal flat = bigDecimal("pay.tax.flat", BigDecimal.ZERO).max(BigDecimal.ZERO);
        return new com.uxplima.uxmessentials.economy.application.TaxPolicy(enabled, percent, flat);
    }

    /** The permission node whose holders pay no {@code /pay} tax. */
    public String taxBypassNode() {
        return config.getString("pay.tax.bypass-permission", "uxmessentials.economy.tax.bypass");
    }

    /**
     * Where the pay tax goes: a configured holding wallet when {@code pay.tax.sink = "account:<uuid>"}, otherwise
     * (the default {@code "void"} or a malformed value) destroyed. The account name is cosmetic: the wallet is
     * keyed by the uuid.
     */
    public java.util.Optional<com.uxplima.uxmessentials.shared.domain.PlayerRef> taxSinkAccount() {
        String raw = config.getString("pay.tax.sink", "void").strip();
        if (!raw.toLowerCase(java.util.Locale.ROOT).startsWith("account:")) {
            return java.util.Optional.empty();
        }
        try {
            java.util.UUID uuid =
                    java.util.UUID.fromString(raw.substring("account:".length()).strip());
            return java.util.Optional.of(new com.uxplima.uxmessentials.shared.domain.PlayerRef(uuid, "tax-account"));
        } catch (IllegalArgumentException badUuid) {
            return java.util.Optional.empty();
        }
    }

    /** Whether the periodic data-maintenance task runs at all (off by default; the destructive opt-in). */
    public boolean maintenanceEnabled() {
        return config.getBoolean("maintenance.enabled", false);
    }

    /** How often the maintenance task runs (at least hourly). */
    public Duration maintenanceInterval() {
        return Duration.ofHours(Math.max(1L, config.getLong("maintenance.run-interval-hours", 24L)));
    }

    /** Purge the wallets of players whose last login is older than this many days. */
    public long maintenancePurgeInactiveDays() {
        return Math.max(1L, config.getLong("maintenance.purge-inactive-days", 90L));
    }

    /** Trim transaction telemetry older than this many days. */
    public long maintenancePruneTransactionDays() {
        return Math.max(1L, config.getLong("maintenance.prune-transactions-days", 90L));
    }

    /** When true (the default), the task only reports what it would remove and deletes nothing. */
    public boolean maintenanceDryRun() {
        return config.getBoolean("maintenance.dry-run", true);
    }

    /** The operator-tunable bank-interest rule; disabled by default so banks accrue nothing until enabled. */
    public com.uxplima.uxmessentials.economy.application.BankInterestPolicy bankInterestPolicy() {
        boolean enabled = config.getBoolean("bank.interest.enabled", false);
        Duration interval = Duration.ofHours(Math.max(1L, config.getLong("bank.interest.interval-hours", 24L)));
        List<com.uxplima.uxmessentials.economy.application.BankInterestPolicy.Tier> tiers = new java.util.ArrayList<>();
        for (String token : config.getStringList("bank.interest.tiers", List.of())) {
            parseTier(token).ifPresent(tiers::add);
        }
        return new com.uxplima.uxmessentials.economy.application.BankInterestPolicy(enabled, interval, tiers);
    }

    /** Parse one {@code "minBalance:ratePercent"} tier token; empty when it is malformed (skipped, not fatal). */
    private static java.util.Optional<com.uxplima.uxmessentials.economy.application.BankInterestPolicy.Tier> parseTier(
            String token) {
        List<String> parts = com.google.common.base.Splitter.on(':').splitToList(token);
        if (parts.size() != 2) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(new com.uxplima.uxmessentials.economy.application.BankInterestPolicy.Tier(
                    new BigDecimal(parts.get(0).strip()),
                    new BigDecimal(parts.get(1).strip())));
        } catch (NumberFormatException malformed) {
            return java.util.Optional.empty();
        }
    }

    private BigDecimal bigDecimal(String path, BigDecimal fallback) {
        String raw = config.getString(path, "").strip();
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    /** Whether the currency exchange system (/exchange) is enabled. */
    public boolean exchangeEnabled() {
        return config.getBoolean("exchange.enabled", true);
    }

    /** Whether item worth and selling (/worth, /sell, /sellall, /setworth) is enabled. */
    public boolean worthEnabled() {
        return config.getBoolean("worth.enabled", true);
    }

    /**
     * Whether an item with no override and no configured worth may fall back to EconomyShopGUI's own sell price.
     * With that plugin absent the setting has no effect: there is nothing to fall back to.
     */
    public boolean shopWorthFallbackEnabled() {
        return config.getBoolean("worth.economyshopgui-fallback", true);
    }

    /** Whether command costs are enabled. */
    public boolean commandCostsEnabled() {
        return config.getBoolean("command-costs-enabled", true);
    }

    /** Whether the salary task is enabled. */
    public boolean salaryEnabled() {
        return config.getBoolean("salary.enabled", false);
    }

    /** The interval between automatic salaries. */
    public java.time.Duration salaryInterval() {
        long seconds = config.getLong("salary.interval-seconds", 3600L);
        return java.time.Duration.ofSeconds(Math.max(1L, seconds));
    }

    /** The default salary amount. */
    public long salaryDefaultAmount() {
        return Math.max(0L, config.getLong("salary.default-amount", 100L));
    }

    /** Whether the plain-text {@code economy/operations.log} audit file is written (off by default). */
    public boolean operationLogEnabled() {
        return config.getBoolean("logs.file-enabled", false);
    }

    /** Whether the daily login reward is enabled (off by default). */
    public boolean dailyRewardEnabled() {
        return config.getBoolean("daily-reward.enabled", false);
    }

    /** The daily login reward amount, in the reward currency's units. */
    public BigDecimal dailyRewardAmount() {
        return bigDecimal("daily-reward.amount", BigDecimal.ZERO).max(BigDecimal.ZERO);
    }

    /** The minimum gap between two daily-reward claims (at least an hour). */
    public Duration dailyRewardCooldown() {
        return Duration.ofHours(Math.max(1L, config.getLong("daily-reward.cooldown-hours", 24L)));
    }

    /** The currency the daily reward is paid in; the configured default when blank or unknown. */
    public Currency dailyRewardCurrency(CurrencyRegistry currencies) {
        String id = config.getString("daily-reward.currency", "").strip();
        if (id.isEmpty()) {
            return currencies.defaultCurrency();
        }
        return currencies.find(CurrencyId.of(id)).orElseGet(currencies::defaultCurrency);
    }

    /** Whether fraud detection is enabled. */
    public boolean fraudEnabled() {
        return config.getBoolean("fraud-detection.enabled", false);
    }

    /** Webhook URL for fraud alerts. */
    public String fraudWebhookUrl() {
        return config.getString("fraud-detection.webhook-url", "");
    }

    /** The limit above which a single transaction triggers a fraud alert. */
    public BigDecimal fraudSingleLimit() {
        try {
            return new BigDecimal(
                    config.getString("fraud-detection.single-limit", "1000000").strip());
        } catch (NumberFormatException e) {
            return new BigDecimal("1000000");
        }
    }

    /** The velocity monitoring period in seconds. */
    public long fraudVelocitySeconds() {
        return Math.max(1L, config.getLong("fraud-detection.velocity-seconds", 60L));
    }

    /** The cumulative limit in a period above which a velocity fraud alert is triggered. */
    public BigDecimal fraudVelocityLimit() {
        try {
            return new BigDecimal(config.getString("fraud-detection.velocity-limit", "5000000")
                    .strip());
        } catch (NumberFormatException e) {
            return new BigDecimal("5000000");
        }
    }

    /** Whether the multi-currency /wallet GUI is enabled. */
    public boolean walletGuiEnabled() {
        return config.getBoolean("wallet.gui-enabled", true);
    }

    /**
     * The time zone the transaction-history GUI formats timestamps in. Defaults to UTC so a record reads the
     * same on every server in a network rather than drifting with each host's {@code ZoneId.systemDefault()};
     * an operator sets {@code gui.timezone} to a zone id (e.g. {@code Europe/Istanbul}) to render in local time.
     * An unknown zone id falls back to UTC.
     */
    public java.time.ZoneId historyTimeZone() {
        String raw = config.getString("gui.timezone", "UTC").strip();
        try {
            return java.time.ZoneId.of(raw.isEmpty() ? "UTC" : raw);
        } catch (java.time.DateTimeException unknownZone) {
            return java.time.ZoneOffset.UTC;
        }
    }

    public record CommandCost(BigDecimal amount, String currencyId) {}

    /** Returns map of lowercase command labels (without slash) to their configured decimal costs. */
    public Map<String, CommandCost> commandCosts() {
        String defaultCurrency = config.getString("wallet.default-currency", "coins");
        Map<String, CommandCost> costs = new HashMap<>();
        for (String token : config.getStringList("command-costs", List.of())) {
            int split = token.lastIndexOf(':');
            if (split <= 0 || split == token.length() - 1) {
                continue;
            }
            String command = token.substring(0, split).strip().toLowerCase(java.util.Locale.ROOT);
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            try {
                String costPart = token.substring(split + 1).strip();
                java.util.List<String> parts = com.google.common.base.Splitter.on(' ')
                        .omitEmptyStrings()
                        .trimResults()
                        .splitToList(costPart);
                if (parts.isEmpty()) {
                    continue;
                }
                BigDecimal cost = new BigDecimal(parts.get(0));
                if (cost.signum() >= 0) {
                    String currency =
                            parts.size() > 1 ? parts.get(1).toLowerCase(java.util.Locale.ROOT) : defaultCurrency;
                    costs.put(command, new CommandCost(cost, currency));
                }
            } catch (Exception notANumber) {
                // Ignore malformed command cost numbers
            }
        }
        return costs;
    }
}
