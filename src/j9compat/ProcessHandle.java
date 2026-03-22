package j9compat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
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

    private static final JdkProcessHandleSupport JDK_SUPPORT = JdkProcessHandleSupport.create();
    private static final ConcurrentMap<Long, ProcessHandleImpl> HANDLES =
            new ConcurrentHashMap<Long, ProcessHandleImpl>();
    private static final ProcessHandleImpl CURRENT = createCurrent();
    private static volatile CompletableFuture<ProcessHandle> CURRENT_EXIT;
    private static final long TERMINATE_WAIT_SECONDS = 5;

    static {
        if (CURRENT.pid > 0) {
            HANDLES.put(CURRENT.pid, CURRENT);
        }
    }

    private final long pid;
    private final Process process;
    private final boolean current;
    private final Long parentPid;
    private final Info info;

    private ProcessHandleImpl(long pid,
                              Process process,
                              boolean current,
                              Long parentPid,
                              Info info) {
        this.pid = pid;
        this.process = process;
        this.current = current;
        this.parentPid = parentPid;
        this.info = info == null ? new InfoImpl(process, current, null) : info;
    }

    static Optional<ProcessHandle> of(long pid) {
        if (JDK_SUPPORT != null) {
            return JDK_SUPPORT.of(pid);
        }
        return ofFallback(pid);
    }

    static ProcessHandle current() {
        if (JDK_SUPPORT != null) {
            return JDK_SUPPORT.current();
        }
        return currentFallback();
    }

    static Stream<ProcessHandle> allProcesses() {
        if (JDK_SUPPORT != null) {
            return JDK_SUPPORT.allProcesses();
        }
        return allProcessesFallback();
    }

    static ProcessHandle fromProcess(Process process) {
        if (process == null) {
            throw new NullPointerException("process");
        }
        if (JDK_SUPPORT != null) {
            return JDK_SUPPORT.fromProcess(process);
        }
        return fromProcessFallback(process);
    }

    @Override
    public long pid() {
        return pid;
    }

    @Override
    public Optional<ProcessHandle> parent() {
        if (parentPid == null || parentPid <= 0) {
            return Optional.<ProcessHandle>empty();
        }
        return ProcessHandleImpl.of(parentPid);
    }

    @Override
    public Stream<ProcessHandle> children() {
        if (pid <= 0) {
            return Stream.empty();
        }
        ProcessSnapshot snapshot = ProcessSnapshot.capture();
        return snapshot.entries().stream()
                .filter(entry -> entry.parentPid == pid)
                .map(entry -> (ProcessHandle) resolveSnapshotHandle(entry));
    }

    @Override
    public Stream<ProcessHandle> descendants() {
        if (pid <= 0) {
            return Stream.empty();
        }
        ProcessSnapshot snapshot = ProcessSnapshot.capture();
        List<ProcessHandle> handles = new ArrayList<>();
        Deque<ProcessSnapshotEntry> stack = new ArrayDeque<>();
        snapshot.entries().stream()
                .filter(entry -> entry.parentPid == pid)
                .forEach(stack::push);
        while (!stack.isEmpty()) {
            ProcessSnapshotEntry entry = stack.pop();
            handles.add(resolveSnapshotHandle(entry));
            snapshot.entries().stream()
                    .filter(child -> child.parentPid == entry.pid)
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
        if (current) {
            return currentExitFuture();
        }
        return pollForExit();
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
        return terminate(false);
    }

    @Override
    public boolean destroyForcibly() {
        if (process != null) {
            process.destroyForcibly();
            return true;
        }
        return terminate(true);
    }

    @Override
    public boolean isAlive() {
        if (process != null) {
            return process.isAlive();
        }
        if (current) {
            return true;
        }
        if (pid <= 0) {
            return false;
        }
        return ProcessSnapshot.capture().contains(pid);
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

    private static ProcessHandleImpl createCurrent() {
        long pid = currentPid();
        ProcessSnapshot snapshot = ProcessSnapshot.capture();
        ProcessSnapshotEntry entry = snapshot.entry(pid);
        Long parentPid = entry != null && entry.parentPid > 0 ? entry.parentPid : null;
        Info info = new InfoImpl(null, true, entry);
        return new ProcessHandleImpl(pid, null, true, parentPid, info);
    }

    static Optional<ProcessHandle> ofFallback(long pid) {
        ProcessHandleImpl handle = HANDLES.get(pid);
        if (handle != null) {
            return Optional.<ProcessHandle>of(handle);
        }
        if (CURRENT.pid == pid && pid > 0) {
            return Optional.<ProcessHandle>of(CURRENT);
        }
        ProcessSnapshot snapshot = ProcessSnapshot.capture();
        ProcessSnapshotEntry entry = snapshot.entry(pid);
        if (entry != null) {
            return Optional.<ProcessHandle>of(new ProcessHandleImpl(entry.pid, null, false,
                    entry.parentPid, new SnapshotInfo(entry)));
        }
        return Optional.<ProcessHandle>empty();
    }

    static ProcessHandle currentFallback() {
        return CURRENT;
    }

    static Stream<ProcessHandle> allProcessesFallback() {
        ProcessSnapshot snapshot = ProcessSnapshot.capture();
        return snapshot.entries().stream()
                .map(entry -> {
                    ProcessHandleImpl existing = HANDLES.get(entry.pid);
                    if (existing != null) {
                        return (ProcessHandle) existing;
                    }
                    if (CURRENT.pid == entry.pid) {
                        return (ProcessHandle) CURRENT;
                    }
                    return (ProcessHandle) new ProcessHandleImpl(entry.pid, null, false,
                            entry.parentPid, new SnapshotInfo(entry));
                });
    }

    static ProcessHandle fromProcessFallback(Process process) {
        long pid = processPid(process);
        Long parentPid = CURRENT.pid > 0 ? CURRENT.pid : null;
        ProcessSnapshotEntry entry = ProcessSnapshot.capture().entry(pid);
        if (entry != null && entry.parentPid > 0) {
            parentPid = entry.parentPid;
        }
        ProcessHandleImpl handle = new ProcessHandleImpl(pid, process, false,
                parentPid, new InfoImpl(process, false, entry));
        if (pid > 0) {
            HANDLES.put(pid, handle);
        }
        return handle;
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

    private CompletableFuture<ProcessHandle> pollForExit() {
        final CompletableFuture<ProcessHandle> future = new CompletableFuture<ProcessHandle>();
        Thread waiter = new Thread(() -> {
            try {
                while (isAlive()) {
                    Thread.sleep(100);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                future.complete(this);
            }
        }, "process-handle-poll-" + pid);
        waiter.setDaemon(true);
        waiter.start();
        return future;
    }

    private boolean terminate(boolean forcibly) {
        if (pid <= 0) {
            return false;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.US);
        List<String> command = new ArrayList<String>();
        if (os.contains("win")) {
            command.add("taskkill");
            if (forcibly) {
                command.add("/F");
            }
            command.add("/PID");
            command.add(String.valueOf(pid));
        } else {
            command.add("kill");
            command.add(forcibly ? "-9" : "-15");
            command.add(String.valueOf(pid));
        }
        try {
            Process killer = new ProcessBuilder(command).redirectErrorStream(true).start();
            killer.waitFor(TERMINATE_WAIT_SECONDS, TimeUnit.SECONDS);
            return killer.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static ProcessHandleImpl resolveSnapshotHandle(ProcessSnapshotEntry entry) {
        if (entry == null) {
            return null;
        }
        ProcessHandleImpl existing = HANDLES.get(entry.pid);
        if (existing != null) {
            return existing;
        }
        if (CURRENT.pid == entry.pid) {
            return CURRENT;
        }
        return new ProcessHandleImpl(entry.pid, null, false, entry.parentPid, new SnapshotInfo(entry));
    }
}

final class ProcessSnapshot {
    private final Map<Long, ProcessSnapshotEntry> entries;

    private ProcessSnapshot(Map<Long, ProcessSnapshotEntry> entries) {
        this.entries = entries;
    }

    static ProcessSnapshot capture() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.US);
        if (os.contains("win")) {
            return captureWindows();
        }
        return captureUnix();
    }

    boolean contains(long pid) {
        return entries.containsKey(pid);
    }

    ProcessSnapshotEntry entry(long pid) {
        return entries.get(pid);
    }

    List<ProcessSnapshotEntry> entries() {
        return new ArrayList<ProcessSnapshotEntry>(entries.values());
    }

    private static ProcessSnapshot captureUnix() {
        Map<Long, ProcessSnapshotEntry> entries = new HashMap<Long, ProcessSnapshotEntry>();
        List<String> command = new ArrayList<String>();
        command.add("ps");
        command.add("-eo");
        command.add("pid=,ppid=,user=,comm=,args=");
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+", 5);
                if (parts.length < 2) {
                    continue;
                }
                long pid = parseLong(parts[0]);
                long ppid = parseLong(parts[1]);
                String user = parts.length > 2 ? parts[2] : null;
                String commandName = parts.length > 3 ? parts[3] : null;
                String commandLine = parts.length > 4 ? parts[4] : commandName;
                String[] args = splitArguments(commandLine);
                entries.put(pid, new ProcessSnapshotEntry(pid, ppid, commandLine, commandName, args, null, user));
            }
        } catch (IOException ignored) {
        } finally {
            if (process != null) {
                try {
                    process.waitFor();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return new ProcessSnapshot(entries);
    }

    private static ProcessSnapshot captureWindows() {
        Map<Long, ProcessSnapshotEntry> entries = new HashMap<Long, ProcessSnapshotEntry>();
        List<String> command = new ArrayList<String>();
        command.add("wmic");
        command.add("process");
        command.add("get");
        command.add("ProcessId,ParentProcessId,CommandLine,Name");
        command.add("/FORMAT:CSV");
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("Node,")) {
                    continue;
                }
                String[] parts = trimmed.split(",", 5);
                if (parts.length < 5) {
                    continue;
                }
                String commandLine = parts[2].isEmpty() ? parts[3] : parts[2];
                long pid = parseLong(parts[4]);
                long ppid = parseLong(parts[1]);
                String commandName = parts[3];
                String[] args = splitArguments(commandLine);
                entries.put(pid, new ProcessSnapshotEntry(pid, ppid, commandLine, commandName, args, null, null));
            }
        } catch (IOException ignored) {
        } finally {
            if (process != null) {
                try {
                    process.waitFor();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return new ProcessSnapshot(entries);
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String[] splitArguments(String commandLine) {
        if (commandLine == null || commandLine.isEmpty()) {
            return new String[0];
        }
        String[] parts = commandLine.trim().split("\\s+");
        if (parts.length <= 1) {
            return new String[0];
        }
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        return args;
    }
}

final class ProcessSnapshotEntry {
    final long pid;
    final long parentPid;
    final String commandLine;
    final String command;
    final String[] arguments;
    final Instant startInstant;
    final String user;

    ProcessSnapshotEntry(long pid,
                         long parentPid,
                         String commandLine,
                         String command,
                         String[] arguments,
                         Instant startInstant,
                         String user) {
        this.pid = pid;
        this.parentPid = parentPid;
        this.commandLine = commandLine;
        this.command = command;
        this.arguments = arguments;
        this.startInstant = startInstant;
        this.user = user;
    }
}

final class SnapshotInfo implements ProcessHandle.Info {
    private final ProcessSnapshotEntry entry;

    SnapshotInfo(ProcessSnapshotEntry entry) {
        this.entry = entry;
    }

    @Override
    public Optional<String> command() {
        return Optional.ofNullable(entry == null ? null : entry.command);
    }

    @Override
    public Optional<String> commandLine() {
        return Optional.ofNullable(entry == null ? null : entry.commandLine);
    }

    @Override
    public Optional<String[]> arguments() {
        return Optional.ofNullable(entry == null ? null : entry.arguments);
    }

    @Override
    public Optional<Instant> startInstant() {
        return Optional.ofNullable(entry == null ? null : entry.startInstant);
    }

    @Override
    public Optional<java.time.Duration> totalCpuDuration() {
        return Optional.empty();
    }

    @Override
    public Optional<String> user() {
        return Optional.ofNullable(entry == null ? null : entry.user);
    }
}

final class JdkProcessHandleSupport {
    final Class<?> handleClass;
    final Class<?> infoClass;
    final java.lang.reflect.Method currentMethod;
    final java.lang.reflect.Method ofMethod;
    final java.lang.reflect.Method allProcessesMethod;
    final java.lang.reflect.Method toHandleMethod;
    final java.lang.reflect.Method pidMethod;
    final java.lang.reflect.Method parentMethod;
    final java.lang.reflect.Method childrenMethod;
    final java.lang.reflect.Method descendantsMethod;
    final java.lang.reflect.Method infoMethod;
    final java.lang.reflect.Method onExitMethod;
    final java.lang.reflect.Method isAliveMethod;
    final java.lang.reflect.Method destroyMethod;
    final java.lang.reflect.Method destroyForciblyMethod;
    final java.lang.reflect.Method supportsNormalTerminationMethod;
    final java.lang.reflect.Method commandMethod;
    final java.lang.reflect.Method commandLineMethod;
    final java.lang.reflect.Method argumentsMethod;
    final java.lang.reflect.Method startInstantMethod;
    final java.lang.reflect.Method totalCpuDurationMethod;
    final java.lang.reflect.Method userMethod;

    private JdkProcessHandleSupport(Class<?> handleClass, Class<?> infoClass,
                                    java.lang.reflect.Method currentMethod,
                                    java.lang.reflect.Method ofMethod,
                                    java.lang.reflect.Method allProcessesMethod,
                                    java.lang.reflect.Method toHandleMethod,
                                    java.lang.reflect.Method pidMethod,
                                    java.lang.reflect.Method parentMethod,
                                    java.lang.reflect.Method childrenMethod,
                                    java.lang.reflect.Method descendantsMethod,
                                    java.lang.reflect.Method infoMethod,
                                    java.lang.reflect.Method onExitMethod,
                                    java.lang.reflect.Method isAliveMethod,
                                    java.lang.reflect.Method destroyMethod,
                                    java.lang.reflect.Method destroyForciblyMethod,
                                    java.lang.reflect.Method supportsNormalTerminationMethod,
                                    java.lang.reflect.Method commandMethod,
                                    java.lang.reflect.Method commandLineMethod,
                                    java.lang.reflect.Method argumentsMethod,
                                    java.lang.reflect.Method startInstantMethod,
                                    java.lang.reflect.Method totalCpuDurationMethod,
                                    java.lang.reflect.Method userMethod) {
        this.handleClass = handleClass;
        this.infoClass = infoClass;
        this.currentMethod = currentMethod;
        this.ofMethod = ofMethod;
        this.allProcessesMethod = allProcessesMethod;
        this.toHandleMethod = toHandleMethod;
        this.pidMethod = pidMethod;
        this.parentMethod = parentMethod;
        this.childrenMethod = childrenMethod;
        this.descendantsMethod = descendantsMethod;
        this.infoMethod = infoMethod;
        this.onExitMethod = onExitMethod;
        this.isAliveMethod = isAliveMethod;
        this.destroyMethod = destroyMethod;
        this.destroyForciblyMethod = destroyForciblyMethod;
        this.supportsNormalTerminationMethod = supportsNormalTerminationMethod;
        this.commandMethod = commandMethod;
        this.commandLineMethod = commandLineMethod;
        this.argumentsMethod = argumentsMethod;
        this.startInstantMethod = startInstantMethod;
        this.totalCpuDurationMethod = totalCpuDurationMethod;
        this.userMethod = userMethod;
    }

    static JdkProcessHandleSupport create() {
        try {
            Class<?> handleClass = Class.forName("java.lang.ProcessHandle");
            Class<?> infoClass = Class.forName("java.lang.ProcessHandle$Info");
            java.lang.reflect.Method currentMethod = handleClass.getMethod("current");
            java.lang.reflect.Method ofMethod = handleClass.getMethod("of", long.class);
            java.lang.reflect.Method allProcessesMethod = handleClass.getMethod("allProcesses");
            java.lang.reflect.Method toHandleMethod = Process.class.getMethod("toHandle");
            java.lang.reflect.Method pidMethod = handleClass.getMethod("pid");
            java.lang.reflect.Method parentMethod = handleClass.getMethod("parent");
            java.lang.reflect.Method childrenMethod = handleClass.getMethod("children");
            java.lang.reflect.Method descendantsMethod = handleClass.getMethod("descendants");
            java.lang.reflect.Method infoMethod = handleClass.getMethod("info");
            java.lang.reflect.Method onExitMethod = handleClass.getMethod("onExit");
            java.lang.reflect.Method isAliveMethod = handleClass.getMethod("isAlive");
            java.lang.reflect.Method destroyMethod = handleClass.getMethod("destroy");
            java.lang.reflect.Method destroyForciblyMethod = handleClass.getMethod("destroyForcibly");
            java.lang.reflect.Method supportsNormalTerminationMethod = handleClass.getMethod("supportsNormalTermination");
            java.lang.reflect.Method commandMethod = infoClass.getMethod("command");
            java.lang.reflect.Method commandLineMethod = infoClass.getMethod("commandLine");
            java.lang.reflect.Method argumentsMethod = infoClass.getMethod("arguments");
            java.lang.reflect.Method startInstantMethod = infoClass.getMethod("startInstant");
            java.lang.reflect.Method totalCpuDurationMethod = infoClass.getMethod("totalCpuDuration");
            java.lang.reflect.Method userMethod = infoClass.getMethod("user");
            return new JdkProcessHandleSupport(handleClass, infoClass,
                    currentMethod, ofMethod, allProcessesMethod, toHandleMethod,
                    pidMethod, parentMethod, childrenMethod, descendantsMethod,
                    infoMethod, onExitMethod, isAliveMethod, destroyMethod, destroyForciblyMethod,
                    supportsNormalTerminationMethod, commandMethod, commandLineMethod, argumentsMethod,
                    startInstantMethod, totalCpuDurationMethod, userMethod);
        } catch (Exception ignored) {
            return null;
        }
    }

    Optional<ProcessHandle> of(long pid) {
        try {
            @SuppressWarnings("unchecked")
            Optional<Object> optional = (Optional<Object>) ofMethod.invoke(null, pid);
            if (optional.isPresent()) {
                return Optional.<ProcessHandle>of(new JdkProcessHandle(optional.get(), this));
            }
        } catch (Exception ignored) {
        }
        return Optional.<ProcessHandle>empty();
    }

    ProcessHandle current() {
        try {
            Object handle = currentMethod.invoke(null);
            return new JdkProcessHandle(handle, this);
        } catch (Exception e) {
            return ProcessHandleImpl.currentFallback();
        }
    }

    Stream<ProcessHandle> allProcesses() {
        try {
            @SuppressWarnings("unchecked")
            Stream<Object> stream = (Stream<Object>) allProcessesMethod.invoke(null);
            return stream.map(handle -> (ProcessHandle) new JdkProcessHandle(handle, this));
        } catch (Exception e) {
            return Stream.empty();
        }
    }

    ProcessHandle fromProcess(Process process) {
        try {
            Object handle = toHandleMethod.invoke(process);
            return new JdkProcessHandle(handle, this);
        } catch (Exception e) {
            return ProcessHandleImpl.fromProcessFallback(process);
        }
    }
}

final class JdkProcessHandle implements ProcessHandle {
    private final Object handle;
    private final JdkProcessHandleSupport support;

    JdkProcessHandle(Object handle, JdkProcessHandleSupport support) {
        this.handle = handle;
        this.support = support;
    }

    @Override
    public long pid() {
        try {
            return (Long) support.pidMethod.invoke(handle);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public Optional<ProcessHandle> parent() {
        try {
            @SuppressWarnings("unchecked")
            Optional<Object> optional = (Optional<Object>) support.parentMethod.invoke(handle);
            if (optional.isPresent()) {
                return Optional.<ProcessHandle>of(new JdkProcessHandle(optional.get(), support));
            }
        } catch (Exception ignored) {
        }
        return Optional.<ProcessHandle>empty();
    }

    @Override
    public Stream<ProcessHandle> children() {
        try {
            @SuppressWarnings("unchecked")
            Stream<Object> stream = (Stream<Object>) support.childrenMethod.invoke(handle);
            return stream.map(value -> (ProcessHandle) new JdkProcessHandle(value, support));
        } catch (Exception e) {
            return Stream.empty();
        }
    }

    @Override
    public Stream<ProcessHandle> descendants() {
        try {
            @SuppressWarnings("unchecked")
            Stream<Object> stream = (Stream<Object>) support.descendantsMethod.invoke(handle);
            return stream.map(value -> (ProcessHandle) new JdkProcessHandle(value, support));
        } catch (Exception e) {
            return Stream.empty();
        }
    }

    @Override
    public Info info() {
        try {
            Object info = support.infoMethod.invoke(handle);
            return new JdkInfo(info, support);
        } catch (Exception e) {
            return new SnapshotInfo(null);
        }
    }

    @Override
    public CompletableFuture<ProcessHandle> onExit() {
        try {
            @SuppressWarnings("unchecked")
            CompletableFuture<Object> future = (CompletableFuture<Object>) support.onExitMethod.invoke(handle);
            return future.thenApply(value -> (ProcessHandle) new JdkProcessHandle(value, support));
        } catch (Exception e) {
            CompletableFuture<ProcessHandle> failed = new CompletableFuture<ProcessHandle>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    @Override
    public boolean supportsNormalTermination() {
        try {
            return (Boolean) support.supportsNormalTerminationMethod.invoke(handle);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean destroy() {
        try {
            return (Boolean) support.destroyMethod.invoke(handle);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean destroyForcibly() {
        try {
            return (Boolean) support.destroyForciblyMethod.invoke(handle);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isAlive() {
        try {
            return (Boolean) support.isAliveMethod.invoke(handle);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Long.valueOf(pid()).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ProcessHandle)) return false;
        ProcessHandle other = (ProcessHandle) obj;
        return pid() == other.pid();
    }

    @Override
    public String toString() {
        return "ProcessHandle[pid=" + pid() + "]";
    }
}

final class JdkInfo implements ProcessHandle.Info {
    private final Object info;
    private final JdkProcessHandleSupport support;

    JdkInfo(Object info, JdkProcessHandleSupport support) {
        this.info = info;
        this.support = support;
    }

    @Override
    public Optional<String> command() {
        return optionalString(support.commandMethod);
    }

    @Override
    public Optional<String> commandLine() {
        return optionalString(support.commandLineMethod);
    }

    @Override
    public Optional<String[]> arguments() {
        return optionalValue(support.argumentsMethod, String[].class);
    }

    @Override
    public Optional<Instant> startInstant() {
        return optionalValue(support.startInstantMethod, Instant.class);
    }

    @Override
    public Optional<java.time.Duration> totalCpuDuration() {
        return optionalValue(support.totalCpuDurationMethod, java.time.Duration.class);
    }

    @Override
    public Optional<String> user() {
        return optionalString(support.userMethod);
    }

    private Optional<String> optionalString(java.lang.reflect.Method method) {
        return optionalValue(method, String.class);
    }

    private <T> Optional<T> optionalValue(java.lang.reflect.Method method, Class<T> type) {
        try {
            @SuppressWarnings("unchecked")
            Optional<T> value = (Optional<T>) method.invoke(info);
            return value;
        } catch (Exception e) {
            return Optional.empty();
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

    InfoImpl(Process process, boolean current, ProcessSnapshotEntry snapshot) {
        this.current = current;
        if (snapshot != null) {
            this.commandLine = snapshot.commandLine;
            this.command = snapshot.command != null ? snapshot.command : deriveCommand(commandLine);
            this.arguments = snapshot.arguments;
            this.startInstant = snapshot.startInstant;
            this.user = snapshot.user;
        } else if (current) {
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
