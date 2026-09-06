/**
 * Pure domain of the messaging bounded context: the {@code MailBox} aggregate (a recipient's persistent
 * mail), the {@code MailItem} value object, the {@code IgnoreList} aggregate with its {@code IgnoreScope}
 * rules, the {@code LastConversation} reply-target with its time-to-live rule, and the sealed
 * {@code MessagingEvent} family. Private messages themselves are transient and real-time: they never
 * become an aggregate here; only the durable facts (the mailbox and the ignore list) and the reply-target
 * rule are modelled. No Bukkit, Paper, Kyori, or logging type appears here. The model is built from value
 * objects and the cross-cutting kernel primitives ({@code PlayerRef}).
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.messaging.domain;
