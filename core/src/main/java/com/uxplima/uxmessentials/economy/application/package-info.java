/**
 * The economy context's use cases, the orchestration above the pure domain. {@code Balance}, {@code Pay}
 * (with the per-currency confirm flow and {@code /paytoggle}), {@code BalTop}, and {@code EcoAdmin}
 * (give/take/set/reset plus the bulk giveall/giverandom/resetall) each reach money only through the
 * {@code EconomyProvider} port (or, for the exact-balance admin paths, the native {@code WalletRepository}),
 * never by touching another context's domain. {@code EconomyMessageKey} carries every user-visible string;
 * {@code EconomyModule} is the {@code FeatureModule} that wires them. No Bukkit, Paper, Kyori, logging,
 * Vault, or Treasury type appears here. The ArchUnit fence {@code economyDomainHasNoProviderSdk} keeps the
 * provider SDKs confined to the outbound adapter.
 *
 * <p>Where "the command gate" in these use cases points, and where it does not. {@code /bank} gates its verbs
 * separately ({@code uxmessentials.economy.bank.deposit}, {@code .withdraw}, {@code .members},
 * {@code .create}), all default-held so an operator narrows by negating one, while bare {@code /bank} needs only
 * {@code uxmessentials.economy.bank} and opens the panel. The panel is a second door: its deposit, withdraw and
 * members buttons carry no node check, so negating a bank capability does not stop a player who can open the
 * GUI. Warps and kits answer this by checking the node inside the use case, where every adapter inherits it;
 * these use cases do not. The GUI side is a known open defect, not a design.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.economy.application;
