package j9compat;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * Java 8-compatible backport of {@link java.lang.ProcessHandle}.
 */
public interface ProcessHandle extends Comparable<ProcessHandle> {

    long pid();

    Optional<ProcessHandle> parent();

    Stream<ProcessHandle> children();

    Stream<ProcessHandle> descendants();

    Info info();

    CompletableFuture<ProcessHandle> onExit();

    boolean supportsNormalTermination();

    boolean destroy();

    boolean destroyForcibly();

    boolean isAlive();

    @Override
    default int compareTo(ProcessHandle other) {
        return Long.compare(pid(), other.pid());
    }

    static Optional<ProcessHandle> of(long pid) {
        return ProcessHandleImpl.of(pid);
    }

    static ProcessHandle current() {
        return ProcessHandleImpl.current();
    }

    static Stream<ProcessHandle> allProcesses() {
        return ProcessHandleImpl.allProcesses();
    }

    static ProcessHandle fromProcess(Process process) {
        return ProcessHandleImpl.fromProcess(process);
    }

    interface Info {
        Optional<String> command();
        Optional<String> commandLine();
        Optional<String[]> arguments();
        Optional<Instant> startInstant();
        Optional<java.time.Duration> totalCpuDuration();
        Optional<String> user();
    }
}

final class ProcessHandleImpl implements ProcessHandle {

    private static final ConcurrentMap<Long, ProcessHandleImpl> HANDLES =
            new ConcurrentHashMap<Long, ProcessHandleImpl>();
    private static final ProcessHandleImpl CURRENT = new ProcessHandleImpl(currentPid(), null, true, null);
    private static volatile CompletableFuture<ProcessHandle> CURRENT_EXIT;

    static {
        if (CURRENT.pid > 0) {
            HANDLES.put(CURRENT.pid, CURRENT);
        }
    }

    private final long pid;
    private final Process process;
    private final boolean current;
    private final ProcessHandleImpl parent;
    private final Info info;

    private ProcessHandleImpl(long pid, Process process, boolean current, ProcessHandleImpl parent) {
        this.pid = pid;
        this.process = process;
        this.current = current;
        this.parent = parent;
        this.info = new InfoImpl(process, current);
    }

    static Optional<ProcessHandle> of(long pid) {
        ProcessHandleImpl handle = HANDLES.get(pid);
        if (handle != null) {
            return Optional.<ProcessHandle>of(handle);
        }
        if (CURRENT.pid == pid && pid > 0) {
            return Optional.<ProcessHandle>of(CURRENT);
        }
        return Optional.<ProcessHandle>empty();
    }

    static ProcessHandle current() {
        return CURRENT;
    }

    static Stream<ProcessHandle> allProcesses() {
        return HANDLES.values().stream().map(h -> (ProcessHandle) h);
    }

    static ProcessHandle fromProcess(Process process) {
        if (process == null) {
            throw new NullPointerException("process");
        }
        long pid = processPid(process);
        ProcessHandleImpl handle = new ProcessHandleImpl(pid, process, false,
                CURRENT.pid > 0 ? CURRENT : null);
        if (pid > 0) {
            HANDLES.put(pid, handle);
        }
        return handle;
    }

    @Override
    public long pid() {
        return pid;
    }

    @Override
    public Optional<ProcessHandle> parent() {
        return Optional.<ProcessHandle>ofNullable(parent);
    }

    @Override
    public Stream<ProcessHandle> children() {
        if (pid <= 0) {
            return Stream.empty();
        }
        return HANDLES.values().stream()
                .filter(handle -> handle.parent != null && handle.parent.pid == pid)
                .map(handle -> (ProcessHandle) handle);
    }

    @Override
    public Stream<ProcessHandle> descendants() {
        if (pid <= 0) {
            return Stream.empty();
        }
        List<ProcessHandle> handles = new ArrayList<>();
        Deque<ProcessHandleImpl> stack = new ArrayDeque<>();
        HANDLES.values().stream()
                .filter(handle -> handle.parent != null && handle.parent.pid == pid)
                .forEach(stack::push);
        while (!stack.isEmpty()) {
            ProcessHandleImpl handle = stack.pop();
            handles.add(handle);
            HANDLES.values().stream()
                    .filter(child -> child.parent != null && child.parent.pid == handle.pid)
                    .forEach(stack::push);
        }
        return handles.stream();
    }

    @Override
    public Info info() {
        return info;
    }

    @Override
    public CompletableFuture<ProcessHandle> onExit() {
        if (process != null) {
            return waitForProcessExit();
        }
        return currentExitFuture();
    }

