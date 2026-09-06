/**
 * Application layer of the npc bounded context: the {@code NpcModule} feature-module registration, the single
 * import com.uxplima.uxmessentials.shared.application.message.Notifier;
 * {@code /npc} command surface, the create / delete / list / move / skin / command use cases, the
 * {@code Notifier} send surface, the {@code NpcMessageKey} catalog handles, and the {@code NpcRepository} /
 * {@code NpcView} outbound ports (under {@code port/}). The use cases orchestrate the {@code Npc} aggregate
 * through those ports and never touch Bukkit, Paper, Kyori, or logging types. The adapters supply the
 * implementations.
 *
 * <p>Where "the command gate" in these use cases points, and where it does not. {@code /npc} requires
 * {@code uxmessentials.npc.admin} and every verb additionally requires the capability node it is mapped to, with
 * an unmapped verb defaulting to {@code uxmessentials.npc.edit} so a newly added verb is gated rather than open.
 * That gate is the one the per-use-case sentences name. The editor GUI is a second door into the same use cases
 * and does not carry it: it checks {@code uxmessentials.npc.gui} once when it opens and no per-capability node
 * afterwards, so a capability an operator has negated stays reachable through the editor. A new verb needs its
 * capability decided in both places, not one. The GUI side is a known open defect, not a design.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.npc.application;
