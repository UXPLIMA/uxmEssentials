package com.uxplima.uxmessentials.messaging.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.domain.IgnoreEntry;
import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /ignorelist}: report the players the owner is ignoring, the read-only companion to {@code /ignore}
 * and {@code /unignore}, reusing the same persistent {@link IgnoreStore} they write to. An owner who ignores
 * no one gets the empty notice; otherwise the listing opens with a header carrying the count and then names
 * each ignored player in turn, in the stable insertion order {@link IgnoreList#entries()} preserves so a
 * second look gives the same order.
 */
public final class ListIgnores {

    private final IgnoreStore ignores;
    private final Notifier notifier;

    public ListIgnores(IgnoreStore ignores, Notifier notifier) {
        this.ignores = Objects.requireNonNull(ignores, "ignores");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Show {@code owner} who they are ignoring. */
    public void list(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        IgnoreList list = ignores.load(owner);
        if (list.size() == 0) {
            notifier.send(owner, MessagingMessageKey.IGNORE_LIST_EMPTY);
            return;
        }
        notifier.send(owner, MessagingMessageKey.IGNORE_LIST_HEADER, Map.of("count", Integer.toString(list.size())));
        for (IgnoreEntry entry : list.entries()) {
            notifier.send(
                    owner,
                    MessagingMessageKey.IGNORE_LIST_ENTRY,
                    Map.of("player", entry.ignored().name()));
        }
    }
}
