package desugarer;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;

import java.io.*;
import java.util.*;
import java.util.jar.*;

/**
 * Java 9 → Java 8 Desugarer
 *
 * Processes a JAR compiled with Java 9 (class version 53) and produces a
 * Java 8-compatible JAR (class version 52) by:
 *
 *  1. Downgrading the class-file version from 53 (Java 9) to 52 (Java 8).
 *  2. Downgrading module-info.class entries so they remain present as metadata
 *     (the JPMS module system itself has no Java 8 equivalent).
 *  3. Making private interface methods package-private so that the Java 8
 *     verifier accepts them.
 *  4. Redirecting Java 9-only API calls to the j9compat backport library that
 *     is bundled inside the output JAR.
 *
 * The j9compat backport library covers:
 *  - java.util.List / Set / Map factory methods  (List.of, Set.of, Map.of,
 *    Map.ofEntries, Map.entry, copyOf variants)
 *  - java.util.stream.Stream additions            (takeWhile, dropWhile,
 *    ofNullable, three-argument iterate)
 *  - java.util.stream.IntStream/LongStream/DoubleStream additions
 *    (takeWhile, dropWhile, three-argument iterate)
 *  - java.util.stream.Collectors additions        (filtering, flatMapping)
 *  - java.util.Optional additions                 (ifPresentOrElse, or, stream)
 *  - java.util.OptionalInt/Long/Double additions  (ifPresentOrElse, stream)
 *  - java.io.InputStream additions               (transferTo, readAllBytes,
 *    readNBytes)
 *  - java.util.Objects additions                  (requireNonNullElse,
 *    requireNonNullElseGet, checkIndex)
 *  - java.util.concurrent.CompletableFuture additions  (orTimeout,
 *    completeOnTimeout, failedFuture, completedStage, failedStage,
 *    minimalCompletionStage, newIncompleteFuture, copy)
 *  - java.lang.ProcessHandle / StackWalker / Flow (rewritten to j9compat types)
 *
 * Usage:
 *   java -cp desugar9to8.jar:asm-9.4.jar:asm-commons-9.4.jar:asm-tree-9.4.jar \
 *        desugarer.Java9ToJava8Desugarer <input-java9.jar> <output-java8.jar>
 */
public class Java9ToJava8Desugarer {

    private static final String CACHE_FILE_SUFFIX = ".desugar-cache.properties";
    private static final String CACHE_VERSION_KEY = "__cacheVersion";
    private static final String CACHE_INPUT_KEY = "__inputPath";
    private static final String CACHE_OUTPUT_KEY = "__outputPath";
    private static final String CACHE_VERSION = "1";
    private static final String DEFAULT_CACHE_DIR = "build/.desugar-cache";
    private static final String TEMURIN_TOKEN = "temurin";
    private static final String ADOPTIUM_TOKEN = "adoptium";

    // ── Backport class source-file paths (relative to src/) ─────────────────
    private static final String[] BACKPORT_CLASSES = {
        "j9compat/CollectionBackport.class",
        "j9compat/StreamBackport.class",
        "j9compat/IntStreamBackport.class",
        "j9compat/LongStreamBackport.class",
        "j9compat/DoubleStreamBackport.class",
        "j9compat/OptionalBackport.class",
        "j9compat/OptionalIntBackport.class",
        "j9compat/OptionalLongBackport.class",
        "j9compat/OptionalDoubleBackport.class",
        "j9compat/IOBackport.class",
        "j9compat/ObjectsBackport.class",
        "j9compat/CompletableFutureBackport.class",
        "j9compat/CollectorsBackport.class",
        "j9compat/ProcessHandle.class",
        "j9compat/ProcessHandle$Info.class",
        "j9compat/Flow.class",
        "j9compat/Flow$Publisher.class",
        "j9compat/Flow$Subscriber.class",
        "j9compat/Flow$Subscription.class",
        "j9compat/Flow$Processor.class",
        "j9compat/StackWalker.class",
        "j9compat/StackWalker$Option.class",
        "j9compat/StackWalker$StackFrame.class",
    };

    // ────────────────────────────────────────────────────────────────────────
    //  Entry point
    // ────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        Options options = parseArgs(args);
        if (options == null) {
            printUsage();
            System.exit(1);
        }
        if (options.showHelp) {
            printUsage();
            return;
        }

        requireTemurinRuntime();

