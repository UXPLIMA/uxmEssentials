package com.uxplima.uxmessentials.security.domain;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Builds the {@code otpauth://totp/...} enrolment URI defined by the Key URI Format that authenticator apps read
 * from a QR code (or a pasted link). It carries the shared secret plus the parameters that must match the server's
 * verification (SHA1, 6 digits, a 30-second period) and the issuer/account label the app shows the user, so the
 * entry in their app reads e.g. "uxmEssentials (Steve)". Pure string assembly with URL-encoded label parts; no
 * platform types beyond {@code java.net.URLEncoder}.
 */
public final class TotpUri {

    private TotpUri() {}

    /**
     * The {@code otpauth://totp/} URI for {@code secret}, labelled with {@code issuer} and {@code account}. The
     * label and issuer are URL-encoded (spaces as {@code %20}, not {@code +}, so a scanner parses the path
     * correctly); the secret is already canonical Base32 and needs no encoding.
     */
    public static String build(String issuer, String account, TwoFactorSecret secret) {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(secret, "secret");
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label
                + "?secret=" + secret.value()
                + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
