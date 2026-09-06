/**
 * The server-tweaks context's application layer: the {@link com.uxplima.uxmessentials.servertweaks.application.ServerTweaksModule}
 * feature-module identity/enable gate, the {@link com.uxplima.uxmessentials.servertweaks.application.ServerTweaksConfig}
 * typed view of {@code modules/servertweaks/config.conf}, and the
 * {@link com.uxplima.uxmessentials.servertweaks.application.SignedDirectiveQueue}, the plain-Java, per-player buffer
 * of a Velocity proxy's chat/command rulings that the SignedVelocity backend listeners coordinate through. All pure
 * application code. The actual Bukkit-facing effects (the F3-brand plugin message, the Log4j2 console filter, the
 * unsigned-chat re-delivery, the {@code signedvelocity:main} channel) live in the adapter. No Bukkit, Paper, Kyori, or
 * SLF4J.
 */
@NullMarked
package com.uxplima.uxmessentials.servertweaks.application;

import org.jspecify.annotations.NullMarked;
