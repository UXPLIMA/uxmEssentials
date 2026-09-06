package com.uxplima.uxmessentials.economy.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

import com.uxplima.uxmessentials.economy.application.port.BankRepository;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.economy.domain.BankError;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankAction;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankMember;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankRole;
import com.uxplima.uxmessentials.economy.domain.event.BankDeposited;
import com.uxplima.uxmessentials.economy.domain.event.BankWithdrawn;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Orchestrates joint shared banking. The money moves, deposit and withdraw, are single atomic calls into the
 * {@link BankRepository}, where the player's wallet leg and the bank-balance change commit together or not at
 * all, so no in-JVM lock is needed and a persistence failure can never create or lose money. A withdrawal's
 * sufficiency is decided by a guarded {@code UPDATE … WHERE balance >= ?} in the repository, not a JVM compare,
 * so two concurrent withdrawals can never both overdraw the bank.
 *
 * <p>Banks are a native-ledger feature: the atomic move runs through the wallet repository the economy context
 * owns. When the active provider is a foreign economy, the money-moving operations are refused with
 * {@link BankError#PROVIDER_UNSUPPORTED} rather than risking a non-atomic move.
 */
public final class BankService {

    /** The mixed-case alphanumeric alphabet a fresh bank id is drawn from. */
    private static final char[] ID_ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    /** The length of a generated bank id, e.g. {@code eEa12523}. */
    private static final int ID_LENGTH = 8;

    /** How many times a draw is retried when it collides with an existing bank before giving up. */
    private static final int MAX_ID_ATTEMPTS = 5;

    private final BankRepository bankRepository;
    private final TransactionHistory history;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final RandomGenerator rng;
    private final boolean nativeLedger;

    public BankService(
            BankRepository bankRepository,
            TransactionHistory history,
            DomainEventPublisher events,
            Clock clock,
            RandomGenerator rng,
            boolean nativeLedger) {
        this.bankRepository = Objects.requireNonNull(bankRepository, "bankRepository");
        this.history = Objects.requireNonNull(history, "history");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.rng = Objects.requireNonNull(rng, "rng");
        this.nativeLedger = nativeLedger;
    }

    /**
     * Creates a new shared bank account under a system-assigned random 8-character id and returns it so the
     * caller can show the creator the id they were given. The id is drawn from a mixed-case alphanumeric
     * alphabet and retried a bounded number of times if a draw collides with an existing bank; the returned
     * {@link SharedBank} carries the assigned {@link SharedBank#id()}.
     */
    public Result<SharedBank, BankError> createBank(String name, Currency currency, PlayerRef creator) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(creator, "creator");
        Optional<String> id = freshId();
        if (id.isEmpty()) {
            return Result.err(BankError.ID_TAKEN);
        }
        List<BankMember> members = List.of(new BankMember(creator, BankRole.LEADER));
        SharedBank bank = new SharedBank(id.get(), name, Money.zero(currency), creator, members, clock.millis());
        bankRepository.save(bank);
        return Result.ok(bank);
    }

    /** Draw a fresh, unused bank id, retrying on a collision; empty if every bounded attempt was taken. */
    private Optional<String> freshId() {
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            String candidate = randomId();
            if (bankRepository.findById(candidate).isEmpty()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private String randomId() {
        StringBuilder out = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            out.append(ID_ALPHABET[rng.nextInt(ID_ALPHABET.length)]);
        }
        return out.toString();
    }

    /** Deposits money from a player's wallet into a shared bank. */
    public Result<Unit, BankError> deposit(PlayerRef player, String bankId, Money amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(bankId, "bankId");
        Objects.requireNonNull(amount, "amount");
        if (!nativeLedger) {
            return Result.err(BankError.PROVIDER_UNSUPPORTED);
        }

        Optional<SharedBank> found = bankRepository.findById(bankId);
        if (found.isEmpty()) {
            return Result.err(BankError.NOT_FOUND);
        }
        SharedBank bank = found.get();
        if (!bank.hasPermission(player, BankAction.DEPOSIT)) {
            return Result.err(BankError.NO_PERMISSION);
        }

        Result<Unit, BankError> moved = bankRepository.deposit(bankId, player, amount);
        if (moved.isErr()) {
            return Result.err(moved.errorOrThrow());
        }
        history.recordTransfer(player.uuid().toString(), bankId, amount, EconomyReason.BANK_DEPOSIT, clock.millis());
        events.publish(new BankDeposited(bankId, player, amount, bank.balance().plus(amount)));
        return Result.ok();
    }

    /** Withdraws money from a shared bank into a player's wallet. */
    public Result<Unit, BankError> withdraw(PlayerRef player, String bankId, Money amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(bankId, "bankId");
        Objects.requireNonNull(amount, "amount");
        if (!nativeLedger) {
            return Result.err(BankError.PROVIDER_UNSUPPORTED);
        }

        Optional<SharedBank> found = bankRepository.findById(bankId);
        if (found.isEmpty()) {
            return Result.err(BankError.NOT_FOUND);
        }
        SharedBank bank = found.get();
        if (!bank.hasPermission(player, BankAction.WITHDRAW)) {
            return Result.err(BankError.NO_PERMISSION);
        }

        Result<Unit, BankError> moved = bankRepository.withdraw(bankId, player, amount);
        if (moved.isErr()) {
            return Result.err(moved.errorOrThrow());
        }
        history.recordTransfer(bankId, player.uuid().toString(), amount, EconomyReason.BANK_WITHDRAW, clock.millis());
        events.publish(new BankWithdrawn(bankId, player, amount, bank.balance().minus(amount)));
        return Result.ok();
    }

    /** Invites or adds a member to a shared bank account. */
    public Result<Unit, BankError> addMember(PlayerRef actor, String bankId, PlayerRef target, BankRole role) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(bankId, "bankId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(role, "role");

        Optional<SharedBank> found = bankRepository.findById(bankId);
        if (found.isEmpty()) {
            return Result.err(BankError.NOT_FOUND);
        }
        SharedBank bank = found.get();
        if (!bank.hasPermission(actor, BankAction.ADD_MEMBER)) {
            return Result.err(BankError.NO_PERMISSION);
        }
        if (bank.isMember(target)) {
            return Result.err(BankError.ALREADY_MEMBER);
        }
        bankRepository.save(bank.withMember(new BankMember(target, role)));
        return Result.ok();
    }

    /** Removes a member from a shared bank account. */
    public Result<Unit, BankError> removeMember(PlayerRef actor, String bankId, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(bankId, "bankId");
        Objects.requireNonNull(target, "target");

        Optional<SharedBank> found = bankRepository.findById(bankId);
        if (found.isEmpty()) {
            return Result.err(BankError.NOT_FOUND);
        }
        SharedBank bank = found.get();
        if (!bank.hasPermission(actor, BankAction.REMOVE_MEMBER)) {
            return Result.err(BankError.NO_PERMISSION);
        }
        if (!bank.isMember(target)) {
            return Result.err(BankError.NOT_MEMBER);
        }
        boolean targetIsLeader = bank.members().stream()
                .anyMatch(m -> m.player().uuid().equals(target.uuid()) && m.role() == BankRole.LEADER);
        if (targetIsLeader) {
            return Result.err(BankError.CANNOT_REMOVE_LEADER);
        }
        bankRepository.save(bank.withoutMember(target));
        return Result.ok();
    }

    /** Lists all shared bank IDs a player belongs to. */
    public List<String> getBankIdsForPlayer(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        return bankRepository.findBankIdsForPlayer(player.uuid());
    }

    /** Retrieves a bank account by its ID. */
    public Optional<SharedBank> getBank(String id) {
        Objects.requireNonNull(id, "id");
        return bankRepository.findById(id);
    }
}
