package com.uxplima.uxmessentials.playerwarps.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.domain.DisplayName;
import com.uxplima.uxmessentials.playerwarps.domain.IconSpec;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpDescription;
import com.uxplima.uxmessentials.playerwarps.domain.WarpEarnings;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The role-gated single-warp edit verbs: happy paths, the metadata/access/price/rename gate, and the two guards. */
class EditPlayerWarpTest {

    private static final PlayerWarpName HUB = PlayerWarpName.of("hub");

    private PlayerWarpTestSupport.Repo repository;
    private PlayerWarpTestSupport.Members members;
    private PlayerWarpTestSupport.Passwords passwords;
    private PlayerWarpTestSupport.Sink sink;
    private EditPlayerWarp edit;
    private PlayerRef owner;
    private PlayerRef coOwner;
    private PlayerRef manager;
    private PlayerWarp warp;

    @BeforeEach
    void setUp() {
        repository = new PlayerWarpTestSupport.Repo();
        members = new PlayerWarpTestSupport.Members();
        passwords = new PlayerWarpTestSupport.Passwords();
        sink = new PlayerWarpTestSupport.Sink();
        edit = new EditPlayerWarp(
                repository,
                new WarpAuthorization(members),
                PlayerWarpTestSupport.notifier(sink),
                passwords,
                PlayerWarpTestSupport.CLOCK);
        owner = PlayerWarpTestSupport.ref("Owner");
        coOwner = PlayerWarpTestSupport.ref("CoOwner");
        manager = PlayerWarpTestSupport.ref("Manager");
        warp = repository.put(PlayerWarpTestSupport.warp(owner, "hub"));
        members.grant(warp.id().orElseThrow(), coOwner.uuid(), WarpRole.CO_OWNER);
        members.grant(warp.id().orElseThrow(), manager.uuid(), WarpRole.MANAGER);
    }

