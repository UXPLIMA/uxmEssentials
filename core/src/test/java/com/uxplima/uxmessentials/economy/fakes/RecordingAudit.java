package com.uxplima.uxmessentials.economy.fakes;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.economy.application.port.EconomyAudit;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A {@link EconomyAudit} that records each audited mutation so a test can assert exactly one line per
 * eco-admin action and one bulk line (with its affected-count) per bulk verb, never per-target spam.
 */
public final class RecordingAudit implements EconomyAudit {

    /** One recorded audit line: its event name, the reason, and the affected count (1 for non-bulk). */
    public record Line(String event, EconomyReason reason, int affected) {}

    private final List<Line> lines = new ArrayList<>();

    @Override
    public void credited(PlayerRef owner, Money amount, EconomyReason reason) {
        lines.add(new Line("economy_credit", reason, 1));
    }

    @Override
    public void debited(PlayerRef owner, Money amount, EconomyReason reason) {
        lines.add(new Line("economy_debit", reason, 1));
    }

    @Override
    public void rejected(PlayerRef owner, Money requested, EconomyReason reason) {
        lines.add(new Line("economy_rejected", reason, 1));
    }

    @Override
    public void transferred(PlayerRef from, PlayerRef to, Money amount, EconomyReason reason) {
        lines.add(new Line("economy_transfer", reason, 1));
    }

    @Override
    public void adminMutation(PlayerRef actor, PlayerRef target, Money amount, EconomyReason reason) {
        lines.add(new Line("economy_admin", reason, 1));
    }

    @Override
    public void bulkMutation(PlayerRef actor, Money amount, int affected, EconomyReason reason) {
        lines.add(new Line("economy_admin_bulk", reason, affected));
    }

    private final List<WorthLine> worthLines = new ArrayList<>();

    /** One recorded worth-override audit line: its event name and the affected material. */
    public record WorthLine(String event, String material) {}

    @Override
    public void worthSet(PlayerRef actor, String material, Money price) {
        worthLines.add(new WorthLine("economy_setworth", material));
    }

    @Override
    public void worthCleared(PlayerRef actor, String material) {
        worthLines.add(new WorthLine("economy_setworth_clear", material));
    }

    /** Every recorded line, in order. */
    public List<Line> lines() {
        return List.copyOf(lines);
    }

    /** Every recorded worth-override line, in order. */
    public List<WorthLine> worthLines() {
        return List.copyOf(worthLines);
    }
}
