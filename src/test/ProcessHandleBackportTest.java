package test;

import j9compat.ProcessHandle;

import java.util.Optional;

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
    }
}
