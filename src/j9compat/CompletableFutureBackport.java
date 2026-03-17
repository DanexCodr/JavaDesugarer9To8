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
            TIMEOUT_SCHEDULER.schedule(
                    () -> future.completeExceptionally(new TimeoutException()),
                    timeout, unit);
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
            TIMEOUT_SCHEDULER.schedule(
                    () -> future.complete(value),
                    timeout, unit);
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
     * {@link CompletableFuture} (which implements {@link CompletionStage}).
     */
    public static <U> CompletionStage<U> completedStage(U value) {
        return CompletableFuture.completedFuture(value);
    }

    // ── failedStage ──────────────────────────────────────────────────────────

    /**
     * Backport of {@code CompletableFuture.failedStage(Throwable ex)}.
     *
     * <p>Returns a new {@link CompletionStage} that is already completed
     * exceptionally with the given throwable.
     */
    public static <U> CompletionStage<U> failedStage(Throwable ex) {
        return CompletableFutureBackport.<U>failedFuture(ex);
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
}
