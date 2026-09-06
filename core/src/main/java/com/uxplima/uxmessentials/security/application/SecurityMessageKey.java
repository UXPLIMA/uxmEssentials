package com.uxplima.uxmessentials.security.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The security context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code SECURITY_2FA_ENABLED} ↔ {@code security.2fa.enabled}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in the
 * context: every message resolves through one of these.
 *
 * <p>This is the Phase-1 seed: the enrolment surface ({@code /2fa setup|confirm|disable}, {@code /pin set}). Later
 * phases (join verification, op-command protection, IP/alt guard) add their own keys here as their behaviour lands.
 * Per the i18n contract a disabled module still ships its keys so the catalog stays whole and the locale-parity
 * guard sees the full {@code en} key set.
 */
public enum SecurityMessageKey implements MessageKey {

    // /2fa setup, the enrolment challenge: the header, the secret and otpauth URI for the authenticator app, and
    // the hint to confirm with a code before it takes effect.
    SECURITY_2FA_SETUP_HEADER("security.2fa.setup-header"),
    SECURITY_2FA_SETUP_SECRET("security.2fa.setup-secret"),
    SECURITY_2FA_SETUP_URI("security.2fa.setup-uri"),
    SECURITY_2FA_SETUP_HINT("security.2fa.setup-hint"),
    SECURITY_2FA_ALREADY_ENROLLED("security.2fa.already-enrolled"),

    // /2fa confirm: the second step that turns the pending secret into an enabled factor.
    SECURITY_2FA_CONFIRM_USAGE("security.2fa.confirm-usage"),
    SECURITY_2FA_CONFIRM_NO_PENDING("security.2fa.confirm-no-pending"),
    SECURITY_2FA_CONFIRM_INVALID("security.2fa.confirm-invalid"),
    SECURITY_2FA_ENABLED("security.2fa.enabled"),

    // /2fa disable: removing the authenticator factor, which requires proving it with a current code first. A PIN
    // neither proves this nor is touched by it; that is /pin remove's business.
    SECURITY_2FA_DISABLE_USAGE("security.2fa.disable-usage"),
    SECURITY_2FA_DISABLE_NOT_ENROLLED("security.2fa.disable-not-enrolled"),
    SECURITY_2FA_DISABLE_INVALID("security.2fa.disable-invalid"),
    SECURITY_2FA_DISABLE_LOCKED_OUT("security.2fa.disable-locked-out"),
    SECURITY_2FA_DISABLED("security.2fa.disabled"),

    // The bare /2fa root: its usage line, the enrolled/not-enrolled status lines, and the refusal when authenticator
    // enrolment is switched off server-side.
    SECURITY_2FA_USAGE("security.2fa.usage"),
    SECURITY_2FA_STATUS("security.2fa.status"),
    SECURITY_2FA_STATUS_NONE("security.2fa.status-none"),
    SECURITY_2FA_FEATURE_DISABLED("security.2fa.feature-disabled"),

    // /pin. The PIN factor's own surface: the root usage and status lines, the first-set verbs, and the feature-off
    // refusal. The three PinPolicy refusals are shared by /pin set and /pin change.
    SECURITY_PIN_USAGE("security.pin.usage"),
    SECURITY_PIN_STATUS("security.pin.status"),
    SECURITY_PIN_STATUS_NONE("security.pin.status-none"),
    SECURITY_PIN_SET("security.pin.set"),
    SECURITY_PIN_ALREADY_SET("security.pin.already-set"),
    SECURITY_PIN_TOO_SHORT("security.pin.too-short"),
    SECURITY_PIN_TOO_LONG("security.pin.too-long"),
    SECURITY_PIN_NOT_NUMERIC("security.pin.not-numeric"),
    SECURITY_PIN_BLOCKED("security.pin.blocked"),

    // The create-a-PIN pad shown to a player the server requires a PIN from: the two step prompts, the mismatch, the
    // window title, and the line that says they are free to play.
    SECURITY_PIN_CREATE_TITLE("security.pin.create-title"),
    SECURITY_PIN_CREATE_PROMPT("security.pin.create-prompt"),
    SECURITY_PIN_CREATE_CONFIRM("security.pin.create-confirm"),
    SECURITY_PIN_CREATE_MISMATCH("security.pin.create-mismatch"),
    SECURITY_PIN_CREATE_DONE("security.pin.create-done"),

    // /pin lock, locking your own account from the keypad before stepping away.
    SECURITY_PIN_LOCK_DONE("security.pin.lock-done"),
    SECURITY_PIN_LOCK_NOT_SET("security.pin.lock-not-set"),
    SECURITY_PIN_FEATURE_DISABLED("security.pin.feature-disabled"),

    // /pin change <old> <new>: replacing a live PIN, which requires proving the current one.
    SECURITY_PIN_CHANGE_USAGE("security.pin.change-usage"),
    SECURITY_PIN_CHANGED("security.pin.changed"),

    // /pin remove <pin>: removing the PIN factor, which requires proving it. Shares the not-set / invalid / locked
    // refusals with /pin change, since both fail the same three ways before they touch the store.
    SECURITY_PIN_REMOVE_USAGE("security.pin.remove-usage"),
    SECURITY_PIN_REMOVED("security.pin.removed"),
    SECURITY_PIN_NOT_SET("security.pin.not-set"),
    SECURITY_PIN_INVALID("security.pin.invalid"),
    SECURITY_PIN_LOCKED_OUT("security.pin.locked-out"),

