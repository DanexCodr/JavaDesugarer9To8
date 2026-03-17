package test;

import j9compat.CompletableFutureBackport;

import java.util.concurrent.*;

import static test.BackportTestRunner.*;

/**
 * Tests for {@link j9compat.CompletableFutureBackport}.
 *
 * The timeout-based methods ({@code orTimeout}, {@code completeOnTimeout})
 * are the "hardest" because they are inherently concurrent.  Tests use short
 * but realistic timeouts and verify completion within a generous deadline.
 */
public final class CompletableFutureBackportTest {

    static void run() throws Exception {
        section("CompletableFutureBackport – failedFuture");
        testFailedFuture();

        section("CompletableFutureBackport – completedStage");
        testCompletedStage();

        section("CompletableFutureBackport – failedStage");
        testFailedStage();

        section("CompletableFutureBackport – minimalCompletionStage");
        testMinimalCompletionStage();

        section("CompletableFutureBackport – newIncompleteFuture");
        testNewIncompleteFuture();

        section("CompletableFutureBackport – copy");
        testCopy();

        section("CompletableFutureBackport – orTimeout");
        testOrTimeout();

        section("CompletableFutureBackport – completeOnTimeout");
        testCompleteOnTimeout();
    }

    // ── failedFuture ──────────────────────────────────────────────────────────

    static void testFailedFuture() throws Exception {
        RuntimeException ex = new RuntimeException("boom");
        CompletableFuture<String> f = CompletableFutureBackport.failedFuture(ex);

        assertTrue(f.isDone(), "failedFuture: isDone() is true");
        assertTrue(f.isCompletedExceptionally(), "failedFuture: isCompletedExceptionally() is true");

        // Verify the exact cause is wrapped in ExecutionException
        try {
            f.get();
            assertTrue(false, "failedFuture: get() should throw");
        } catch (ExecutionException ee) {
            assertEquals(ex, ee.getCause(), "failedFuture: cause is the original exception");
        }

        // Null throwable rejection
        assertThrows(NullPointerException.class,
                () -> CompletableFutureBackport.failedFuture(null),
                "failedFuture(null): throws NPE");
    }

    // ── completedStage ────────────────────────────────────────────────────────

    static void testCompletedStage() throws Exception {
        CompletionStage<String> stage = CompletableFutureBackport.completedStage("result");
        assertEquals("result", stage.toCompletableFuture().get(),
                "completedStage: value is accessible");
        assertTrue(!(stage instanceof CompletableFuture),
                "completedStage: minimal stage is not a CompletableFuture");

        // Null value is allowed (same as CompletableFuture.completedFuture(null))
        CompletionStage<String> nullStage = CompletableFutureBackport.completedStage(null);
        assertEquals(null, nullStage.toCompletableFuture().get(),
                "completedStage(null): null value is allowed");
    }

    // ── failedStage ───────────────────────────────────────────────────────────

    static void testFailedStage() throws Exception {
        IllegalStateException ex = new IllegalStateException("bad state");
        CompletionStage<Integer> stage = CompletableFutureBackport.failedStage(ex);

        assertTrue(stage.toCompletableFuture().isCompletedExceptionally(),
                "failedStage: isCompletedExceptionally is true");

        try {
            stage.toCompletableFuture().get();
            assertTrue(false, "failedStage: get() should throw");
        } catch (ExecutionException ee) {
            assertEquals(ex, ee.getCause(), "failedStage: cause is original exception");
        }

        // Null throwable rejection
        assertThrows(NullPointerException.class,
                () -> CompletableFutureBackport.failedStage(null),
                "failedStage(null): throws NPE");
        assertTrue(!(stage instanceof CompletableFuture),
                "failedStage: minimal stage is not a CompletableFuture");
    }

    // ── minimalCompletionStage ───────────────────────────────────────────────