        String inputPath = options.inputPath;
        String outputPath = options.outputPath;
        String backportDir = options.backportDir;

        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            System.err.println("Input file not found: " + inputPath);
            System.exit(1);
        }

        System.out.println("=== Java 9 → Java 8 Desugarer ===");
        System.out.println("Input  : " + inputFile.getAbsolutePath());
        System.out.println("Output : " + new File(outputPath).getAbsolutePath());
        System.out.println("Mode   : " + (options.incremental ? "incremental" : "full"));
        if (options.incremental) {
            System.out.println("Cache  : " + options.cacheFile.getAbsolutePath());
        }

        Stats stats = new Stats();
        IncrementalConfig incremental = new IncrementalConfig(options.incremental, options.cacheFile);
        desugarJar(inputFile, new File(outputPath), backportDir, stats, incremental);

        System.out.println();
        System.out.println("── Summary ──────────────────────────────");
        System.out.println("  Classes processed  : " + stats.classesProcessed);
        System.out.println("  Version downgraded : " + stats.versionDowngraded);
        System.out.println("  Module-info kept   : " + stats.moduleInfoProcessed);
        System.out.println("  API calls remapped : " + stats.apiCallsRemapped);
        System.out.println("  Iface priv methods : " + stats.privateIfaceMethods);
        System.out.println("  Other entries kept : " + stats.otherEntries);
        if (options.incremental) {
            System.out.println("  Entries reused     : " + stats.entriesReused);
        }
        System.out.println("Desugaring complete.");
    }

    // ────────────────────────────────────────────────────────────────────────
    //  JAR processing
    // ────────────────────────────────────────────────────────────────────────

    public static void desugarJar(File input, File output,
                                  String backportDir, Stats stats,
                                  IncrementalConfig incremental) throws Exception {

        Set<String> written = new HashSet<>();
        Map<String, String> previousHashes = Collections.emptyMap();
        Map<String, String> currentHashes = new HashMap<>();
        JarFile previousOutputJar = null;
        File previousOutputSnapshot = null;

        if (incremental.enabled) {
            previousHashes = loadCache(incremental.cacheFile, input, output);
            if (!previousHashes.isEmpty() && output.isFile()) {
                previousOutputSnapshot = createOutputSnapshot(output, incremental.cacheFile);
                if (previousOutputSnapshot != null) {
                    previousOutputJar = new JarFile(previousOutputSnapshot);
                }
            }
        }

        try (JarFile jarFile = new JarFile(input);
             JarOutputStream jos = new JarOutputStream(
                     new BufferedOutputStream(new FileOutputStream(output)))) {
            ClassHierarchy hierarchy = buildHierarchy(jarFile);

            // 1. Transform all entries from the input JAR
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.equals("module-info.class")
                        || name.endsWith("/module-info.class")) {
                    stats.moduleInfoProcessed++;
                }

                try (InputStream is = jarFile.getInputStream(entry)) {
                    byte[] bytes = readAllBytes(is);
                    if (incremental.enabled) {
                        String digest = sha256(bytes);
                        currentHashes.put(name, digest);
                        if (previousOutputJar != null) {
                            String previousDigest = previousHashes.get(name);
                            if (digest.equals(previousDigest)) {
                                JarEntry cachedEntry = previousOutputJar.getJarEntry(name);
                                if (cachedEntry != null) {
                                    try (InputStream cachedStream = previousOutputJar.getInputStream(cachedEntry)) {
                                        bytes = readAllBytes(cachedStream);
                                    }
                                    stats.entriesReused++;
                                    writeEntry(jos, name, bytes, written);
                                    continue;
                                }
                            }
                        }
                    }

                    if (name.endsWith(".class")) {
                        ClassTransformResult result = desugarClass(bytes, name, stats, hierarchy);
                        bytes = result.bytes;
                    } else {
                        stats.otherEntries++;
                    }

                    writeEntry(jos, name, bytes, written);
                }
            }

            // 2. Bundle j9compat backport classes (if a compiled dir was given)
            if (backportDir != null) {
                File bpDir = new File(backportDir);
                if (bpDir.isDirectory()) {
                    bundleBackportClasses(bpDir, jos, written, stats);
                } else {
                    System.err.println("Warning: backport-classes-dir not found: " + backportDir);
                }
            }
        } finally {
            if (previousOutputJar != null) {
                previousOutputJar.close();
            }
            if (previousOutputSnapshot != null && previousOutputSnapshot.exists()) {
                if (!previousOutputSnapshot.delete()) {
                    System.err.println("Warning: unable to delete output snapshot: "
                            + previousOutputSnapshot.getAbsolutePath());
                    previousOutputSnapshot.deleteOnExit();
                }
            }
        }

        if (incremental.enabled) {
            saveCache(incremental.cacheFile, input, output, currentHashes);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Class transformation
    // ────────────────────────────────────────────────────────────────────────

    private static ClassHierarchy buildHierarchy(JarFile jarFile) throws IOException {
        ClassHierarchy hierarchy = new ClassHierarchy();
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.endsWith(".class")) {
                continue;
            }
            try (InputStream is = jarFile.getInputStream(entry)) {
                byte[] bytes = readAllBytes(is);
                ClassReader cr = new ClassReader(bytes);
                cr.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(int version, int access, String className,
                                      String signature, String superName,
                                      String[] interfaces) {
                        if (!"module-info".equals(className)) {
                            hierarchy.record(className, superName, interfaces);
                        }
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        return hierarchy;
    }

    public static ClassTransformResult desugarClass(byte[] classBytes,
                                                     String name,
                                                     Stats stats,
                                                     ClassHierarchy hierarchy) {
        try {
            ClassReader cr = new ClassReader(classBytes);
            ClassWriter cw = new SafeClassWriter(cr,
                    ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

            ClassDesugarer cv = new ClassDesugarer(
                    new ClassRemapper(cw, new BackportRemapper()), stats, hierarchy);
            cr.accept(cv, ClassReader.EXPAND_FRAMES);

            stats.classesProcessed++;
            return new ClassTransformResult(cw.toByteArray());
        } catch (Exception e) {
            System.err.println("  [WARN ]  Could not desugar " + name
                    + " (" + e.getMessage() + ") – keeping original bytes");
            stats.classesProcessed++;
            return new ClassTransformResult(classBytes);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Bundle pre-compiled backport classes into the output JAR
    // ────────────────────────────────────────────────────────────────────────

    private static void bundleBackportClasses(File dir,
                                               JarOutputStream jos,
                                               Set<String> written,
                                               Stats stats) throws IOException {
        bundleDir(dir, dir, jos, written, stats);
    }

    private static void bundleDir(File root, File dir,
                                   JarOutputStream jos,
                                   Set<String> written,
                                   Stats stats) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                bundleDir(root, f, jos, written, stats);
            } else if (f.getName().endsWith(".class")) {
                String entryName = root.toURI().relativize(f.toURI()).getPath();
                try (FileInputStream fis = new FileInputStream(f)) {
                    byte[] bytes = readAllBytes(fis);
                    writeEntry(jos, entryName, bytes, written);
                    stats.otherEntries++;
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────────────────────

    private static final class SafeClassWriter extends ClassWriter {
        SafeClassWriter(ClassReader classReader, int flags) {
            super(classReader, flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            try {
                return super.getCommonSuperClass(type1, type2);
            } catch (RuntimeException ignored) {
                return "java/lang/Object";
            }
        }
    }

    static void writeEntry(JarOutputStream jos, String name,
                            byte[] bytes, Set<String> written) throws IOException {
        if (written.contains(name)) return;
        written.add(name);
        JarEntry entry = new JarEntry(name);
        jos.putNextEntry(entry);
        jos.write(bytes);
        jos.closeEntry();
    }

    /** Java 8-compatible equivalent of InputStream.readAllBytes(). */
    static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private static File createOutputSnapshot(File output, File cacheFile) throws IOException {
        File parent = cacheFile != null ? cacheFile.getParentFile() : null;
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            System.err.println("Warning: unable to create cache dir: " + parent.getAbsolutePath());
            parent = null;
        }
        File snapshot = File.createTempFile(output.getName(), ".prev", parent);
        copyFile(output, snapshot);
        return snapshot;
    }

    private static void copyFile(File source, File target) throws IOException {
        try (InputStream is = new FileInputStream(source);
             OutputStream os = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Helper value types
    // ────────────────────────────────────────────────────────────────────────

    public static class ClassTransformResult {
        public final byte[] bytes;
        ClassTransformResult(byte[] bytes) { this.bytes = bytes; }
    }

    public static class Stats {
        public int classesProcessed;
        public int versionDowngraded;
        public int moduleInfoProcessed;
        public int apiCallsRemapped;
        public int privateIfaceMethods;
        public int otherEntries;
        public int entriesReused;
    }

    private static class Options {
        String inputPath;
        String outputPath;
        String backportDir;
        boolean incremental;
        boolean showHelp;
        File cacheDir = new File(DEFAULT_CACHE_DIR);
        File cacheFile;
    }

    private static class IncrementalConfig {
        public final boolean enabled;
        public final File cacheFile;

        IncrementalConfig(boolean enabled, File cacheFile) {
            this.enabled = enabled;
            this.cacheFile = cacheFile;
        }
    }

    private static Options parseArgs(String[] args) {
        Options options = new Options();
        List<String> positional = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--help".equals(arg) || "-h".equals(arg)) {
                options.showHelp = true;
                return options;
            }
            if ("--incremental".equals(arg)) {
                options.incremental = true;
                continue;
            }
            if ("--cache-dir".equals(arg)) {
                if (i + 1 >= args.length) {
                    System.err.println("Missing value for --cache-dir");
                    return null;
                }
                options.cacheDir = new File(args[++i]);
                continue;
            }
            if (arg.startsWith("--")) {
                System.err.println("Unknown option: " + arg);
                return null;
            }
            positional.add(arg);
        }

        if (positional.size() < 2 || positional.size() > 3) {
            return null;
        }
        options.inputPath = positional.get(0);
        options.outputPath = positional.get(1);
        options.backportDir = positional.size() == 3 ? positional.get(2) : null;
        String outputName = new File(options.outputPath).getName();
        options.cacheFile = new File(options.cacheDir, outputName + CACHE_FILE_SUFFIX);
        return options;
    }

    private static void printUsage() {
        System.err.println(
            "Usage: java desugarer.Java9ToJava8Desugarer [--incremental] [--cache-dir <dir>] <input.jar> <output.jar> [backport-classes-dir]");
        System.err.println();
        System.err.println("  <input.jar>            Java 9-compiled JAR to desugar");
        System.err.println("  <output.jar>           Java 8-compatible output JAR");
        System.err.println("  [backport-classes-dir] Optional directory that contains compiled");
        System.err.println("                         j9compat/*.class backport files to bundle");
        System.err.println();
        System.err.println("  --incremental          Enable incremental mode (reuses unchanged output entries)");
        System.err.println("  --cache-dir <dir>      Cache directory (default: build/.desugar-cache)");
    }

    private static void requireTemurinRuntime() {
        String vendor = System.getProperty("java.vendor", "");
        String runtime = System.getProperty("java.runtime.name", "");
        String vmVendor = System.getProperty("java.vm.vendor", "");
        String vmName = System.getProperty("java.vm.name", "");
        String combined = (vendor + " " + runtime + " " + vmVendor + " " + vmName)
                .toLowerCase(Locale.ROOT);
        if (!combined.contains(TEMURIN_TOKEN) && !combined.contains(ADOPTIUM_TOKEN)) {
            System.err.println("Unsupported Java runtime detected.");
            System.err.println("This tool is supported only on Eclipse Temurin (Adoptium).");
            System.err.println("Detected: " + vendor + " / " + runtime + " / " + vmVendor);
            System.exit(1);
        }
    }

    private static Map<String, String> loadCache(File cacheFile, File input, File output) {
        if (!cacheFile.isFile()) {
            return Collections.emptyMap();
        }
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(cacheFile)) {
            props.load(is);
        } catch (IOException e) {
            System.err.println("Warning: failed to read cache file: " + cacheFile.getAbsolutePath());
            return Collections.emptyMap();
        }
        String version = props.getProperty(CACHE_VERSION_KEY);
        String cachedInput = props.getProperty(CACHE_INPUT_KEY);
        String cachedOutput = props.getProperty(CACHE_OUTPUT_KEY);
        if (!CACHE_VERSION.equals(version)) {
            System.err.println("Warning: cache version mismatch, ignoring cache.");
            return Collections.emptyMap();
        }
        if (!input.getAbsolutePath().equals(cachedInput)) {
            System.err.println("Warning: cache input mismatch, ignoring cache.");
            return Collections.emptyMap();
        }
        if (!output.getAbsolutePath().equals(cachedOutput)) {
            System.err.println("Warning: cache output mismatch, ignoring cache.");
            return Collections.emptyMap();
        }
        Map<String, String> hashes = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("__")) {
                continue;
            }
            hashes.put(key, props.getProperty(key));
        }
        return hashes;
    }

    private static void saveCache(File cacheFile, File input, File output,
                                  Map<String, String> hashes) throws IOException {
        File parent = cacheFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            System.err.println("Warning: unable to create cache dir: " + parent.getAbsolutePath());
            return;
        }
        Properties props = new Properties();
        props.setProperty(CACHE_VERSION_KEY, CACHE_VERSION);
        props.setProperty(CACHE_INPUT_KEY, input.getAbsolutePath());
        props.setProperty(CACHE_OUTPUT_KEY, output.getAbsolutePath());
        for (Map.Entry<String, String> entry : hashes.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }
        try (OutputStream os = new FileOutputStream(cacheFile)) {
            props.store(os, "Desugarer incremental cache");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
