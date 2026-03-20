package test;

import desugarer.SourceDesugarer;

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
        String input = "import java.util.List;\n"
                + "import java.util.Map;\n"
                + "import java.util.Optional;\n"
                + "import java.util.stream.Stream;\n"
                + "public class Example {\n"
                + "    void test() {\n"
                + "        List<String> list = List.of(\"a\", \"b\");\n"
                + "        Map<String, Integer> map = Map.of(\"k\", 1);\n"
                + "        Stream<String> stream = Stream.ofNullable(\"x\");\n"
                + "        Optional<String> opt = Optional.empty();\n"
                + "        opt.ifPresentOrElse(v -> {}, () -> {});\n"
                + "    }\n"
                + "}\n";

        String output = new SourceDesugarer().desugar(input, "Example.java", false);
        BackportTestRunner.assertTrue(output.contains("import j9compat.CollectionBackport;"),
                "CollectionBackport import added");
        BackportTestRunner.assertTrue(output.contains("import j9compat.StreamBackport;"),
                "StreamBackport import added");
        BackportTestRunner.assertTrue(output.contains("import j9compat.OptionalBackport;"),
                "OptionalBackport import added");
        BackportTestRunner.assertTrue(output.contains("CollectionBackport.listOf"),
                "List.of call rewritten");
        BackportTestRunner.assertTrue(output.contains("CollectionBackport.mapOf"),
                "Map.of call rewritten");
        BackportTestRunner.assertTrue(output.contains("StreamBackport.ofNullable"),
                "Stream.ofNullable call rewritten");
        BackportTestRunner.assertTrue(output.contains("OptionalBackport.ifPresentOrElse(opt"),
                "Optional.ifPresentOrElse call rewritten");
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
}