    static void testMinimalCompletionStage() throws Exception {
        CompletableFuture<String> base = new CompletableFuture<>();
        CompletionStage<String> stage = CompletableFutureBackport.minimalCompletionStage(base);
        assertTrue(!(stage instanceof CompletableFuture),
                "minimalCompletionStage: wrapper is not CompletableFuture");

        base.complete("done");
        assertEquals("done", stage.toCompletableFuture().get(1, TimeUnit.SECONDS),
                "minimalCompletionStage: completion shared with base future");

        assertThrows(NullPointerException.class,
                () -> CompletableFutureBackport.minimalCompletionStage(null),
                "minimalCompletionStage(null): throws NPE");
    }

    // ── newIncompleteFuture ──────────────────────────────────────────────────

    static void testNewIncompleteFuture() throws Exception {
        CompletableFuture<String> base = new CompletableFuture<>();
        CompletableFuture<String> created = CompletableFutureBackport.newIncompleteFuture(base);
        assertTrue(created != base, "newIncompleteFuture: returns new instance");
        assertTrue(!created.isDone(), "newIncompleteFuture: starts incomplete");

        created.complete("value");
        assertEquals("value", created.get(1, TimeUnit.SECONDS),
                "newIncompleteFuture: completion works");

        assertThrows(NullPointerException.class,
                () -> CompletableFutureBackport.newIncompleteFuture(null),
                "newIncompleteFuture(null): throws NPE");
    }

    // ── copy ──────────────────────────────────────────────────────────────────

    static void testCopy() throws Exception {
        // Normal completion propagates to copy
        CompletableFuture<String> original = new CompletableFuture<>();
        CompletableFuture<String> cp = CompletableFutureBackport.copy(original);
        assertTrue(!cp.isDone(), "copy: not done before original completes");

        original.complete("hello");
        assertEquals("hello", cp.get(1, TimeUnit.SECONDS),
                "copy: normal completion propagated");

        // Exceptional completion: get() unwraps the internal CompletionException,
        // so ee.getCause() is the same as the exception passed to completeExceptionally.
        CompletableFuture<String> origEx = new CompletableFuture<>();
        CompletableFuture<String> cpEx = CompletableFutureBackport.copy(origEx);

        RuntimeException cause = new RuntimeException("original cause");
        origEx.completeExceptionally(cause);

        assertTrue(cpEx.isCompletedExceptionally(),
                "copy: exceptional completion propagated");
        try {
            cpEx.get();
            assertTrue(false, "copy: get() should throw");
        } catch (ExecutionException ee) {
            // Java's get() unwraps the internal CompletionException wrapper,
            // so ee.getCause() is the original exception directly.
            assertEquals(cause, ee.getCause(),
                    "copy: original exception is the cause of ExecutionException");
        }

        // When the original is completed with a CompletionException, get() on
        // the copy unwraps it: ee.getCause() is the CompletionException's own cause.
        CompletableFuture<String> origCE = new CompletableFuture<>();
        CompletableFuture<String> cpCE = CompletableFutureBackport.copy(origCE);

        Exception inner = new Exception("inner");
        CompletionException directCE = new CompletionException("direct", inner);
        origCE.completeExceptionally(directCE);

        assertTrue(cpCE.isCompletedExceptionally(),
                "copy: exceptional completion from CompletionException propagated");
        try {
            cpCE.get();
            assertTrue(false, "copy: get() should throw for CompletionException");
        } catch (ExecutionException ee) {
            // get() unwraps the CompletionException → ee.getCause() == inner
            assertEquals(inner, ee.getCause(),
                    "copy: CompletionException's cause surfaced by get()");
        }

        // Already-completed future
        CompletableFuture<Integer> done = CompletableFuture.completedFuture(99);
        CompletableFuture<Integer> cpDone = CompletableFutureBackport.copy(done);
        assertEquals(99, cpDone.get(100, TimeUnit.MILLISECONDS),
                "copy: already-completed future value propagated immediately");

        // Null future rejection
        assertThrows(NullPointerException.class,
                () -> CompletableFutureBackport.copy(null),
                "copy(null): throws NPE");
    }

