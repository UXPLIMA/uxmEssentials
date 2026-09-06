/**
 * The moderation bounded context's domain, pure Java, no Bukkit/Paper/Adventure/SLF4J imports.
 *
 * <p>It models the DB-backed sanction state that survives restart: a {@link
 * com.uxplima.uxmessentials.moderation.domain.MuteState mute} (none / permanent / timed), a {@link
 * com.uxplima.uxmessentials.moderation.domain.JailState jail sentence} whose timed countdown advances on
 * online time only, a {@link com.uxplima.uxmessentials.moderation.domain.TempbanState tempban} with a
 * wall-clock expiry, the append-only {@link com.uxplima.uxmessentials.moderation.domain.Warn warning}
 * history, and the {@link com.uxplima.uxmessentials.moderation.domain.SeenRecord last-seen/IP} record. The
 * {@link com.uxplima.uxmessentials.moderation.domain.ModerationProfile} aggregate gathers a target's mute,
 * jail and tempban into one value and answers the gate questions the messaging and teleport contexts ask.
 *
 * <p>All time reasoning takes an explicit instant, the domain never reads the wall clock, so the
 * online-only jail countdown and the tempban expiry are deterministic and unit-testable.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.moderation.domain;