    @Test
    void ownerSetsDisplayNameAndItIsSaved() {
        Result<Unit, PlayerWarpError> result =
                edit.setDisplayName(owner, HUB, Optional.of(DisplayName.of("Central Hub")));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.stored("hub").displayName())
                .map(DisplayName::value)
                .contains("Central Hub");
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.display-name-set"));
    }

    @Test
    void ownerSetsDescriptionIconCategoryAndAccess() {
        assertThat(edit.setDescription(owner, HUB, Optional.of(WarpDescription.of("A busy hub")))
                        .isOk())
                .isTrue();
        assertThat(edit.setIcon(owner, HUB, Optional.of(IconSpec.of("BEACON"))).isOk())
                .isTrue();
        assertThat(edit.setCategory(owner, HUB, Optional.of("social")).isOk()).isTrue();
        assertThat(edit.setAccess(owner, HUB, WarpAccess.PUBLIC).isOk()).isTrue();

        PlayerWarp saved = repository.stored("hub");
        assertThat(saved.description()).map(WarpDescription::value).contains("A busy hub");
        assertThat(saved.icon()).map(IconSpec::value).contains("BEACON");
        assertThat(saved.categoryId()).contains("social");
        assertThat(saved.access()).isEqualTo(WarpAccess.PUBLIC);
    }

    @Test
    void aManagerMaySetMetadataButNotAccessPriceOrRename() {
        assertThat(edit.setDescription(manager, HUB, Optional.of(WarpDescription.of("ok")))
                        .isOk())
                .isTrue();

        assertThat(edit.setAccess(manager, HUB, WarpAccess.PUBLIC).errorOrThrow())
                .isEqualTo(PlayerWarpError.NO_PERMISSION);
        assertThat(edit.setPrice(manager, HUB, WarpCost.of(BigDecimal.TEN)).errorOrThrow())
                .isEqualTo(PlayerWarpError.NO_PERMISSION);
        assertThat(edit.rename(manager, HUB, PlayerWarpName.of("plaza")).errorOrThrow())
                .isEqualTo(PlayerWarpError.NO_PERMISSION);
        // None of the refused verbs mutated the warp.
        assertThat(repository.stored("hub").access()).isEqualTo(WarpAccess.PRIVATE);
        assertThat(repository.stored("hub").price().isFree()).isTrue();
    }

    @Test
    void aCoOwnerMaySetThePrice() {
        Result<Unit, PlayerWarpError> result = edit.setPrice(coOwner, HUB, WarpCost.of(BigDecimal.valueOf(50)));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.stored("hub").price().amount()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.price-set"));
    }

    @Test
    void editingAMissingWarpIsNotFound() {
        Result<Unit, PlayerWarpError> result = edit.setDisplayName(owner, PlayerWarpName.of("ghost"), Optional.empty());

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NOT_FOUND);
    }

    @Test
    void renamingOntoATakenNameIsRefused() {
        repository.put(PlayerWarpTestSupport.warp(PlayerWarpTestSupport.ref("Other"), "plaza"));

        Result<Unit, PlayerWarpError> result = edit.rename(owner, HUB, PlayerWarpName.of("plaza"));

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.NAME_TAKEN);
        assertThat(repository.findByName(HUB)).isPresent();
    }

    @Test
    void renamingOntoAReservedVerbNameIsRefused() {
        // "admin" is a /pwarp verb literal, so a warp under it would be unreachable. The rename is refused before
        // the row is touched, and the collision check runs even though no other warp holds the name.
        Result<Unit, PlayerWarpError> result = edit.rename(owner, HUB, PlayerWarpName.of("admin"));

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.RESERVED_NAME);
        assertThat(repository.findByName(HUB)).isPresent();
        assertThat(repository.findByName(PlayerWarpName.of("admin"))).isEmpty();
    }

    @Test
    void renamingToAFreeNameMovesTheRowInPlace() {
        Result<Unit, PlayerWarpError> result = edit.rename(owner, HUB, PlayerWarpName.of("plaza"));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.findByName(HUB)).isEmpty();
        assertThat(repository.stored("plaza").id()).isEqualTo(warp.id());
    }

    @Test
    void changingCurrencyWhileTheBankIsNonEmptyIsCurrencyLocked() {
        repository.save(PlayerWarpTestSupport.withEarnings(
                repository.stored("hub"), WarpEarnings.of(BigDecimal.valueOf(20), "default")));

        Result<Unit, PlayerWarpError> result = edit.setPrice(owner, HUB, WarpCost.of(BigDecimal.valueOf(5), "gold"));

        assertThat(result.errorOrThrow()).isEqualTo(PlayerWarpError.CURRENCY_LOCKED);
        assertThat(repository.stored("hub").price().currencyId()).isEqualTo("default");
    }

    @Test
    void repricingInTheSameCurrencyIsAllowedEvenWithANonEmptyBank() {
        repository.save(PlayerWarpTestSupport.withEarnings(
                repository.stored("hub"), WarpEarnings.of(BigDecimal.valueOf(20), "default")));

        Result<Unit, PlayerWarpError> result = edit.setPrice(owner, HUB, WarpCost.of(BigDecimal.valueOf(9), "default"));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.stored("hub").price().amount()).isEqualByComparingTo(BigDecimal.valueOf(9));
    }

    @Test
    void changingCurrencyWithAnEmptyBankIsAllowed() {
        Result<Unit, PlayerWarpError> result = edit.setPrice(owner, HUB, WarpCost.of(BigDecimal.valueOf(5), "gold"));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.stored("hub").price().currencyId()).isEqualTo("gold");
    }

    @Test
    void settingAPasswordHandsItToTheStoreAndNeverRendersIt() {
        Result<Unit, PlayerWarpError> result = edit.setPassword(owner, HUB, "hunter2");

        assertThat(result.isOk()).isTrue();
        assertThat(passwords.set).containsExactly("hunter2");
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.password-set"));
        assertThat(sink.delivered).noneMatch(text -> text.contains("hunter2"));
    }

    @Test
    void clearingAPasswordDelegatesToTheStore() {
        Result<Unit, PlayerWarpError> result = edit.clearPassword(owner, HUB);

        assertThat(result.isOk()).isTrue();
        assertThat(passwords.cleared).containsExactly(warp.id().orElseThrow());
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.password-cleared"));
    }

    @Test
    void aManagerMayNotTouchThePassword() {
        assertThat(edit.setPassword(manager, HUB, "x").errorOrThrow()).isEqualTo(PlayerWarpError.NO_PERMISSION);
        assertThat(passwords.set).isEmpty();
    }

    @Test
    void moveHereReAnchorsTheWarp() {
        Result<Unit, PlayerWarpError> result = edit.moveHere(owner, HUB, PlayerWarpTestSupport.at(100, 70, 100));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.stored("hub").location().blockX()).isEqualTo(100);
    }
}