    // ── orTimeout ────────────────────────────────────────────────────────────

    static void testOrTimeout() throws Exception {
        // Future completes before timeout → no TimeoutException
        CompletableFuture<String> fast = new CompletableFuture<>();
        CompletableFutureBackport.orTimeout(fast, 500, TimeUnit.MILLISECONDS);
        fast.complete("quick");
        assertEquals("quick", fast.get(200, TimeUnit.MILLISECONDS),
                "orTimeout: future completed before timeout, value preserved");

        // Future does NOT complete before timeout → should complete with TimeoutException
        CompletableFuture<String> slow = new CompletableFuture<>();
        CompletableFutureBackport.orTimeout(slow, 100, TimeUnit.MILLISECONDS);

        try {
            slow.get(2, TimeUnit.SECONDS);
            assertTrue(false, "orTimeout: should have timed out");
        } catch (ExecutionException ee) {
            assertTrue(ee.getCause() instanceof TimeoutException,
                    "orTimeout: cause is TimeoutException");
        }

        // Already-done future is not affected
        CompletableFuture<String> alreadyDone = CompletableFuture.completedFuture("done");
        CompletableFuture<String> returned =
                CompletableFutureBackport.orTimeout(alreadyDone, 50, TimeUnit.MILLISECONDS);
        assertEquals(alreadyDone, returned, "orTimeout: returns same future instance");
        assertEquals("done", returned.get(), "orTimeout: already-done future unchanged");

        // Null checks
        assertThrows(NullPointerException.class,
                () -> CompletableFutureBackport.orTimeout(null, 1, TimeUnit.SECONDS),
                "orTimeout(null future): throws NPE");
        assertThrows(NullPointerException.class,
                () -> CompletableFutureBackport.orTimeout(
                        new CompletableFuture<>(), 1, null),
                "orTimeout(null unit): throws NPE");
    }

    // ── completeOnTimeout ─────────────────────────────────────────────────────

    static void testCompleteOnTimeout() throws Exception {
        // Future does NOT complete before timeout → default value used
        CompletableFuture<String> slow = new CompletableFuture<>();
        CompletableFutureBackport.completeOnTimeout(slow, "default", 100, TimeUnit.MILLISECONDS);

        String result = slow.get(2, TimeUnit.SECONDS);
        assertEquals("default", result, "completeOnTimeout: default value used on timeout");

        // Future completes before timeout → original value preserved
        CompletableFuture<String> fast = new CompletableFuture<>();
        CompletableFutureBackport.completeOnTimeout(fast, "default", 500, TimeUnit.MILLISECONDS);
        fast.complete("original");
        assertEquals("original", fast.get(200, TimeUnit.MILLISECONDS),
                "completeOnTimeout: original value wins when completed before timeout");

        // Returns same future instance
        CompletableFuture<String> f = new CompletableFuture<>();
        CompletableFuture<String> returned =
                CompletableFutureBackport.completeOnTimeout(f, "v", 1, TimeUnit.HOURS);
        assertEquals(f, returned, "completeOnTimeout: returns same future instance");
        f.complete("done"); // clean up

        // Null value is allowed as the default
        CompletableFuture<String> withNull = new CompletableFuture<>();
        CompletableFutureBackport.completeOnTimeout(withNull, null, 100, TimeUnit.MILLISECONDS);
        String nullResult = withNull.get(2, TimeUnit.SECONDS);
        assertEquals(null, nullResult, "completeOnTimeout: null default value allowed");

        // Null checks
        assertThrows(NullPointerException.class,
                () -> CompletableFutureBackport.completeOnTimeout(
                        null, "v", 1, TimeUnit.SECONDS),
                "completeOnTimeout(null future): throws NPE");
        assertThrows(NullPointerException.class,
                () -> CompletableFutureBackport.completeOnTimeout(
                        new CompletableFuture<>(), "v", 1, null),
                "completeOnTimeout(null unit): throws NPE");
    }
}
