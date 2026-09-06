/**
 * The communication context's Bukkit listeners: {@code ConnectionMessageListener} replaces the vanilla join and
 * quit lines per the configured {@code MessagePolicy} (and broadcasts a first-join welcome on a player's
 * first-ever join); {@code DeathMessageListener} replaces the vanilla death line and optionally shows a dying
 * player a configured info page. Each listener binds the live event's placeholders, asks the matching resolution
 * use case for the operator template, and renders it through MiniMessage. The templates are operator content,
 * never {@code MessageKey}s.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.communication.adapter.inbound.listener;
