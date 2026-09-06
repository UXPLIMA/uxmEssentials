/**
 * The security context's application layer: the {@link com.uxplima.uxmessentials.security.application.SecurityModule
 * feature module}, its typed config and message keys, and the two-factor enrolment use cases (begin/confirm a TOTP
 * secret, set a PIN, disable a factor). Pure orchestration over the {@code domain} maths and the outbound
 * {@code port}s, no Bukkit, no Adventure, no jOOQ. The join-verification flow, op-command protection and IP/alt
 * guard land in the later phases behind the same module.
 */
@NullMarked
package com.uxplima.uxmessentials.security.application;

import org.jspecify.annotations.NullMarked;
