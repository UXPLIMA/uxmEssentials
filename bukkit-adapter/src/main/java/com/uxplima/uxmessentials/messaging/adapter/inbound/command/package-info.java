/**
 * The messaging context's inbound Brigadier commands: {@code /msg} {@code /reply} {@code /mail}
 * {@code /msgtoggle} {@code /ignore} {@code /unignore} {@code /socialspy} {@code /mailclear} {@code /helpop}
 * (docs/10-feature-modules.md §15.7, NO public chat). Each handler maps its arguments to one use-case call
 * over the constructed {@code MessagingServices}; the vanish-aware {@code /msg} target resolution lives in the
 * shared command support. No domain rule lives here: the use cases own the gates.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.messaging.adapter.inbound.command;
