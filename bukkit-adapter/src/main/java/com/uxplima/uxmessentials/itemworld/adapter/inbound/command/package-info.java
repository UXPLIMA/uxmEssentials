/**
 * The itemworld context's inbound Brigadier command adapters for groups A. Item utils, virtual workstations,
 * and cleanup (docs/10-feature-modules.md §15.10). One class per command literal, each gating on its
 * sub-feature group plus per-command disable, validating its inputs at the boundary, and applying a stateless
 * ACL-thin mutation on the player's region thread through the kernel {@code Scheduler}. The nine virtual
 * workstations share {@link com.uxplima.uxmessentials.itemworld.adapter.inbound.command.WorkstationCommand}
 * parameterised by {@link com.uxplima.uxmessentials.itemworld.adapter.inbound.command.Workstation}; the bulk
 * {@code /give} is the one audit-logged verb in these groups.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;
