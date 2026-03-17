package test;

import j9compat.ProcessHandle;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static test.BackportTestRunner.*;

/**
 * Tests for {@link j9compat.ProcessHandle}.
 */
public final class ProcessHandleBackportTest {

    static void run() {
        section("ProcessHandleBackport");

        ProcessHandle current = ProcessHandle.current();
        long pid = current.pid();
        assertTrue(pid != 0, "ProcessHandle.current: pid is available (non-zero)");
        assertTrue(current.isAlive(), "ProcessHandle.current: isAlive true");
        assertTrue(current.info() != null, "ProcessHandle.current: info available");

        Optional<ProcessHandle> same = ProcessHandle.of(pid);
        if (pid > 0) {
            assertTrue(same.isPresent(), "ProcessHandle.of(current pid): present");
        } else {
            assertTrue(!same.isPresent(), "ProcessHandle.of(unknown pid): empty");
        }

        if (pid > 0) {
            boolean found = ProcessHandle.allProcesses()
                    .anyMatch(handle -> handle.pid() == pid);
            assertTrue(found, "ProcessHandle.allProcesses: contains current pid");
        }

        testChildTracking(current);
        testOnExit();
    }

    private static void testChildTracking(ProcessHandle current) {
        Process process = null;
        try {
            process = spawnSleepProcess();
            ProcessHandle child = ProcessHandle.fromProcess(process);
            long childPid = child.pid();
            if (current.pid() > 0 && childPid > 0) {
                Optional<ProcessHandle> parent = child.parent();
                assertTrue(parent.isPresent(), "ProcessHandle.parent: present for child process");
                assertEquals(current.pid(), parent.get().pid(),
                        "ProcessHandle.parent: matches current pid");

                boolean inChildren = current.children()
                        .anyMatch(handle -> handle.pid() == childPid);
                assertTrue(inChildren, "ProcessHandle.children: includes spawned process");

                boolean inDescendants = current.descendants()
                        .anyMatch(handle -> handle.pid() == childPid);
                assertTrue(inDescendants, "ProcessHandle.descendants: includes spawned process");
            }
        } catch (Exception e) {
            fail("ProcessHandle child tracking threw exception: " + e.getMessage());
        } finally {
            waitForProcess(process);
        }
    }

    private static void testOnExit() {
        Process process = null;
        try {
            process = spawnSleepProcess();
            ProcessHandle handle = ProcessHandle.fromProcess(process);
            handle.onExit().get(5, TimeUnit.SECONDS);
            assertTrue(!handle.isAlive(), "ProcessHandle.onExit: completes after process exit");
        } catch (Exception e) {
            fail("ProcessHandle.onExit threw exception: " + e.getMessage());
        } finally {
            waitForProcess(process);
        }
    }

    private static Process spawnSleepProcess() throws Exception {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + java.io.File.separator + "bin" + java.io.File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        return new ProcessBuilder(javaBin, "-cp", classpath, "test.SleepyProcess")
                .redirectErrorStream(true)
                .start();
    }

    private static void waitForProcess(Process process) {
        if (process == null) {
            return;
        }
        try {
            process.waitFor();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
