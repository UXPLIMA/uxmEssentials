/**
 * The security context's inbound Brigadier commands: {@code /2fa} (setup / confirm / disable) and {@code /pin set}.
 * Each handler does its cheap gating on the tick thread and hops the expensive crypto and DB work (PBKDF2 hashing,
 * AES encryption, the {@code security_2fa} read/write) onto the {@link
 * com.uxplima.uxmessentials.shared.application.port.Scheduler} port, delivering the outcome back through the message
 * sink. No player-facing literal is inline. Every line resolves through {@link
 * com.uxplima.uxmessentials.security.application.SecurityMessageKey}.
 */
@NullMarked
package com.uxplima.uxmessentials.security.adapter.inbound.command;

import org.jspecify.annotations.NullMarked;
