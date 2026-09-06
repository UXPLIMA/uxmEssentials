package com.uxplima.uxmessentials.playerwarps.application.port;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The narrow outbound seam the rent reminder pass uses to leave an owner a piece of durable mail, a rent-due
 * heads-up that survives the owner being offline and is read on their next join. It is expressed purely in this
 * context's own terms (a {@link PlayerRef} recipient plus a {@link MessageKey} and its placeholders), so the
 * player-warps context never imports a messaging type: the adapter behind this port resolves the key in the
 * owner's locale and appends the mail through the messaging store.
 *
 * <p>Delivery is best-effort and fire-and-forget: a mail that cannot be written (messaging storage unavailable)
 * must never break the sweep, so the implementation swallows its own faults into the operator log rather than
 * throwing back across this seam.
 */
public interface RentMailer {

    /** Leave {@code owner} a piece of mail rendered from {@code key} with {@code placeholders} substituted. */
    void mail(PlayerRef owner, MessageKey key, Map<String, String> placeholders);
}
