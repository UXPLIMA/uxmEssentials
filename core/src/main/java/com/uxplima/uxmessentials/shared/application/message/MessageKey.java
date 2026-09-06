package com.uxplima.uxmessentials.shared.application.message;

/**
 * Compile-time handle for one user-visible string. Every player-facing message resolves through a
 * {@code MessageKey}; there are no inline literals anywhere in the codebase
 * ({@code NoInlineUserMessageDriftTest}).
 *
 * <p>The contract is intentionally minimal. A key carries only its {@link #key() catalog lookup
 * string} and nothing else. The text lives in per-locale {@code messages_<lang>.conf} catalogs loaded
 * by the adapter; the constant is the handle, the catalog is the registry. This is what keeps the
 * enum immutable and lets a new locale be added by shipping a file, never by touching Java.
 *
 * <p>Per-module key ownership: each bounded context defines its own {@code enum} implementing this
 * interface (the teleport context owns the teleport keys, economy owns the wallet keys, …), so a
 * context references its own keys without a cross-context dependency and a disabled module still ships
 * its keys to keep the catalog whole. The constant name and the catalog key map 1:1 by convention
 * {@code HOME_NOT_FOUND} ↔ {@code home.not-found}, and that mapping is asserted by the locale-parity
 * guard. Catalog keys are kebab-case, dot-separated; the enum constant is {@code UPPER_SNAKE}.
 */
// A key is its catalog string and nothing else, which is what lets an error enum hold one as a plain field. Saying
// so out loud is what stops Error Prone's enum-immutability check from flagging every such field.
@com.google.errorprone.annotations.Immutable
public interface MessageKey {

    /** The runtime lookup string in the locale catalog (kebab-case, dot-separated). */
    String key();
}
