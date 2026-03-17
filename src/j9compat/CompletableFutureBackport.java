package j9compat;

import java.util.Objects;
import java.util.concurrent.*;

/**
 * Java 8-compatible backport of the {@link java.util.concurrent.CompletableFuture}
 * methods added in Java 9.
 *
 * <h2>Covered methods</h2>
 * <ul>
 *   <li>{@code CompletableFuture.orTimeout(timeout, unit)} – if not already
 *       completed, completes the future with a {@link TimeoutException} after
 *       the specified timeout.</li>
 *   <li>{@code CompletableFuture.completeOnTimeout(value, timeout, unit)} –
 *       if not already completed, completes the future with the given value
 *       after the specified timeout.</li>
 *   <li>{@code CompletableFuture.failedFuture(Throwable)} – returns an
 *       already-completed future that completed exceptionally with the given
 *       throwable.</li>
 *   <li>{@code CompletableFuture.completedStage(value)} – returns an already-
 *       completed {@link CompletionStage} with the given value.</li>
 *   <li>{@code CompletableFuture.failedStage(Throwable)} – returns an already-
 *       completed {@link CompletionStage} that completed exceptionally.</li>
 *   <li>{@code CompletableFuture.minimalCompletionStage()} – returns a minimal
 *       {@link CompletionStage} view of the given future.</li>
 *   <li>{@code CompletableFuture.newIncompleteFuture()} – returns a new
 *       incomplete {@link CompletableFuture}.</li>
 *   <li>{@code CompletableFuture.copy()} – returns a new future that completes
 *       with the same value or exception as this future.</li>
 * </ul>
 *
 * <p>The timeout-based methods use a shared scheduled executor to schedule the
 * timeout task, matching the behaviour described in the Java 9 specification.
 */
public final class CompletableFutureBackport {

    private CompletableFutureBackport() {}

