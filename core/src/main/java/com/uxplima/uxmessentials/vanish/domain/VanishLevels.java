package com.uxplima.uxmessentials.vanish.domain;

/**
 * The pure PremiumVanish see/use level comparison, in one place so {@link VanishState#canSee} and the adapter's
 * hide/show reconciliation cannot disagree. The rule, verbatim from the PremiumVanish semantics:
 *
 * <blockquote>A viewer sees a vanished player if and only if the viewer's <em>see level</em> is at least the target's
 * <em>use level</em>. A higher-or-equal see level overrides a lower-or-equal use level; a higher use level overrides a
 * lower see level.</blockquote>
 *
 * <p>Levels are resolved from permissions by the adapter, never here. The two anchor points a level resolver maps onto:
 * a viewer with no see permission at all resolves to {@link #NO_SEE_LEVEL} (0), which, because a vanished player is
 * always at least {@link VanishLevel#MIN_LEVEL} (1). Never clears the bar, reproducing the flat "no {@code .see} node
 * means you cannot see vanished players" behaviour; plain {@code .see} / {@code .use} (no numbered node) both map to
 * level 1, so with layered permissions off every vanished player is level 1 and the whole model collapses to that flat
 * rule.
 */
public final class VanishLevels {

    /** The see level of a viewer holding no vanish-see permission: below every use level, so they see no one. */
    public static final int NO_SEE_LEVEL = 0;

    private VanishLevels() {}

    /**
     * Whether a viewer at {@code viewerSeeLevel} sees a target vanished at {@code targetUseLevel}: {@code true} exactly
     * when the see level is at least the use level.
     */
    public static boolean sees(int viewerSeeLevel, VanishLevel targetUseLevel) {
        java.util.Objects.requireNonNull(targetUseLevel, "targetUseLevel");
        return viewerSeeLevel >= targetUseLevel.level();
    }
}
