package desugarer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class Java9SourceDesugarer {
    // Temurin is the Adoptium distribution name used for runtime validation.
    private static final String TEMURIN_VENDOR_IDENTIFIER = "temurin";
    private static final String ADOPTIUM_VENDOR_IDENTIFIER = "adoptium";

    private Java9SourceDesugarer() {}

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

        File input = new File(options.sourcePath);
        if (!input.isFile()) {
            System.err.println("Source file not found: " + input.getAbsolutePath());
            System.exit(1);
        }

        String outputPath = options.outputPath;
        if (outputPath == null && !options.dryRun) {
            outputPath = defaultOutputPath(input);
        }

        String source = readFile(input);
        SourceDesugarer desugarer = new SourceDesugarer();
        String transformed = desugarer.desugar(source, input.getName(), options.verbose);

        if (options.dryRun) {
            System.out.println(transformed);
            return;
        }

        File outputFile = new File(outputPath);
        writeFile(outputFile, transformed);
        System.out.println("Source desugared: " + outputFile.getAbsolutePath());

        if (options.compile) {
            compileOutput(outputFile, options.classPath);
        }
    }

    public static String desugarSource(String source, String fileName) {
        return new SourceDesugarer().desugar(source, fileName, false);
    }

    private static void compileOutput(File outputFile, String classPath) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("javac");
        command.add("-source");
        command.add("8");
        command.add("-target");
        command.add("8");
        if (classPath != null && !classPath.trim().isEmpty()) {
            command.add("-cp");
            command.add(classPath);
        }
        command.add(outputFile.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        Process process = pb.start();
        int exit = process.waitFor();
        if (exit != 0) {
            System.err.println("javac failed with exit code " + exit);
            System.exit(exit);
        }
    }

    private static void requireTemurinRuntime() {
        String vendor = System.getProperty("java.vendor", "");
        String runtime = System.getProperty("java.runtime.name", "");
        String vmVendor = System.getProperty("java.vm.vendor", "");
        String vmName = System.getProperty("java.vm.name", "");
        String combined = (vendor + " " + runtime + " " + vmVendor + " " + vmName)
                .toLowerCase(java.util.Locale.ROOT);
        if (!combined.contains(TEMURIN_VENDOR_IDENTIFIER)
                && !combined.contains(ADOPTIUM_VENDOR_IDENTIFIER)) {
            System.err.println("Unsupported Java runtime detected.");
            System.err.println("This tool is supported only on Eclipse Temurin (Adoptium).");
            System.err.println("Detected: " + vendor + " / " + runtime + " / " + vmVendor);
            System.exit(1);
        }
    }

    private static String defaultOutputPath(File input) {
        String name = input.getName();
        File parent = input.getParentFile();
        if (name.endsWith(".java")) {
            String outputName = name.substring(0, name.length() - 5) + ".java8.java";
            return parent == null ? outputName : new File(parent, outputName).getPath();
        }
        String outputName = name + ".java8";
        return parent == null ? outputName : new File(parent, outputName).getPath();
    }

    private static String readFile(File input) throws IOException {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append("\n");
            }
        }
        return out.toString();
    }

    private static void writeFile(File output, String content) throws IOException {
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create directory: " + parent.getAbsolutePath());
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {
            writer.write(content);
        }
    }

    private static Options parseArgs(String[] args) {
        Options options = new Options();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help":
                case "-h":
                    options.showHelp = true;
                    return options;
                case "--source":
                    if (i + 1 >= args.length) {
                        return null;
                    }
                    options.sourcePath = args[++i];
                    break;
                case "--output":
                    if (i + 1 >= args.length) {
                        return null;
                    }
                    options.outputPath = args[++i];
                    break;
                case "--compile":
                    options.compile = true;
                    break;
                case "--class-path":
                    if (i + 1 >= args.length) {
                        return null;
                    }
                    options.classPath = args[++i];
                    break;
                case "--dry-run":
                    options.dryRun = true;
                    break;
                case "--verbose":
                    options.verbose = true;
                    break;
                default:
                    return null;
            }
        }
        if (options.sourcePath == null) {
            return null;
        }
        if (options.compile && options.dryRun) {
            System.err.println("--compile cannot be used with --dry-run");
            return null;
        }
        return options;
    }

    private static void printUsage() {
        System.err.println("Usage: java -jar desugar9to8.jar --source <file> [--output <file>] [--compile] [--class-path <path>] [--dry-run] [--verbose]");
        System.err.println();
        System.err.println("  --source <file>        Java 9–11 source file to desugar");
        System.err.println("  --output <file>        Output file (default: <name>.java8.java)");
        System.err.println("  --compile              Compile output with javac -source 8 -target 8");
        System.err.println("  --class-path <path>    Classpath to use when compiling");
        System.err.println("  --dry-run              Print transformed source to stdout");
        System.err.println("  --verbose              Print warnings for ambiguous transforms");
    }

    private static final class Options {
        String sourcePath;
        String outputPath;
        String classPath;
        boolean compile;
        boolean dryRun;
        boolean verbose;
        boolean showHelp;
    }
}