    // /security. The operator surface over another player's factors: the root usage, the per-target status lines,
    // the force verb that pushes a target back into verification, and the reset verb that clears a factor without a
    // proof so a locked-out player can be recovered.
    SECURITY_ADMIN_USAGE("security.admin.usage"),
    SECURITY_ADMIN_STATUS_HEADER("security.admin.status-header"),
    SECURITY_ADMIN_STATUS_TOTP("security.admin.status-totp"),
    SECURITY_ADMIN_STATUS_PIN("security.admin.status-pin"),
    SECURITY_ADMIN_STATUS_NONE("security.admin.status-none"),
    SECURITY_ADMIN_FORCE_USAGE("security.admin.force-usage"),
    SECURITY_ADMIN_FORCE_DONE("security.admin.force-done"),
    SECURITY_ADMIN_FORCE_NOT_ENROLLED("security.admin.force-not-enrolled"),
    SECURITY_ADMIN_RESET_USAGE("security.admin.reset-usage"),
    SECURITY_ADMIN_RESET_DONE("security.admin.reset-done"),
    SECURITY_ADMIN_RESET_NOTHING("security.admin.reset-nothing"),

    // Phase 2, join verification: the freeze prompt on join, the keypad GUI (title, digit/clear/submit/TOTP buttons
    // and the masked entry display), the TOTP text prompt, the success and failed replies, the must-verify nudge when
    // a frozen player tries to act, and the lockout kick after too many failures.
    SECURITY_VERIFY_PROMPT("security.verify.prompt"),
    SECURITY_VERIFY_KEYPAD_TITLE("security.verify.keypad-title"),
    SECURITY_VERIFY_KEYPAD_DIGIT("security.verify.keypad-digit"),
    SECURITY_VERIFY_KEYPAD_CLEAR("security.verify.keypad-clear"),
    SECURITY_VERIFY_KEYPAD_SUBMIT("security.verify.keypad-submit"),
    SECURITY_VERIFY_KEYPAD_ENTRY("security.verify.keypad-entry"),
    SECURITY_VERIFY_KEYPAD_TOTP("security.verify.keypad-totp"),
    SECURITY_VERIFY_TOTP_PROMPT("security.verify.totp-prompt"),
    SECURITY_VERIFY_SUCCESS("security.verify.success"),
    SECURITY_VERIFY_FAILED("security.verify.failed"),
    SECURITY_VERIFY_MUST_VERIFY("security.verify.must-verify"),
    SECURITY_VERIFY_LOCKED_OUT("security.verify.locked-out"),
    SECURITY_VERIFY_UNAVAILABLE("security.verify.unavailable"),
    SECURITY_VERIFY_TIMED_OUT("security.verify.timed-out"),
    SECURITY_ACCESS_REVOKED("security.verify.access-revoked"),

    // The titles shown over the keypad, where a player with a window open is actually looking, and the two outcome
    // titles that replace them. Paired with the feedback sounds; both are operator-switchable.
    SECURITY_VERIFY_TITLE("security.verify.title"),
    SECURITY_VERIFY_SUBTITLE("security.verify.subtitle"),
    SECURITY_VERIFY_SUCCESS_TITLE("security.verify.success-title"),
    SECURITY_VERIFY_SUCCESS_SUBTITLE("security.verify.success-subtitle"),
    SECURITY_VERIFY_FAILED_TITLE("security.verify.failed-title"),
    SECURITY_VERIFY_FAILED_SUBTITLE("security.verify.failed-subtitle"),

    // Phase 3. Op-command protection: the prompt when a protected command is blocked pending a fresh proof, the
    // reply when the re-auth succeeds and the command is retried, the reply when the submitted code is wrong, and the
    // refusal when the shared lockout blocks any further attempt.
    SECURITY_REAUTH_REQUIRED("security.reauth.required"),
    SECURITY_REAUTH_SUCCESS("security.reauth.success"),
    SECURITY_REAUTH_FAILED("security.reauth.failed"),
    SECURITY_REAUTH_LOCKED_OUT("security.reauth.locked-out"),

    // Phase 4, IP/alt guard: the /alts header/entry/empty lines shown to staff, the staff notice raised when a
    // joining player shares an IP with other accounts, and the kick shown when an IP is over the account cap.
    SECURITY_ALTS_HEADER("security.alts.header"),
    SECURITY_ALTS_ENTRY("security.alts.entry"),
    SECURITY_ALTS_NONE("security.alts.none"),
    SECURITY_ALTS_NOTIFY("security.alts.notify"),
    SECURITY_ALTS_KICKED("security.alts.kicked"),

    // Phase 4. ClientID: the /clientinfo line reporting a player's recorded brand (or that none was seen), the
    // staff notice when a flagged/blocked brand joins, and the kick shown when a client is not allowed.
    SECURITY_CLIENT_INFO("security.client.info"),
    SECURITY_CLIENT_UNKNOWN("security.client.unknown"),
    SECURITY_CLIENT_FLAGGED("security.client.flagged"),
    SECURITY_CLIENT_BLOCKED("security.client.blocked");

    private final String key;

    SecurityMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
