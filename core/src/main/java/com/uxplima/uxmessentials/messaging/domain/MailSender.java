package com.uxplima.uxmessentials.messaging.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Who a piece of mail is from. A player sender carries both a uuid and the name they sent under; a
 * system/console sender carries a name but no uuid, so a server-originated notice still renders an author.
 * The name is preserved at send time so a since-renamed sender still shows the name the recipient would
 * recognise, mirroring the {@code sender} / {@code sender_name} column pair in the messaging migration.
 *
 * @param uuid the sender's account id, or empty for a system/console sender
 * @param name the name the mail was sent under, always present
 */
public record MailSender(Optional<UUID> uuid, String name) {

    public MailSender {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
    }

    /** A player sender, keeping the uuid and the name at send time. */
    public static MailSender player(PlayerRef sender) {
        Objects.requireNonNull(sender, "sender");
        return new MailSender(Optional.of(sender.uuid()), sender.name());
    }

    /** A system/console sender with a display name but no account id. */
    public static MailSender system(String name) {
        return new MailSender(Optional.empty(), Objects.requireNonNull(name, "name"));
    }

    /** Rebuild a sender from a stored uuid (nullable) and name. */
    public static MailSender stored(Optional<UUID> uuid, String name) {
        return new MailSender(uuid, name);
    }

    /** True when a known player account sent this mail (false for a system sender). */
    public boolean isPlayer() {
        return uuid.isPresent();
    }
}
