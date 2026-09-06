package com.uxplima.uxmessentials.api.link;

import org.jspecify.annotations.NullMarked;

/**
 * A presence marker the optional Discord bridge advertises through Bukkit's {@code ServicesManager} once its
 * gateway is actually connected and the host's {@link DiscordLinkConfirmation} seam is wired. That is, once a
 * {@code /link} code can really be redeemed. The host looks the registration up to decide whether issuing a
 * link code is useful: with no bridge connected, a code is a dead end (nothing in Discord redeems it), so the
 * host refuses to mint one and tells the player Discord is not configured.
 *
 * <p>Like {@link DiscordLinkConfirmation}, this contract lives in {@code :api}, the only module both jars
 * depend on, so the two jars see it by the same class identity across the joined classpath, with no
 * compile-time link between them. It is a pure marker: presence is the whole signal, so it carries no methods.
 * The bridge registers exactly one instance after a successful connect and unregisters it on shutdown, so a
 * lookup answering non-null means "installed, connected, and linking-capable right now".
 */
@NullMarked
public interface DiscordBridgePresence {}