    @Override
    public boolean supportsNormalTermination() {
        return true;
    }

    @Override
    public boolean destroy() {
        if (process != null) {
            process.destroy();
            return true;
        }
        return false;
    }

    @Override
    public boolean destroyForcibly() {
        if (process != null) {
            process.destroyForcibly();
            return true;
        }
        return false;
    }

    @Override
    public boolean isAlive() {
        return process != null ? process.isAlive() : current;
    }

    @Override
    public int hashCode() {
        return Long.valueOf(pid).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ProcessHandle)) return false;
        ProcessHandle other = (ProcessHandle) obj;
        return pid == other.pid();
    }

    @Override
    public String toString() {
        return "ProcessHandle[pid=" + pid + "]";
    }

    private CompletableFuture<ProcessHandle> waitForProcessExit() {
        final CompletableFuture<ProcessHandle> future = new CompletableFuture<ProcessHandle>();
        Thread waiter = new Thread(() -> {
            try {
                process.waitFor();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                if (pid > 0) {
                    HANDLES.remove(pid);
                }
                future.complete(this);
            }
        }, "process-handle-waiter-" + pid);
        waiter.setDaemon(true);
        waiter.start();
        return future;
    }

    private CompletableFuture<ProcessHandle> currentExitFuture() {
        CompletableFuture<ProcessHandle> future = CURRENT_EXIT;
        if (future != null) {
            return future;
        }
        synchronized (ProcessHandleImpl.class) {
            if (CURRENT_EXIT == null) {
                CURRENT_EXIT = new CompletableFuture<ProcessHandle>();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> CURRENT_EXIT.complete(this)));
            }
            return CURRENT_EXIT;
        }
    }

    private static long currentPid() {
        String name = runtimeName();
        if (name == null) {
            return -1;
        }
        int at = name.indexOf('@');
        if (at > 0) {
            try {
                return Long.parseLong(name.substring(0, at));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private static long processPid(Process process) {
        try {
            java.lang.reflect.Field pidField = process.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            Object value = pidField.get(process);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static String runtimeName() {
        Object runtime = runtimeMxBean();
        if (runtime == null) {
            return null;
        }
        try {
            Object value = runtime.getClass().getMethod("getName").invoke(runtime);
            return value != null ? value.toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    static Long runtimeStartTime() {
        Object runtime = runtimeMxBean();
        if (runtime == null) {
            return null;
        }
        try {
            Object value = runtime.getClass().getMethod("getStartTime").invoke(runtime);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object runtimeMxBean() {
        try {
            Class<?> managementFactory = Class.forName("java.lang.management.ManagementFactory");
            return managementFactory.getMethod("getRuntimeMXBean").invoke(null);
        } catch (Exception ignored) {
            return null;
        }
    }
}

final class InfoImpl implements ProcessHandle.Info {

    private final boolean current;
    private final String commandLine;
    private final String command;
    private final String[] arguments;
    private final Instant startInstant;
    private final String user;

    InfoImpl(Process process, boolean current) {
        this.current = current;
        if (current) {
            this.commandLine = System.getProperty("sun.java.command");
            this.command = deriveCommand(commandLine);
            this.arguments = deriveArguments(commandLine);
            Long startMillis = ProcessHandleImpl.runtimeStartTime();
            this.startInstant = (startMillis != null && startMillis > 0)
                    ? Instant.ofEpochMilli(startMillis)
                    : null;
            this.user = System.getProperty("user.name");
        } else {
            this.commandLine = null;
            this.command = null;
            this.arguments = null;
            this.startInstant = null;
            this.user = null;
        }
    }

    @Override
    public Optional<String> command() {
        return Optional.ofNullable(command);
    }

    @Override
    public Optional<String> commandLine() {
        return Optional.ofNullable(commandLine);
    }

    @Override
    public Optional<String[]> arguments() {
        return Optional.ofNullable(arguments);
    }

    @Override
    public Optional<Instant> startInstant() {
        return Optional.ofNullable(startInstant);
    }

    @Override
    public Optional<java.time.Duration> totalCpuDuration() {
        return Optional.empty();
    }

    @Override
    public Optional<String> user() {
        return Optional.ofNullable(user);
    }

    private static String deriveCommand(String commandLine) {
        if (commandLine == null) {
            return null;
        }
        String[] parts = commandLine.split(" ");
        return parts.length > 0 ? parts[0] : commandLine;
    }

    private static String[] deriveArguments(String commandLine) {
        if (commandLine == null) {
            return null;
        }
        String[] parts = commandLine.split(" ");
        if (parts.length <= 1) {
            return new String[0];
        }
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        return args;
    }
}
