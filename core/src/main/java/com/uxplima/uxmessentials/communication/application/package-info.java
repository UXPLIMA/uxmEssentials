/**
 * Application layer of the communication context: the use cases that turn the operator's configured policies into
 * rendered output through outbound ports, the {@code CommunicationModule} {@code FeatureModule}, and the
 * {@code CommunicationMessageKey} catalog of the plugin's own strings. {@code ResolveJoinMessage} /
 * {@code ResolveQuitMessage} / {@code ResolveDeathMessage} apply a channel's {@code MessagePolicy} and substitute
 * placeholders; {@code NextAnnouncement} advances the announcer cursor honouring no-immediate-repeat and the
 * min-players gate; {@code InfoRegistry} maps a command name to its {@code InfoPage}; {@code BroadcastOptOut}
 * owns the per-player announcer subscription toggle.
 *
 * <p>The layer keeps a clean split between the plugin's own strings and operator content. The
 * {@code /broadcasttoggle} confirmations, the "no such info page" error, and the announcer-reloaded notice are
 * {@code CommunicationMessageKey}s in both locale catalogs and are parity-checked. The join/quit/death templates,
 * the announcer lines, and the {@code /rules} / {@code /motd} text are operator content carried as raw strings
 * through these use cases and rendered through MiniMessage in the adapter, never {@code MessageKey}s and never
 * parity-checked. No Bukkit, Paper, Kyori, or logging type appears here; the layer is pure Java over the kernel
 * ports.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.communication.application;
