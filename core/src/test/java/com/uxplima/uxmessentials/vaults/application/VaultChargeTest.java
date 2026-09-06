package com.uxplima.uxmessentials.vaults.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultEconomy;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * {@code VaultCharge} expresses the economy soft coupling: the charge is skipped entirely when the action is
 * free (zero cost), when no economy provider is wired, or when the player holds {@code uxmessentials.vault.free}
 *, and only otherwise does it consult {@link VaultEconomy}. The withdraw is itself the guarded debit and is
 * the gate: it succeeds and charges, or rejects with {@link VaultError#CANNOT_AFFORD}. A first allocation is a
 * single combined create + open withdrawal so a new vault is never half-charged. The refund deposits only when
 * configured and the player was not bypassed, and surfaces a dropped (capped) refund as {@code false}. A
 * capturing fake economy asserts the exact provider calls.
 */
class VaultChargeTest {

    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Alice");

    @Test
    void bypassNodeMakesEveryActionFreeWithoutTouchingTheEconomy() {
        CapturingEconomy economy = new CapturingEconomy(true);
        VaultCharge charge = new VaultCharge(
                allow("uxmessentials.vault.free"), Optional.of(economy), VaultChargeSettings.of(100, 50, 25));

        assertThat(charge.chargeAllocate(WHO).isOk()).isTrue();
        assertThat(charge.chargeOpen(WHO).isOk()).isTrue();
        assertThat(charge.refund(WHO)).isTrue();

        assertThat(economy.withdrawals).isEmpty();
        assertThat(economy.deposits).isEmpty();
    }

    @Test
    void absentEconomyMakesEveryActionFreeEvenWithAConfiguredCost() {
        VaultCharge charge = new VaultCharge(denyAll(), Optional.empty(), VaultChargeSettings.of(100, 50, 25));

        assertThat(charge.chargeAllocate(WHO).isOk()).isTrue();
        assertThat(charge.chargeOpen(WHO).isOk()).isTrue();
        assertThat(charge.refund(WHO)).isTrue(); // no economy, nothing to deposit, must not throw
    }

    @Test
    void zeroCostSkipsTheEconomyEntirely() {
        CapturingEconomy economy = new CapturingEconomy(true);
        VaultCharge charge = new VaultCharge(denyAll(), Optional.of(economy), VaultChargeSettings.allFree());

        assertThat(charge.chargeAllocate(WHO).isOk()).isTrue();
        assertThat(charge.chargeOpen(WHO).isOk()).isTrue();

        assertThat(economy.withdrawals).isEmpty();
    }

    @Test
    void allocateWithdrawsTheCombinedCreateAndOpenFeeInOneCall() {
        CapturingEconomy economy = new CapturingEconomy(true);
        VaultCharge charge = new VaultCharge(denyAll(), Optional.of(economy), VaultChargeSettings.of(100, 50, 0));

        Result<Unit, VaultError> result = charge.chargeAllocate(WHO);

        assertThat(result.isOk()).isTrue();
        // One indivisible debit of create + open (150), never two separate withdrawals.
        assertThat(economy.withdrawals).containsExactly(BigDecimal.valueOf(150.0));
    }

    @Test
    void anAffordableOpenFeeIsWithdrawnAndSucceeds() {
        CapturingEconomy economy = new CapturingEconomy(true);
        VaultCharge charge = new VaultCharge(denyAll(), Optional.of(economy), VaultChargeSettings.of(0, 50, 0));

        Result<Unit, VaultError> result = charge.chargeOpen(WHO);

        assertThat(result.isOk()).isTrue();
        assertThat(economy.withdrawals).containsExactly(BigDecimal.valueOf(50.0));
    }

    @Test
    void anUnaffordableFeeFailsWithCannotAffordAndWithdrawsNothing() {
        // canAfford true, but the guarded debit still rejects (concurrent spend / min-balance edge): the
        // withdraw result is the gate, so the charge must fail rather than allocate without payment.
        CapturingEconomy economy = new CapturingEconomy(true).withdrawAlwaysFails();
        VaultCharge charge = new VaultCharge(denyAll(), Optional.of(economy), VaultChargeSettings.of(100, 50, 0));

        Result<Unit, VaultError> result = charge.chargeAllocate(WHO);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(VaultError.CANNOT_AFFORD);
    }

    @Test
    void refundDepositsTheConfiguredAmountWhenNotBypassed() {
        CapturingEconomy economy = new CapturingEconomy(true);
        VaultCharge charge = new VaultCharge(denyAll(), Optional.of(economy), VaultChargeSettings.of(0, 0, 25));

        assertThat(charge.refund(WHO)).isTrue();

        assertThat(economy.deposits).containsExactly(BigDecimal.valueOf(25.0));
    }

    @Test
    void refundReportsFalseWhenTheDepositIsRejected() {
        // The refund was attempted but the provider capped it (e.g. max-balance); the boolean surfaces the
        // dropped refund so a caller can log it.
        CapturingEconomy economy = new CapturingEconomy(true).depositAlwaysFails();
        VaultCharge charge = new VaultCharge(denyAll(), Optional.of(economy), VaultChargeSettings.of(0, 0, 25));

        assertThat(charge.refund(WHO)).isFalse();
        assertThat(economy.deposits).containsExactly(BigDecimal.valueOf(25.0)); // the deposit was attempted
    }

    @Test
    void refundIsAnoOpWhenTheRefundIsZero() {
        CapturingEconomy economy = new CapturingEconomy(true);
        VaultCharge charge = new VaultCharge(denyAll(), Optional.of(economy), VaultChargeSettings.of(100, 50, 0));

        assertThat(charge.refund(WHO)).isTrue();

        assertThat(economy.deposits).isEmpty();
    }

    private static Permissions denyAll() {
        return allow("");
    }

    /** A {@code Permissions} that grants only {@code grantedNode} and resolves no quotas. */
    private static Permissions allow(String grantedNode) {
        return new Permissions() {
            @Override
            public boolean has(PlayerRef who, String node) {
                return !grantedNode.isEmpty() && grantedNode.equals(node);
            }

            @Override
            public QuotaResult resolveQuota(
                    PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
                return QuotaResult.limited(configDefault);
            }
        };
    }

    /**
     * A {@link VaultEconomy} recording every withdraw/deposit and answering {@code canAfford} from a flag. The
     * withdraw/deposit success is independently toggleable so the "affordable yet the guarded debit rejects"
     * and "capped refund" paths can be exercised.
     */
    private static final class CapturingEconomy implements VaultEconomy {
        private final boolean canAfford;
        private boolean withdrawSucceeds = true;
        private boolean depositSucceeds = true;
        private final List<BigDecimal> withdrawals = new ArrayList<>();
        private final List<BigDecimal> deposits = new ArrayList<>();

        private CapturingEconomy(boolean canAfford) {
            this.canAfford = canAfford;
        }

        private CapturingEconomy withdrawAlwaysFails() {
            this.withdrawSucceeds = false;
            return this;
        }

        private CapturingEconomy depositAlwaysFails() {
            this.depositSucceeds = false;
            return this;
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount) {
            return canAfford;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount) {
            withdrawals.add(amount);
            return withdrawSucceeds;
        }

        @Override
        public boolean deposit(PlayerRef who, BigDecimal amount) {
            deposits.add(amount);
            return depositSucceeds;
        }
    }
}
