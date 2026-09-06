/**
 * Outbound ports the economy use cases drive. {@code EconomyProvider} is the canonical seam every other
 * context reaches money through (native ledger, Treasury, or Vault all satisfy it); {@code WalletRepository}
 * is the durable ledger behind the native provider (the guarded transfer/debit {@code UPDATE} lives in its
 * jOOQ adapter, mirroring {@code JooqHomeRepository}); {@code PayPreferences}, {@code BaltopExemption}, and
 * {@code EconomyAudit} carry the {@code /paytoggle} flag, the {@code /baltop} exemption check, and the audit
 * trail. {@code BaltopRow} is the ranked read-model row the port returns. None of these import a Vault or
 * Treasury type, the SDKs live only in the outbound adapter, ArchUnit-fenced.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.economy.application.port;
