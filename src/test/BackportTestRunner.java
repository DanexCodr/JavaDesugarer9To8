package test;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal test framework and entry point for all j9compat backport tests.
 *
 * <p>Run with:
 * <pre>
 *   javac -source 8 -target 8 -cp build/backport:build/desugarer -d build/test src/test/*.java
 *   java -cp build/backport:build/desugarer:build/test test.BackportTestRunner
 * </pre>
 *
 * <p>Exit code 0 = all tests passed; 1 = at least one failure.
 */
public final class BackportTestRunner {

    static int passed;
    static int failed;
    static final List<String> failures = new ArrayList<>();

    // ── Assertion helpers ────────────────────────────────────────────────────

    public static void assertTrue(boolean condition, String message) {
        if (condition) {
            passed++;
        } else {
            failed++;
            failures.add("FAIL: " + message);
            System.out.println("  FAIL: " + message);
        }
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        boolean ok = (expected == null && actual == null)
                || (expected != null && expected.equals(actual));
        assertTrue(ok, message + " — expected [" + expected + "] but got [" + actual + "]");
    }

    public static void assertThrows(Class<? extends Throwable> type,
                                    Runnable action, String message) {
        try {
            action.run();
            failed++;
            failures.add("FAIL: " + message + " — expected " + type.getSimpleName()
                    + " but no exception was thrown");
            System.out.println("  FAIL: " + message
                    + " — expected " + type.getSimpleName() + " but no exception thrown");
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                passed++;
            } else {
                failed++;
                failures.add("FAIL: " + message + " — expected " + type.getSimpleName()
                        + " but got " + t.getClass().getSimpleName());
                System.out.println("  FAIL: " + message
                        + " — expected " + type.getSimpleName()
                        + " but got " + t.getClass().getSimpleName());
            }
        }
    }

    public static void fail(String message) {
        assertTrue(false, message);
    }

    static void section(String name) {
        System.out.println("\n── " + name + " ──");
    }

    // ── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("=== j9compat Backport Test Suite ===\n");

        ObjectsBackportTest.run();
        CollectionBackportTest.run();
        StreamBackportTest.run();
        PrimitiveStreamBackportTest.run();
        CollectorsBackportTest.run();
        OptionalBackportTest.run();
        OptionalPrimitiveBackportTest.run();
        IOBackportTest.run();
        StringBackportTest.run();
        FilesBackportTest.run();
        PathBackportTest.run();
        PredicateBackportTest.run();
        CompletableFutureBackportTest.run();
        ProcessHandleBackportTest.run();
        FlowBackportTest.run();
        StackWalkerBackportTest.run();
        ModuleBackportTest.run();
        VarHandleBackportTest.run();
        ReflectionBackportTest.run();
        SourceDesugarerTest.run();

        System.out.println("\n=== Results ===");
        System.out.println("Passed : " + passed);
        System.out.println("Failed : " + failed);
        if (!failures.isEmpty()) {
            System.out.println("\nFailures:");
            for (String f : failures) {
                System.out.println("  " + f);
            }
        }
        if (failed > 0) {
            System.exit(1);
        }
        System.out.println("All tests passed.");
    }
}
