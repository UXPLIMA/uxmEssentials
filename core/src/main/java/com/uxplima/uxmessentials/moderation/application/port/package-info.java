/**
 * The moderation context's outbound ports. The seams the pure use cases depend on and the adapters
 * implement.
 *
 * <p>{@link com.uxplima.uxmessentials.moderation.application.port.ModerationRepository} is the DB-backed
 * sanction store (mute/jail/tempban/warn/ip-ban/seen), {@link
 * com.uxplima.uxmessentials.moderation.application.port.ModerationAudit} the {@code
 * com.uxplima.uxmessentials.audit} logger seam, and {@link
 * com.uxplima.uxmessentials.moderation.application.port.Sanctions} the live-player actions (kick, freeze,
 * teleport-to-jail) the sanction use cases drive. The cross-context gates moderation <em>provides</em>
 * the messaging {@code MutePolicy} and the teleport {@code JailGate}. Are implemented under {@code
 * application} so messaging and teleport consume a real policy without a hard edge to this context.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.moderation.application.port;
