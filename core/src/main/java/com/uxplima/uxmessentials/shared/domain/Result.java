package com.uxplima.uxmessentials.shared.domain;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/**
 * A success-or-failure outcome carrying either a value of type {@code T} or an error of type
 * {@code E}. Application services return this instead of throwing for expected, modelled failures
 * (insufficient funds, an active cooldown, a quota reached) so the caller must handle both arms, the
 * compiler enforces exhaustiveness through the sealed permits and pattern matching.
 *
 * <p>Exceptions remain reserved for genuinely exceptional, unmodelled faults. A modelled failure is a
 * first-class value here, never a thrown control-flow signal.
 *
 * @param <T> the success value type
 * @param <E> the modelled-failure type
 */
public sealed interface Result<T, E> permits Result.Ok, Result.Err {

    /** Wraps a present success value. */
    static <T, E> Result<T, E> ok(T value) {
        return new Ok<>(Objects.requireNonNull(value, "value"));
    }

    /** The empty-success shape: {@code Result<Unit, E>} carrying {@link Unit#INSTANCE}. */
    static <E> Result<Unit, E> ok() {
        return new Ok<>(Unit.INSTANCE);
    }

    /** Wraps a modelled failure. */
    static <T, E> Result<T, E> err(E error) {
        return new Err<>(Objects.requireNonNull(error, "error"));
    }

    boolean isOk();

    default boolean isErr() {
        return !isOk();
    }

    /** The success value if present, else throws, call only after {@link #isOk()}. */
    T orElseThrow();

    /** The error if present, else throws, call only after {@link #isErr()}. */
    E errorOrThrow();

    /** The success value as an {@link Optional}; empty on failure. */
    Optional<T> asValue();

    /** The error as an {@link Optional}; empty on success. */
    Optional<E> asError();

    /** Maps the success value, leaving a failure untouched. */
    <U> Result<U, E> map(Function<? super T, ? extends U> mapper);

    /** Maps the error, leaving a success untouched. */
    <F> Result<T, F> mapErr(Function<? super E, ? extends F> mapper);

    record Ok<T, E>(T value) implements Result<T, E> {

        public Ok {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public boolean isOk() {
            return true;
        }

        @Override
        public T orElseThrow() {
            return value;
        }

        @Override
        public E errorOrThrow() {
            throw new NoSuchElementException("result is ok, not err");
        }

        @Override
        public Optional<T> asValue() {
            return Optional.of(value);
        }

        @Override
        public Optional<E> asError() {
            return Optional.empty();
        }

        @Override
        public <U> Result<U, E> map(Function<? super T, ? extends U> mapper) {
            return Result.ok(mapper.apply(value));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <F> Result<T, F> mapErr(Function<? super E, ? extends F> mapper) {
            // No error to map; the success arm carries no E, so the cast is sound.
            return (Result<T, F>) this;
        }
    }

    record Err<T, E>(E error) implements Result<T, E> {

        public Err {
            Objects.requireNonNull(error, "error");
        }

        @Override
        public boolean isOk() {
            return false;
        }

        @Override
        public T orElseThrow() {
            throw new NoSuchElementException("result is err, not ok");
        }

        @Override
        public E errorOrThrow() {
            return error;
        }

        @Override
        public Optional<T> asValue() {
            return Optional.empty();
        }

        @Override
        public Optional<E> asError() {
            return Optional.of(error);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Result<U, E> map(Function<? super T, ? extends U> mapper) {
            // No success value to map; the failure arm carries no T, so the cast is sound.
            return (Result<U, E>) this;
        }

        @Override
        public <F> Result<T, F> mapErr(Function<? super E, ? extends F> mapper) {
            return Result.err(mapper.apply(error));
        }
    }

    /** Convenience for the common case where the caller only wants the error or {@code null}. */
    default @Nullable E errorOrNull() {
        return asError().orElse(null);
    }
}
