package com.uxplima.uxmessentials.messaging.domain;

/**
 * The kind of traffic an ignore check is being made for. A {@link IgnoreList#blocks} call names the channel
 * so the same list answers "is this private message suppressed?" and "is this mail suppressed?" against the
 * one persisted {@link IgnoreScope}, keeping the channel-vs-scope rule in one place rather than re-derived
 * at every call site.
 */
public enum IgnoreChannel {

    /** A private message, {@code /msg} or {@code /reply}. */
    MESSAGE {
        @Override
        boolean blockedBy(IgnoreScope scope) {
            return scope.blocksMessages();
        }
    },

    /** A piece of mail, {@code /mail send}. */
    MAIL {
        @Override
        boolean blockedBy(IgnoreScope scope) {
            return scope.blocksMail();
        }
    };

    /** Whether an entry under {@code scope} suppresses this channel. */
    abstract boolean blockedBy(IgnoreScope scope);
}
