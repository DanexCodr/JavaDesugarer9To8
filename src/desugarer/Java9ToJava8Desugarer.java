package desugarer;

import org.objectweb.asm.*;

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
 *  2. Removing module-info.class entries (the JPMS module system has no Java 8
 *     equivalent).
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
 *  - java.util.Optional additions                 (ifPresentOrElse, or, stream)
 *  - java.io.InputStream additions               (transferTo, readAllBytes,
 *    readNBytes)
 *  - java.util.Objects additions                  (requireNonNullElse,
 *    requireNonNullElseGet, checkIndex)
 *  - java.util.concurrent.CompletableFuture additions  (orTimeout,
 *    completeOnTimeout, failedFuture, completedStage, failedStage)
 *
 * Usage:
 *   java -cp desugar9to8.jar:asm-9.4.jar:asm-commons-9.4.jar:asm-tree-9.4.jar \
 *        desugarer.Java9ToJava8Desugarer <input-java9.jar> <output-java8.jar>
 */
public class Java9ToJava8Desugarer {

    // ── Backport class source-file paths (relative to src/) ─────────────────
    private static final String[] BACKPORT_CLASSES = {
        "j9compat/CollectionBackport.class",
        "j9compat/StreamBackport.class",
        "j9compat/OptionalBackport.class",
        "j9compat/IOBackport.class",
        "j9compat/ObjectsBackport.class",
        "j9compat/CompletableFutureBackport.class",
    };

    // ────────────────────────────────────────────────────────────────────────
    //  Entry point
    // ────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println(
                "Usage: java desugarer.Java9ToJava8Desugarer <input.jar> <output.jar> [backport-classes-dir]");
            System.err.println();
            System.err.println("  <input.jar>            Java 9-compiled JAR to desugar");
            System.err.println("  <output.jar>           Java 8-compatible output JAR");
            System.err.println("  [backport-classes-dir] Optional directory that contains compiled");
            System.err.println("                         j9compat/*.class backport files to bundle");
            System.exit(1);
        }

        String inputPath = args[0];
        String outputPath = args[1];
        String backportDir = args.length >= 3 ? args[2] : null;

        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            System.err.println("Input file not found: " + inputPath);
            System.exit(1);
        }

        System.out.println("=== Java 9 → Java 8 Desugarer ===");
        System.out.println("Input  : " + inputFile.getAbsolutePath());
        System.out.println("Output : " + new File(outputPath).getAbsolutePath());

        Stats stats = new Stats();
        desugarJar(inputFile, new File(outputPath), backportDir, stats);

        System.out.println();
        System.out.println("── Summary ──────────────────────────────");
        System.out.println("  Classes processed  : " + stats.classesProcessed);
        System.out.println("  Version downgraded : " + stats.versionDowngraded);
        System.out.println("  Module-info skipped: " + stats.moduleInfoSkipped);
        System.out.println("  API calls remapped : " + stats.apiCallsRemapped);
        System.out.println("  Iface priv methods : " + stats.privateIfaceMethods);
        System.out.println("  Other entries kept : " + stats.otherEntries);
        System.out.println("Desugaring complete.");
    }

    // ────────────────────────────────────────────────────────────────────────
    //  JAR processing
    // ────────────────────────────────────────────────────────────────────────

    public static void desugarJar(File input, File output,
                                  String backportDir, Stats stats) throws Exception {

        Set<String> written = new HashSet<>();

        try (JarFile jarFile = new JarFile(input);
             JarOutputStream jos = new JarOutputStream(
                     new BufferedOutputStream(new FileOutputStream(output)))) {

            // 1. Transform all entries from the input JAR
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                // Skip module-info at any nesting depth
                if (name.equals("module-info.class")
                        || name.endsWith("/module-info.class")) {
                    System.out.println("  [SKIP ]  " + name);
                    stats.moduleInfoSkipped++;
                    continue;
                }

                try (InputStream is = jarFile.getInputStream(entry)) {
                    byte[] bytes = readAllBytes(is);

                    if (name.endsWith(".class")) {
                        ClassTransformResult result = desugarClass(bytes, name, stats);
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
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Class transformation
    // ────────────────────────────────────────────────────────────────────────

    public static ClassTransformResult desugarClass(byte[] classBytes,
                                                     String name,
                                                     Stats stats) {
        try {
            ClassReader cr = new ClassReader(classBytes);
            ClassWriter cw = new ClassWriter(cr,
                    ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

            ClassDesugarer cv = new ClassDesugarer(cw, stats);
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
        public int moduleInfoSkipped;
        public int apiCallsRemapped;
        public int privateIfaceMethods;
        public int otherEntries;
    }
}
