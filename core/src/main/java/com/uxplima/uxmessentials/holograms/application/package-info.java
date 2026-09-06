/**
 * The holograms context's use cases and outbound ports. The use cases orchestrate the {@code Hologram}
 * aggregate through the {@code HologramRepository} port, drive the in-world rendering through the
 * {@code HologramView} port (spawn / re-render / move / despawn the native display, realised on the right
 * region thread in the adapter), and render feedback through the {@code Messages}/{@code MessageSink} pair.
 * Holograms are an operator surface, so the single {@code /hologram} command is gated as a whole and the
 * list is unfiltered. The {@code HologramsModule} declares the context's command and enable gate. No Bukkit,
 * Paper, Kyori, or logging type appears here.
 *
 * <p>Where "the command gate" in these use cases points, and where it does not. {@code /hologram} gates each
 * verb on its capability node ({@code uxmessentials.hologram.create}, {@code .delete}, {@code .move},
 * {@code .edit}, {@code .action}, {@code .appearance}, {@code .visibility}), all of which default to held so an
 * operator narrows by negating one. The editor GUI is a second door into the same use cases and carries none of
 * them: it drives create, delete, move, copy, appearance, visibility, lines and the click command with no
 * per-capability check, so a negated capability stays reachable through it. Warps and kits answer this by
 * checking the node inside the use case, where every adapter inherits it; these use cases do not. The GUI side
 * is a known open defect, not a design.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.holograms.application;
