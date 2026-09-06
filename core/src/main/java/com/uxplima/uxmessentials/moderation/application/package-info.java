/**
 * The moderation context's application layer — the use cases that orchestrate the domain through the
 * outbound ports, the {@link com.uxplima.uxmessentials.moderation.application.ModerationModule} feature
 * module, and the {@link com.uxplima.uxmessentials.moderation.application.ModerationMessageKey} catalog.
 *
 * <p>Each sanction is one use case ({@code Mute}, {@code Jail}, {@code TempBan}, {@code Kick}, {@code
 * IssueWarn}, {@code BanIp}, {@code Freeze}, {@code Seen}, …) returning a {@code Result} for its modelled
 * failures rather than throwing; the exempt-node check, the duration grammar and the audit emission are
 * shared. This layer also <em>provides</em> the cross-context gates the other contexts consume — {@link
 * com.uxplima.uxmessentials.moderation.application.RepositoryMutePolicy} for messaging and {@link
 * com.uxplima.uxmessentials.moderation.application.RepositoryJailGate} for teleport — implemented against the
 * consuming contexts' ports so the soft couple stays a contract.
 *
 * <p>Where "the command gate" in these use cases points, and where it does not. Each punishment command carries
 * its own node ({@code uxmessentials.moderation.ban}, {@code .banip}, {@code .mute}, {@code .warn},
 * {@code .jail}, {@code .kick}), which is what lets junior staff hold mute and warn without ban. The moderation
 * GUI is a second door gated only on {@code uxmessentials.moderation.gui}, and from it ban, tempban, banip,
 * mute, warn, jail, unban, unmute, unjail and the jail editor are all reachable with no further check. Warps
 * and kits answer this by checking the node inside the use case, where every adapter inherits it; these use
 * cases do not. The GUI side is a known open defect, not a design.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.moderation.application;
