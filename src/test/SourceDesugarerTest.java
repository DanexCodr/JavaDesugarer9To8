package test;

import desugarer.SourceDesugarer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public final class SourceDesugarerTest {
    public static void run() {
        BackportTestRunner.section("SourceDesugarer – private interface methods");
        testPrivateInterfaceMethod();

        BackportTestRunner.section("SourceDesugarer – diamond operator");
        testDiamondOperator();

        BackportTestRunner.section("SourceDesugarer – try-with-resources");
        testTryWithResources();

        BackportTestRunner.section("SourceDesugarer – API calls + imports");
        testApiCalls();

        BackportTestRunner.section("SourceDesugarer – static imports");
        testStaticImport();

        BackportTestRunner.section("SourceDesugarer – module-info");
        testModuleInfo();

        BackportTestRunner.section("SourceDesugarer – external Java 11 sources");
        testExternalSourcesCompile();
    }

    private static void testPrivateInterfaceMethod() {
        String input = "public interface Calculator {\n"
                + "    private int add(int a, int b) {\n"
                + "        return a + b;\n"
                + "    }\n"
                + "\n"
                + "    default int multiply(int a, int b) {\n"
                + "        int sum = 0;\n"
                + "        for (int i = 0; i < b; i++) {\n"
                + "            sum = add(sum, a);\n"
                + "        }\n"
                + "        return sum;\n"
                + "    }\n"
                + "}\n";

        String output = new SourceDesugarer().desugar(input, "Calculator.java", false);
        BackportTestRunner.assertTrue(output.contains("class CalculatorHelper"),
                "Helper class inserted");
        BackportTestRunner.assertTrue(output.contains("static int add(Calculator __desugar_j9_interface_self__, int a, int b)"),
                "Private method moved to helper with self param");
        BackportTestRunner.assertTrue(output.contains("CalculatorHelper.add(this, sum, a)"),
                "Private method call rewritten");
    }

    private static void testDiamondOperator() {
        String input = "import java.util.Comparator;\n"
                + "public class Example {\n"
                + "    Comparator<String> cmp = new Comparator<>() {\n"
                + "        @Override\n"
                + "        public int compare(String a, String b) {\n"
                + "            return a.length() - b.length();\n"
                + "        }\n"
                + "    };\n"
                + "}\n";

        String output = new SourceDesugarer().desugar(input, "Example.java", false);
        BackportTestRunner.assertTrue(output.contains("new Comparator<String>()"),
                "Diamond operator replaced with explicit type args");
    }

    private static void testTryWithResources() {
        String input = "class Example {\n"
                + "    void test(Resource r1, Resource r2) throws Exception {\n"
                + "        try (r1; r2) {\n"
                + "            r1.use();\n"
                + "            r2.use();\n"
                + "        }\n"
                + "    }\n"
                + "}\n";

        String output = new SourceDesugarer().desugar(input, "Example.java", false);
        BackportTestRunner.assertTrue(output.contains("try (AutoCloseable __desugar_resource_0 = r1; AutoCloseable __desugar_resource_1 = r2)"),
                "Try-with-resources rewritten with AutoCloseable variables");
    }

    private static void testApiCalls() {
        String input = "import java.nio.file.Files;\n"
                + "import java.nio.file.Path;\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n"
                + "import java.util.Optional;\n"
                + "import java.util.function.Predicate;\n"
                + "import java.util.stream.Stream;\n"
                + "public class Example {\n"
                + "    void test() {\n"
                + "        List<String> list = List.of(\"a\", \"b\");\n"
                + "        Map<String, Integer> map = Map.of(\"k\", 1);\n"
                + "        Stream<String> stream = Stream.ofNullable(\"x\");\n"
                + "        Optional<String> opt = Optional.empty();\n"
                + "        opt.ifPresentOrElse(v -> {}, () -> {});\n"
                + "        opt.isEmpty();\n"
                + "        String raw = \"  hi \";\n"
                + "        String stripped = raw.strip();\n"
                + "        String blankValue = \" \";\n"
                + "        boolean blank = blankValue.isBlank();\n"
                + "        Path path = Path.of(\"sample.txt\");\n"
                + "        Files.readString(path);\n"
                + "        Files.writeString(path, \"data\");\n"
                + "        Predicate<String> predicate = Predicate.not(String::isBlank);\n"
                + "        String[] array = list.toArray(String[]::new);\n"
                + "    }\n"
                + "}\n";

        String output = new SourceDesugarer().desugar(input, "Example.java", false);
        BackportTestRunner.assertTrue(output.contains("import j9compat.CollectionBackport;"),
                "CollectionBackport import added");
        BackportTestRunner.assertTrue(output.contains("import j9compat.StreamBackport;"),
                "StreamBackport import added");
        BackportTestRunner.assertTrue(output.contains("import j9compat.OptionalBackport;"),
                "OptionalBackport import added");
        BackportTestRunner.assertTrue(output.contains("import j9compat.StringBackport;"),
                "StringBackport import added");
        BackportTestRunner.assertTrue(output.contains("import j9compat.FilesBackport;"),
                "FilesBackport import added");
        BackportTestRunner.assertTrue(output.contains("import j9compat.PathBackport;"),
                "PathBackport import added");
        BackportTestRunner.assertTrue(output.contains("import j9compat.PredicateBackport;"),
                "PredicateBackport import added");
        BackportTestRunner.assertTrue(output.contains("CollectionBackport.listOf"),
                "List.of call rewritten");
        BackportTestRunner.assertTrue(output.contains("CollectionBackport.mapOf"),
                "Map.of call rewritten");
        BackportTestRunner.assertTrue(output.contains("StreamBackport.ofNullable"),
                "Stream.ofNullable call rewritten");
        BackportTestRunner.assertTrue(output.contains("OptionalBackport.ifPresentOrElse(opt"),
                "Optional.ifPresentOrElse call rewritten");
        BackportTestRunner.assertTrue(output.contains("OptionalBackport.isEmpty(opt)"),
                "Optional.isEmpty call rewritten");
        BackportTestRunner.assertTrue(output.contains("StringBackport.strip(raw)"),
                "String.strip call rewritten");
        BackportTestRunner.assertTrue(output.contains("StringBackport.isBlank(blankValue)"),
                "String.isBlank call rewritten");
        BackportTestRunner.assertTrue(output.contains("PathBackport.of(\"sample.txt\")"),
                "Path.of call rewritten");
        BackportTestRunner.assertTrue(output.contains("FilesBackport.readString(path)"),
                "Files.readString call rewritten");
        BackportTestRunner.assertTrue(output.contains("FilesBackport.writeString(path, \"data\")"),
                "Files.writeString call rewritten");
        BackportTestRunner.assertTrue(output.contains("PredicateBackport.not(String::isBlank)"),
                "Predicate.not call rewritten");
        BackportTestRunner.assertTrue(output.contains("CollectionBackport.toArray(list, String[]::new)"),
                "Collection.toArray(IntFunction) call rewritten");
    }

    private static void testStaticImport() {
        String input = "import static java.util.List.of;\n"
                + "public class Example {\n"
                + "    void test() {\n"
                + "        Object list = of(\"a\");\n"
                + "    }\n"
                + "}\n";

        String output = new SourceDesugarer().desugar(input, "Example.java", false);
        BackportTestRunner.assertTrue(output.contains("CollectionBackport.listOf(\"a\")"),
                "Static-imported List.of call rewritten");
    }

    private static void testModuleInfo() {
        String input = "module example {\n    requires java.base;\n}\n";
        String output = new SourceDesugarer().desugar(input, "module-info.java", false);
        BackportTestRunner.assertTrue(output.startsWith("/* module-info.java desugared"),
                "module-info.java converted to comment");
    }

    private static void testExternalSourcesCompile() {
        List<SourceFile> sources = Arrays.asList(
                new SourceFile("com/external/config/ConfigLoader.java",
                        "package com.external.config;\n"
                                + "\n"
                                + "import java.io.InputStream;\n"
                                + "import java.io.IOException;\n"
                                + "import java.nio.file.Files;\n"
                                + "import java.nio.file.Path;\n"
                                + "import java.nio.charset.StandardCharsets;\n"
                                + "import java.util.List;\n"
                                + "import java.util.Map;\n"
                                + "import java.util.Objects;\n"
                                + "import java.util.Optional;\n"
                                + "import java.util.function.Predicate;\n"
                                + "import java.util.stream.Collectors;\n"
                                + "import java.util.stream.Stream;\n"
                                + "\n"
                                + "public class ConfigLoader {\n"
                                + "    public List<String> loadNames(InputStream in) throws IOException {\n"
                                + "        byte[] data = in.readAllBytes();\n"
                                + "        String text = new String(data, StandardCharsets.UTF_8);\n"
                                + "        return List.of(text.split(\",\"));\n"
                                + "    }\n"
                                + "\n"
                                + "    public String normalize(String name) {\n"
                                + "        return name.strip();\n"
                                + "    }\n"
                                + "\n"
                                + "    public boolean isBlank(String name) {\n"
                                + "        return name.isBlank();\n"
                                + "    }\n"
                                + "\n"
                                + "    public String loadText(Path path) throws IOException {\n"
                                + "        return Files.readString(path);\n"
                                + "    }\n"
                                + "\n"
                                + "    public Path writeText(Path path, String value) throws IOException {\n"
                                + "        return Files.writeString(path, value);\n"
                                + "    }\n"
                                + "\n"
                                + "    public Path defaultPath() {\n"
                                + "        return Path.of(\"config.txt\");\n"
                                + "    }\n"
                                + "\n"
                                + "    public Predicate<String> nonBlank() {\n"
                                + "        return Predicate.not(String::isBlank);\n"
                                + "    }\n"
                                + "\n"
                                + "    public String[] asArray(List<String> values) {\n"
                                + "        return values.toArray(String[]::new);\n"
                                + "    }\n"
                                + "\n"
                                + "    public Map<String, Integer> defaultScores() {\n"
                                + "        return Map.of(\"alpha\", 1, \"beta\", 2);\n"
                                + "    }\n"
                                + "\n"
                                + "    public Optional<String> pick(Optional<String> input) {\n"
                                + "        return input.or(() -> Optional.of(\"fallback\"));\n"
                                + "    }\n"
                                + "\n"
                                + "    public List<Integer> takePositive(List<Integer> values) {\n"
                                + "        Stream<Integer> stream = Stream.of(values.toArray(new Integer[0]));\n"
                                + "        return stream.takeWhile(v -> v > 0)\n"
                                + "                .collect(Collectors.toList());\n"
                                + "    }\n"
                                + "\n"
                                + "    public String requireName(String name) {\n"
                                + "        return Objects.requireNonNullElse(name, \"unknown\");\n"
                                + "    }\n"
                                + "}\n"),
                new SourceFile("com/external/io/ResourceUser.java",
                        "package com.external.io;\n"
                                + "\n"
                                + "public class ResourceUser {\n"
                                + "    public int readOne(CloseableResource resource) throws Exception {\n"
                                + "        try (resource) {\n"
                                + "            return resource.read();\n"
                                + "        }\n"
                                + "    }\n"
                                + "\n"
                                + "    public static final class CloseableResource implements AutoCloseable {\n"
                                + "        private final int value;\n"
                                + "        public CloseableResource(int value) {\n"
                                + "            this.value = value;\n"
                                + "        }\n"
                                + "        public int read() {\n"
                                + "            return value;\n"
                                + "        }\n"
                                + "        @Override\n"
                                + "        public void close() {\n"
                                + "        }\n"
                                + "    }\n"
                                + "}\n"),
                new SourceFile("com/external/io/Checker.java",
                        "package com.external.io;\n"
                                + "\n"
                                + "import java.util.List;\n"
                                + "\n"
                                + "public interface Checker {\n"
                                + "    private boolean isValid(int value) {\n"
                                + "        return value > 0;\n"
                                + "    }\n"
                                + "\n"
                                + "    default boolean allValid(List<Integer> values) {\n"
                                + "        for (int value : values) {\n"
                                + "            if (!isValid(value)) {\n"
                                + "                return false;\n"
                                + "            }\n"
                                + "        }\n"
                                + "        return true;\n"
                                + "    }\n"
                                + "}\n"),
                new SourceFile("com/external/util/EntryFactory.java",
                        "package com.external.util;\n"
                                + "\n"
                                + "import java.util.Map;\n"
                                + "\n"
                                + "public class EntryFactory {\n"
                                + "    public Map<String, Integer> build() {\n"
                                + "        return Map.ofEntries(\n"
                                + "                Map.entry(\"alpha\", 1),\n"
                                + "                Map.entry(\"beta\", 2)\n"
                                + "        );\n"
                                + "    }\n"
                                + "}\n")
        );

        try {
            SourceDesugarer desugarer = new SourceDesugarer();
            Path root = Files.createTempDirectory("desugar-external-src");
            List<String> filePaths = new ArrayList<>();
            for (SourceFile source : sources) {
                String output = desugarer.desugar(source.source, source.path, false);
                Path file = root.resolve(source.path);
                Files.createDirectories(file.getParent());
                Files.write(file, output.getBytes(StandardCharsets.UTF_8));
                filePaths.add(file.toString());
            }

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                BackportTestRunner.fail("System Java compiler not available for external source compilation");
                return;
            }

            Path classesDir = Files.createTempDirectory("desugar-external-classes");
            List<String> args = new ArrayList<>();
            args.add("-source");
            args.add("8");
            args.add("-target");
            args.add("8");
            args.add("-classpath");
            args.add(System.getProperty("java.class.path"));
            args.add("-d");
            args.add(classesDir.toString());
            args.addAll(filePaths);

            int result = compiler.run(null, null, null, args.toArray(new String[0]));
            BackportTestRunner.assertEquals(0, result,
                    "External Java 11 sources desugar and compile on Java 8");
        } catch (Exception e) {
            BackportTestRunner.fail("External Java 11 sources compile: " + e.getMessage());
        }
    }

    private static final class SourceFile {
        private final String path;
        private final String source;

        private SourceFile(String path, String source) {
            this.path = path;
            this.source = source;
        }
    }
}
