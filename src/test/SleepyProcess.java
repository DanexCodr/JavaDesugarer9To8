package test;

/**
 * Helper process that waits briefly so ProcessHandle tests can observe it.
 */
public final class SleepyProcess {

    private SleepyProcess() {}

    public static void main(String[] args) throws Exception {
        Thread.sleep(500);
    }
}
