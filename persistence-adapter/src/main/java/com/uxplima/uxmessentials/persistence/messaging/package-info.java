/**
 * The messaging context's outbound persistence adapters: the jOOQ {@code MailRepository} over the generated
 * {@code MAIL} table and the jOOQ {@code IgnoreStore} over {@code IGNORES}, with a Caffeine read-cache on the
 * hot ignore-list path. Mail is DB-backed and survives restart (the hard messaging invariant), every mail
 * fact (recipient, sender, sender name, body, send time, read flag) is a first-class column, and the ignore
 * list is one row per {@code (owner, ignored)} with the scope as a first-class column. There is no opaque
 * JSON blob. Mail is text-only; there are no item attachments. SQL is issued only through the typed jOOQ DSL,
 * never string concatenation.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.messaging;
