package com.uxplima.uxmessentials.messaging.application.port;

import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the persistent ignore list. The list survives restart, ignore-aware {@code /msg}
 * resolution and the async chat-mute path both read it, so it is DB-backed, with {@code (owner, ignored)}
 * as the primary key (a duplicate {@code /ignore} is idempotent at the database) and the {@link IgnoreScope}
 * name as a first-class column.
 *
 * <p>A read returns the owner's whole {@link IgnoreList} aggregate; a write adds or removes one entry. The
 * jOOQ/PDC adapter behind this port may sit behind a Caffeine read-cache; the contract here is the durable
 * source of truth.
 */
public interface IgnoreStore {

    /** The owner's full ignore list. Empty list when the owner ignores no one. */
    IgnoreList load(PlayerRef owner);

    /** Add or re-scope an entry: {@code owner} ignores {@code ignored} under {@code scope}. Idempotent. */
    void ignore(PlayerRef owner, PlayerRef ignored, IgnoreScope scope);

    /** Remove {@code owner}'s ignore entry for {@code ignored}; a no-op when no such entry exists. */
    void unignore(PlayerRef owner, PlayerRef ignored);
}
