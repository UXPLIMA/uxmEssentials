package com.uxplima.uxmessentials.migration.convert.essentialsx.parse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;

/**
 * One parsed EssentialsX {@code userdata/<uuid>.yml} file, source-shaped: the player uuid, the
 * last-known name, the named homes (each a raw {@link EssXLocation}), the optional balance, and the
 * mail lines. Defensive defaults are applied at parse time. A missing balance is {@link
 * Optional#empty()} (mapped to an empty wallet, not a failed record), missing homes/mail are empty
 * lists, so the mapper downstream never has to second-guess a missing key (docs/12-migration §4).
 *
 * @param uuid the player's account identifier
 * @param name the last-known display name
 * @param homes the named homes, by EssentialsX home name
 * @param balance the EssentialsX {@code money} figure, empty when absent
 * @param mail the mailbox lines in source order
 * @param moderation the parsed mute/jail block, {@link EssXModeration#none()} for a clean player
 */
@NullMarked
public record EssXUserdata(
        UUID uuid,
        String name,
        Map<String, EssXLocation> homes,
        Optional<BigDecimal> balance,
        List<String> mail,
        EssXModeration moderation) {

    public EssXUserdata {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        homes = Map.copyOf(Objects.requireNonNull(homes, "homes"));
        Objects.requireNonNull(balance, "balance");
        mail = List.copyOf(Objects.requireNonNull(mail, "mail"));
        Objects.requireNonNull(moderation, "moderation");
    }
}
