/**
 * The presence context's inbound Brigadier commands: {@code /afk [reason]} and {@code /vanish}
 * (docs/10-feature-modules.md §15.8). Each handler maps its arguments to one use-case call over the constructed
 * {@code PresenceServices}; no domain rule lives here. The {@code MarkAfk} and {@code ToggleVanish} use cases
 * own the transitions, events, broadcasts, and feedback.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.presence.adapter.inbound.command;