    /** Shared scheduler for timeout tasks (daemon threads, won't block JVM exit). */
    private static final ScheduledExecutorService TIMEOUT_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "j9compat-cf-timeout");
                t.setDaemon(true);
                return t;
            });

    // ── orTimeout ────────────────────────────────────────────────────────────

    /**
     * Backport of {@code CompletableFuture.orTimeout(long timeout, TimeUnit unit)}.
     *
     * <p>If this future is not already completed when the timeout elapses,
     * completes it exceptionally with a {@link TimeoutException}.
     *
     * @return {@code future} itself (for chaining)
     */
    public static <T> CompletableFuture<T> orTimeout(
            CompletableFuture<T> future, long timeout, TimeUnit unit) {
        Objects.requireNonNull(future, "future");
        Objects.requireNonNull(unit,   "unit");

        if (!future.isDone()) {
            ScheduledFuture<?> timeoutTask = TIMEOUT_SCHEDULER.schedule(
                    () -> future.completeExceptionally(new TimeoutException()),
                    timeout, unit);
            future.whenComplete((v, ex) -> timeoutTask.cancel(false));
        }
        return future;
    }

    // ── completeOnTimeout ────────────────────────────────────────────────────

    /**
     * Backport of
     * {@code CompletableFuture.completeOnTimeout(T value, long timeout, TimeUnit unit)}.
     *
     * <p>If this future is not already completed when the timeout elapses,
     * completes it with the given value.
     *
     * @return {@code future} itself (for chaining)
     */
    public static <T> CompletableFuture<T> completeOnTimeout(
            CompletableFuture<T> future, T value, long timeout, TimeUnit unit) {
        Objects.requireNonNull(future, "future");
        Objects.requireNonNull(unit,   "unit");

        if (!future.isDone()) {
            ScheduledFuture<?> timeoutTask = TIMEOUT_SCHEDULER.schedule(
                    () -> future.complete(value),
                    timeout, unit);
            future.whenComplete((v, ex) -> timeoutTask.cancel(false));
        }
        return future;
    }

    // ── failedFuture ─────────────────────────────────────────────────────────

    /**
     * Backport of {@code CompletableFuture.failedFuture(Throwable ex)}.
     *
     * <p>Returns a new {@link CompletableFuture} that is already completed
     * exceptionally with the given throwable.
     */
    public static <T> CompletableFuture<T> failedFuture(Throwable ex) {
        Objects.requireNonNull(ex, "ex");
        CompletableFuture<T> f = new CompletableFuture<>();
        f.completeExceptionally(ex);
        return f;
    }

    // ── completedStage ───────────────────────────────────────────────────────

    /**
     * Backport of {@code CompletableFuture.completedStage(U value)}.
     *
     * <p>Returns a new {@link CompletionStage} that is already completed with
     * the given value and supports only those methods in interface
     * {@link CompletionStage}.  In this backport we return a
     * minimal stage wrapper.
     */
    public static <U> CompletionStage<U> completedStage(U value) {
        return minimalStage(CompletableFuture.completedFuture(value));
    }

    // ── failedStage ──────────────────────────────────────────────────────────

    /**
     * Backport of {@code CompletableFuture.failedStage(Throwable ex)}.
     *
     * <p>Returns a new {@link CompletionStage} that is already completed
     * exceptionally with the given throwable.
     */
    public static <U> CompletionStage<U> failedStage(Throwable ex) {
        return minimalStage(CompletableFutureBackport.<U>failedFuture(ex));
    }

    // ── minimalCompletionStage ────────────────────────────────────────────────

    /**
     * Backport of {@code CompletableFuture.minimalCompletionStage()}.
     *
     * <p>Returns a minimal {@link CompletionStage} view that shares completion
     * with the given future but does not expose {@link CompletableFuture}
     * methods directly.
     */
    public static <U> CompletionStage<U> minimalCompletionStage(CompletableFuture<U> future) {
        Objects.requireNonNull(future, "future");
        return minimalStage(future);
    }

    // ── newIncompleteFuture ───────────────────────────────────────────────────

    /**
     * Backport of {@code CompletableFuture.newIncompleteFuture()}.
     *
     * <p>Returns a new, incomplete {@link CompletableFuture}.
     */
    public static <U> CompletableFuture<U> newIncompleteFuture(CompletableFuture<U> future) {
        Objects.requireNonNull(future, "future");
        return new CompletableFuture<>();
    }

    // ── copy ─────────────────────────────────────────────────────────────────

    /**
     * Backport of {@code CompletableFuture.copy()}.
     *
     * <p>Returns a new {@link CompletableFuture} that completes normally with
     * the same value as this future when it completes normally, and completes
     * exceptionally with a {@link CompletionException} whose cause is the
     * same exception as this future when it completes exceptionally.
     */
    public static <T> CompletableFuture<T> copy(CompletableFuture<T> future) {
        Objects.requireNonNull(future, "future");
        CompletableFuture<T> copy = new CompletableFuture<>();
        future.whenComplete((v, ex) -> {
            if (ex != null) {
                copy.completeExceptionally(
                        ex instanceof CompletionException ? ex
                                : new CompletionException(ex));
            } else {
                copy.complete(v);
            }
        });
        return copy;
    }

    private static <U> CompletionStage<U> minimalStage(CompletableFuture<U> future) {
        return new MinimalStage<>(future);
    }

    private static final class MinimalStage<T> implements CompletionStage<T> {
        private final CompletableFuture<T> future;

        private MinimalStage(CompletableFuture<T> future) {
            this.future = future;
        }

        @Override
        public <U> CompletionStage<U> thenApply(java.util.function.Function<? super T, ? extends U> fn) {
            return future.thenApply(fn);
        }

        @Override
        public <U> CompletionStage<U> thenApplyAsync(java.util.function.Function<? super T, ? extends U> fn) {
            return future.thenApplyAsync(fn);
        }

        @Override
        public <U> CompletionStage<U> thenApplyAsync(java.util.function.Function<? super T, ? extends U> fn,
                                                     Executor executor) {
            return future.thenApplyAsync(fn, executor);
        }

        @Override
        public CompletionStage<Void> thenAccept(java.util.function.Consumer<? super T> action) {
            return future.thenAccept(action);
        }

        @Override
        public CompletionStage<Void> thenAcceptAsync(java.util.function.Consumer<? super T> action) {
            return future.thenAcceptAsync(action);
        }

        @Override
        public CompletionStage<Void> thenAcceptAsync(java.util.function.Consumer<? super T> action,
                                                     Executor executor) {
            return future.thenAcceptAsync(action, executor);
        }

        @Override
        public CompletionStage<Void> thenRun(Runnable action) {
            return future.thenRun(action);
        }

        @Override
        public CompletionStage<Void> thenRunAsync(Runnable action) {
            return future.thenRunAsync(action);
        }

        @Override
        public CompletionStage<Void> thenRunAsync(Runnable action, Executor executor) {
            return future.thenRunAsync(action, executor);
        }

        @Override
        public <U, V> CompletionStage<V> thenCombine(CompletionStage<? extends U> other,
                                                     java.util.function.BiFunction<? super T, ? super U, ? extends V> fn) {
            return future.thenCombine(other, fn);
        }

        @Override
        public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                          java.util.function.BiFunction<? super T, ? super U, ? extends V> fn) {
            return future.thenCombineAsync(other, fn);
        }

        @Override
        public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                          java.util.function.BiFunction<? super T, ? super U, ? extends V> fn,
                                                          Executor executor) {
            return future.thenCombineAsync(other, fn, executor);
        }

        @Override
        public <U> CompletionStage<Void> thenAcceptBoth(CompletionStage<? extends U> other,
                                                        java.util.function.BiConsumer<? super T, ? super U> action) {
            return future.thenAcceptBoth(other, action);
        }

        @Override
        public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                             java.util.function.BiConsumer<? super T, ? super U> action) {
            return future.thenAcceptBothAsync(other, action);
        }

        @Override
        public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                             java.util.function.BiConsumer<? super T, ? super U> action,
                                                             Executor executor) {
            return future.thenAcceptBothAsync(other, action, executor);
        }

        @Override
        public CompletionStage<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
            return future.runAfterBoth(other, action);
        }

        @Override
        public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
            return future.runAfterBothAsync(other, action);
        }

        @Override
        public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
            return future.runAfterBothAsync(other, action, executor);
        }

        @Override
        public <U> CompletionStage<U> applyToEither(CompletionStage<? extends T> other,
                                                    java.util.function.Function<? super T, U> fn) {
            return future.applyToEither(other, fn);
        }

        @Override
        public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other,
                                                         java.util.function.Function<? super T, U> fn) {
            return future.applyToEitherAsync(other, fn);
        }

        @Override
        public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other,
                                                         java.util.function.Function<? super T, U> fn,
                                                         Executor executor) {
            return future.applyToEitherAsync(other, fn, executor);
        }

        @Override
        public CompletionStage<Void> acceptEither(CompletionStage<? extends T> other,
                                                  java.util.function.Consumer<? super T> action) {
            return future.acceptEither(other, action);
        }

        @Override
        public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                                                       java.util.function.Consumer<? super T> action) {
            return future.acceptEitherAsync(other, action);
        }

        @Override
        public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                                                       java.util.function.Consumer<? super T> action,
                                                       Executor executor) {
            return future.acceptEitherAsync(other, action, executor);
        }

        @Override
        public CompletionStage<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
            return future.runAfterEither(other, action);
        }

        @Override
        public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
            return future.runAfterEitherAsync(other, action);
        }

        @Override
        public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action,
                                                         Executor executor) {
            return future.runAfterEitherAsync(other, action, executor);
        }

        @Override
        public <U> CompletionStage<U> thenCompose(java.util.function.Function<? super T, ? extends CompletionStage<U>> fn) {
            return future.thenCompose(fn);
        }

        @Override
        public <U> CompletionStage<U> thenComposeAsync(java.util.function.Function<? super T, ? extends CompletionStage<U>> fn) {
            return future.thenComposeAsync(fn);
        }

        @Override
        public <U> CompletionStage<U> thenComposeAsync(java.util.function.Function<? super T, ? extends CompletionStage<U>> fn,
                                                       Executor executor) {
            return future.thenComposeAsync(fn, executor);
        }

        @Override
        public CompletionStage<T> exceptionally(java.util.function.Function<Throwable, ? extends T> fn) {
            return future.exceptionally(fn);
        }

        @Override
        public CompletionStage<T> whenComplete(java.util.function.BiConsumer<? super T, ? super Throwable> action) {
            return future.whenComplete(action);
        }

        @Override
        public CompletionStage<T> whenCompleteAsync(java.util.function.BiConsumer<? super T, ? super Throwable> action) {
            return future.whenCompleteAsync(action);
        }

        @Override
        public CompletionStage<T> whenCompleteAsync(java.util.function.BiConsumer<? super T, ? super Throwable> action,
                                                    Executor executor) {
            return future.whenCompleteAsync(action, executor);
        }

        @Override
        public <U> CompletionStage<U> handle(java.util.function.BiFunction<? super T, Throwable, ? extends U> fn) {
            return future.handle(fn);
        }

        @Override
        public <U> CompletionStage<U> handleAsync(java.util.function.BiFunction<? super T, Throwable, ? extends U> fn) {
            return future.handleAsync(fn);
        }

        @Override
        public <U> CompletionStage<U> handleAsync(java.util.function.BiFunction<? super T, Throwable, ? extends U> fn,
                                                  Executor executor) {
            return future.handleAsync(fn, executor);
        }

        @Override
        public CompletableFuture<T> toCompletableFuture() {
            return future;
        }
    }
}
