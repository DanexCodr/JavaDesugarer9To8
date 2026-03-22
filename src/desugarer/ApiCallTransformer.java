package desugarer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApiCallTransformer implements SourceTransformer {
    private static final String COLLECTION_BACKPORT = "j9compat.CollectionBackport";
    private static final String STREAM_BACKPORT = "j9compat.StreamBackport";
    private static final String INT_STREAM_BACKPORT = "j9compat.IntStreamBackport";
    private static final String LONG_STREAM_BACKPORT = "j9compat.LongStreamBackport";
    private static final String DOUBLE_STREAM_BACKPORT = "j9compat.DoubleStreamBackport";
    private static final String OPTIONAL_BACKPORT = "j9compat.OptionalBackport";
    private static final String OPTIONAL_INT_BACKPORT = "j9compat.OptionalIntBackport";
    private static final String OPTIONAL_LONG_BACKPORT = "j9compat.OptionalLongBackport";
    private static final String OPTIONAL_DOUBLE_BACKPORT = "j9compat.OptionalDoubleBackport";
    private static final String IO_BACKPORT = "j9compat.IOBackport";
    private static final String OBJECTS_BACKPORT = "j9compat.ObjectsBackport";
    private static final String COMPLETABLE_BACKPORT = "j9compat.CompletableFutureBackport";
    private static final String COLLECTORS_BACKPORT = "j9compat.CollectorsBackport";
    private static final String STRING_BACKPORT = "j9compat.StringBackport";
    private static final String FILES_BACKPORT = "j9compat.FilesBackport";
    private static final String PATH_BACKPORT = "j9compat.PathBackport";
    private static final String PREDICATE_BACKPORT = "j9compat.PredicateBackport";

    @Override
    public String transform(String source, SourceContext context) {
        return SourceDesugarer.transformCodeSegments(source, this::transformCode, context);
    }

    private String transformCode(String code, SourceContext context) {
        ImportAnalyzer imports = context.getImports();
        String updated = code;

        updated = replaceStatic(updated, imports, context,
                "java.util.List", "List", "of", COLLECTION_BACKPORT, "listOf");
        updated = replaceStatic(updated, imports, context,
                "java.util.Set", "Set", "of", COLLECTION_BACKPORT, "setOf");
        updated = replaceStatic(updated, imports, context,
                "java.util.Map", "Map", "of", COLLECTION_BACKPORT, "mapOf");
        updated = replaceStatic(updated, imports, context,
                "java.util.Map", "Map", "ofEntries", COLLECTION_BACKPORT, "mapOfEntries");
        updated = replaceStatic(updated, imports, context,
                "java.util.Map", "Map", "entry", COLLECTION_BACKPORT, "mapEntry");
        updated = replaceStatic(updated, imports, context,
                "java.util.List", "List", "copyOf", COLLECTION_BACKPORT, "listCopyOf");
        updated = replaceStatic(updated, imports, context,
                "java.util.Set", "Set", "copyOf", COLLECTION_BACKPORT, "setCopyOf");
        updated = replaceStatic(updated, imports, context,
                "java.util.Map", "Map", "copyOf", COLLECTION_BACKPORT, "mapCopyOf");

        updated = replaceStatic(updated, imports, context,
                "java.util.stream.Stream", "Stream", "ofNullable", STREAM_BACKPORT, "ofNullable");
        updated = replaceIterate(updated, imports, context,
                "java.util.stream.Stream", "Stream", STREAM_BACKPORT);
        updated = replaceIterate(updated, imports, context,
                "java.util.stream.IntStream", "IntStream", INT_STREAM_BACKPORT);
        updated = replaceIterate(updated, imports, context,
                "java.util.stream.LongStream", "LongStream", LONG_STREAM_BACKPORT);
        updated = replaceIterate(updated, imports, context,
                "java.util.stream.DoubleStream", "DoubleStream", DOUBLE_STREAM_BACKPORT);

        updated = replaceStatic(updated, imports, context,
                "java.util.Objects", "Objects", "requireNonNullElse", OBJECTS_BACKPORT, "requireNonNullElse");
        updated = replaceStatic(updated, imports, context,
                "java.util.Objects", "Objects", "requireNonNullElseGet", OBJECTS_BACKPORT, "requireNonNullElseGet");
        updated = replaceStatic(updated, imports, context,
                "java.util.Objects", "Objects", "checkIndex", OBJECTS_BACKPORT, "checkIndex");
        updated = replaceStatic(updated, imports, context,
                "java.util.Objects", "Objects", "checkFromToIndex", OBJECTS_BACKPORT, "checkFromToIndex");
        updated = replaceStatic(updated, imports, context,
                "java.util.Objects", "Objects", "checkFromIndexSize", OBJECTS_BACKPORT, "checkFromIndexSize");

        updated = replaceStatic(updated, imports, context,
                "java.util.concurrent.CompletableFuture", "CompletableFuture",
                "failedFuture", COMPLETABLE_BACKPORT, "failedFuture");
        updated = replaceStatic(updated, imports, context,
                "java.util.concurrent.CompletableFuture", "CompletableFuture",
                "completedStage", COMPLETABLE_BACKPORT, "completedStage");
        updated = replaceStatic(updated, imports, context,
                "java.util.concurrent.CompletableFuture", "CompletableFuture",
                "failedStage", COMPLETABLE_BACKPORT, "failedStage");

        updated = replaceStatic(updated, imports, context,
                "java.util.stream.Collectors", "Collectors", "filtering", COLLECTORS_BACKPORT, "filtering");
        updated = replaceStatic(updated, imports, context,
                "java.util.stream.Collectors", "Collectors", "flatMapping", COLLECTORS_BACKPORT, "flatMapping");
        updated = replaceStatic(updated, imports, context,
                "java.util.stream.Collectors", "Collectors", "toUnmodifiableList", COLLECTORS_BACKPORT, "toUnmodifiableList");
        updated = replaceStatic(updated, imports, context,
                "java.util.stream.Collectors", "Collectors", "toUnmodifiableSet", COLLECTORS_BACKPORT, "toUnmodifiableSet");
        updated = replaceStatic(updated, imports, context,
                "java.util.stream.Collectors", "Collectors", "toUnmodifiableMap", COLLECTORS_BACKPORT, "toUnmodifiableMap");
        updated = replaceStatic(updated, imports, context,
                "java.nio.file.Files", "Files", "readString", FILES_BACKPORT, "readString");
        updated = replaceStatic(updated, imports, context,
                "java.nio.file.Files", "Files", "writeString", FILES_BACKPORT, "writeString");
        updated = replaceStatic(updated, imports, context,
                "java.nio.file.Path", "Path", "of", PATH_BACKPORT, "of");
        updated = replaceStatic(updated, imports, context,
                "java.util.function.Predicate", "Predicate", "not", PREDICATE_BACKPORT, "not");

        if (imports.isTypeImported("Stream", "java.util.stream.Stream")
                || imports.isTypeImported("IntStream", "java.util.stream.IntStream")
                || imports.isTypeImported("LongStream", "java.util.stream.LongStream")
                || imports.isTypeImported("DoubleStream", "java.util.stream.DoubleStream")
                || code.contains("java.util.stream.Stream")
                || code.contains("java.util.stream.IntStream")
                || code.contains("java.util.stream.LongStream")
                || code.contains("java.util.stream.DoubleStream")) {
            updated = replaceInstance(updated, "takeWhile", STREAM_BACKPORT, "takeWhile", context);
            updated = replaceInstance(updated, "dropWhile", STREAM_BACKPORT, "dropWhile", context);
        }

        if (imports.isTypeImported("IntStream", "java.util.stream.IntStream")
                || code.contains("java.util.stream.IntStream")) {
            updated = replaceInstance(updated, "takeWhile", INT_STREAM_BACKPORT, "takeWhile", context);
            updated = replaceInstance(updated, "dropWhile", INT_STREAM_BACKPORT, "dropWhile", context);
        }

        if (imports.isTypeImported("LongStream", "java.util.stream.LongStream")
                || code.contains("java.util.stream.LongStream")) {
            updated = replaceInstance(updated, "takeWhile", LONG_STREAM_BACKPORT, "takeWhile", context);
            updated = replaceInstance(updated, "dropWhile", LONG_STREAM_BACKPORT, "dropWhile", context);
        }

        if (imports.isTypeImported("DoubleStream", "java.util.stream.DoubleStream")
                || code.contains("java.util.stream.DoubleStream")) {
            updated = replaceInstance(updated, "takeWhile", DOUBLE_STREAM_BACKPORT, "takeWhile", context);
            updated = replaceInstance(updated, "dropWhile", DOUBLE_STREAM_BACKPORT, "dropWhile", context);
        }

        if (imports.isTypeImported("Optional", "java.util.Optional")
                || code.contains("java.util.Optional")) {
            updated = replaceInstance(updated, "ifPresentOrElse", OPTIONAL_BACKPORT, "ifPresentOrElse", context);
            updated = replaceInstance(updated, "or", OPTIONAL_BACKPORT, "or", context);
            updated = replaceInstance(updated, "stream", OPTIONAL_BACKPORT, "stream", context);
            updated = replaceInstanceNoArgs(updated, "isEmpty", OPTIONAL_BACKPORT, "isEmpty", context);
            updated = replaceInstanceNoArgs(updated, "orElseThrow", OPTIONAL_BACKPORT, "orElseThrow", context);
        }

        if (imports.isTypeImported("OptionalInt", "java.util.OptionalInt")
                || code.contains("java.util.OptionalInt")) {
            updated = replaceInstance(updated, "ifPresentOrElse", OPTIONAL_INT_BACKPORT, "ifPresentOrElse", context);
            updated = replaceInstance(updated, "stream", OPTIONAL_INT_BACKPORT, "stream", context);
            updated = replaceInstanceNoArgs(updated, "isEmpty", OPTIONAL_INT_BACKPORT, "isEmpty", context);
            updated = replaceInstanceNoArgs(updated, "orElseThrow", OPTIONAL_INT_BACKPORT, "orElseThrow", context);
        }

        if (imports.isTypeImported("OptionalLong", "java.util.OptionalLong")
                || code.contains("java.util.OptionalLong")) {
            updated = replaceInstance(updated, "ifPresentOrElse", OPTIONAL_LONG_BACKPORT, "ifPresentOrElse", context);
            updated = replaceInstance(updated, "stream", OPTIONAL_LONG_BACKPORT, "stream", context);
            updated = replaceInstanceNoArgs(updated, "isEmpty", OPTIONAL_LONG_BACKPORT, "isEmpty", context);
            updated = replaceInstanceNoArgs(updated, "orElseThrow", OPTIONAL_LONG_BACKPORT, "orElseThrow", context);
        }

        if (imports.isTypeImported("OptionalDouble", "java.util.OptionalDouble")
                || code.contains("java.util.OptionalDouble")) {
            updated = replaceInstance(updated, "ifPresentOrElse", OPTIONAL_DOUBLE_BACKPORT, "ifPresentOrElse", context);
            updated = replaceInstance(updated, "stream", OPTIONAL_DOUBLE_BACKPORT, "stream", context);
            updated = replaceInstanceNoArgs(updated, "isEmpty", OPTIONAL_DOUBLE_BACKPORT, "isEmpty", context);
            updated = replaceInstanceNoArgs(updated, "orElseThrow", OPTIONAL_DOUBLE_BACKPORT, "orElseThrow", context);
        }

        if (code.contains("isBlank") || code.contains("strip")
                || code.contains("repeat") || code.contains("lines")) {
            updated = replaceInstanceNoArgs(updated, "isBlank", STRING_BACKPORT, "isBlank", context);
            updated = replaceInstanceNoArgs(updated, "strip", STRING_BACKPORT, "strip", context);
            updated = replaceInstanceNoArgs(updated, "stripLeading", STRING_BACKPORT, "stripLeading", context);
            updated = replaceInstanceNoArgs(updated, "stripTrailing", STRING_BACKPORT, "stripTrailing", context);
            updated = replaceInstanceNoArgs(updated, "lines", STRING_BACKPORT, "lines", context);
            updated = replaceInstance(updated, "repeat", STRING_BACKPORT, "repeat", context);
        }

        updated = replaceCollectionToArray(updated, context);

        if (imports.isTypeImported("InputStream", "java.io.InputStream")
                || code.contains("java.io.InputStream")) {
            updated = replaceInstance(updated, "transferTo", IO_BACKPORT, "transferTo", context);
            updated = replaceInstance(updated, "readAllBytes", IO_BACKPORT, "readAllBytes", context);
            updated = replaceInstance(updated, "readNBytes", IO_BACKPORT, "readNBytes", context);
        }

        if (imports.isTypeImported("CompletableFuture", "java.util.concurrent.CompletableFuture")
                || code.contains("java.util.concurrent.CompletableFuture")) {
            updated = replaceInstance(updated, "orTimeout", COMPLETABLE_BACKPORT, "orTimeout", context);
            updated = replaceInstance(updated, "completeOnTimeout", COMPLETABLE_BACKPORT, "completeOnTimeout", context);
            updated = replaceInstance(updated, "copy", COMPLETABLE_BACKPORT, "copy", context);
        }

        return updated;
    }

    private String replaceCollectionToArray(String code, SourceContext context) {
        String updated = rewriteCollectionToArray(code);
        if (!updated.equals(code)) {
            context.addImport(COLLECTION_BACKPORT);
        }
        return updated;
    }

    private String rewriteCollectionToArray(String code) {
        StringBuilder out = new StringBuilder(code.length());
        int index = 0;
        while (index < code.length()) {
            int dotIndex = findDotMethod(code, "toArray", index);
            if (dotIndex < 0) {
                out.append(code.substring(index));
                break;
            }
            int receiverEnd = dotIndex;
            int receiverStart = findReceiverStart(code, receiverEnd - 1);
            if (receiverStart < 0) {
                out.append(code.substring(index, dotIndex + 1));
                index = dotIndex + 1;
                continue;
            }
            int methodStart = skipWhitespace(code, dotIndex + 1);
            int parenIndex = skipWhitespace(code, methodStart + "toArray".length());
            if (parenIndex >= code.length() || code.charAt(parenIndex) != '(') {
                out.append(code.substring(index, dotIndex + 1));
                index = dotIndex + 1;
                continue;
            }
            int closeParen = findMatchingParen(code, parenIndex);
            if (closeParen < 0) {
                out.append(code.substring(index));
                break;
            }

            String receiver = code.substring(receiverStart, receiverEnd).trim();
            String args = code.substring(parenIndex + 1, closeParen);
            if (countTopLevelArgs(args) != 1 || !looksLikeIntFunction(args)) {
                out.append(code, index, closeParen + 1);
                index = closeParen + 1;
                continue;
            }

            out.append(code, index, receiverStart);
            out.append(simpleName(COLLECTION_BACKPORT))
                    .append(".toArray(")
                    .append(receiver)
                    .append(", ")
                    .append(args)
                    .append(")");
            index = closeParen + 1;
        }
        return out.toString();
    }

    private boolean looksLikeIntFunction(String args) {
        String stripped = stripComments(args).trim();
        return stripped.contains("::")
                || stripped.contains("->")
                || stripped.contains("IntFunction");
    }

    private String replaceStatic(String code, ImportAnalyzer imports, SourceContext context,
                                 String fqn, String simpleName, String method,
                                 String replacementClass, String replacementMethod) {
        String updated = code;
        String replacementSimple = simpleName(replacementClass) + "." + replacementMethod + "(";

        String quotedMethod = Pattern.quote(method);
        Pattern fqPattern = Pattern.compile("\\b" + Pattern.quote(fqn) + "\\s*\\.\\s*" + quotedMethod + "\\s*\\(");
        Matcher fqMatcher = fqPattern.matcher(updated);
        if (fqMatcher.find()) {
            context.addImport(replacementClass);
            updated = fqMatcher.replaceAll(replacementSimple);
        }

        if (imports.isTypeImported(simpleName, fqn)) {
            Pattern simplePattern = Pattern.compile("\\b" + simpleName + "\\s*\\.\\s*" + quotedMethod + "\\s*\\(");
            Matcher simpleMatcher = simplePattern.matcher(updated);
            if (simpleMatcher.find()) {
                context.addImport(replacementClass);
                updated = simpleMatcher.replaceAll(replacementSimple);
            }
        }

        if (imports.hasStaticImport(fqn, method)) {
            Pattern staticPattern = Pattern.compile("(?<![\\w.])" + quotedMethod + "\\s*\\(");
            Matcher staticMatcher = staticPattern.matcher(updated);
            if (staticMatcher.find()) {
                context.addImport(replacementClass);
                updated = staticMatcher.replaceAll(replacementSimple);
            }
        }

        return updated;
    }

    private String replaceIterate(String code, ImportAnalyzer imports, SourceContext context,
                                  String fqn, String simpleName, String replacementClass) {
        String updated = code;
        String replacementSimple = simpleName(replacementClass) + ".iterate(";

        updated = replaceIteratePattern(updated, "\\b" + Pattern.quote(fqn) + "\\s*\\.\\s*iterate\\s*\\(",
                replacementSimple, context, replacementClass);
        if (imports.isTypeImported(simpleName, fqn)) {
            updated = replaceIteratePattern(updated, "\\b" + simpleName + "\\s*\\.\\s*iterate\\s*\\(",
                    replacementSimple, context, replacementClass);
        }
        if (imports.hasStaticImport(fqn, "iterate")) {
            updated = replaceIteratePattern(updated, "(?<![\\w.])iterate\\s*\\(",
                    replacementSimple, context, replacementClass);
        }
        return updated;
    }

    private String replaceIteratePattern(String code, String pattern,
                                         String replacement, SourceContext context,
                                         String replacementClass) {
        Pattern compiled = Pattern.compile(pattern);
        Matcher matcher = compiled.matcher(code);
        StringBuilder out = new StringBuilder(code.length());
        int index = 0;
        while (matcher.find(index)) {
            int openParen = matcher.end() - 1;
            int closeParen = findMatchingParen(code, openParen);
            if (closeParen < 0) {
                break;
            }
            String args = code.substring(openParen + 1, closeParen);
            if (countTopLevelArgs(args) == 3) {
                context.addImport(replacementClass);
                out.append(code, index, matcher.start());
                out.append(replacement).append(args).append(")");
            } else {
                out.append(code, index, closeParen + 1);
            }
            index = closeParen + 1;
            matcher.region(index, code.length());
        }
        out.append(code.substring(index));
        return out.toString();
    }

    private String replaceInstance(String code, String method,
                                   String replacementClass, String replacementMethod,
                                   SourceContext context) {
        String updated = rewriteInstanceCalls(code, method, replacementClass, replacementMethod);
        if (!updated.equals(code)) {
            context.addImport(replacementClass);
        }
        return updated;
    }

    private String replaceInstanceNoArgs(String code, String method,
                                         String replacementClass, String replacementMethod,
                                         SourceContext context) {
        String updated = rewriteInstanceCallsNoArgs(code, method, replacementClass, replacementMethod);
        if (!updated.equals(code)) {
            context.addImport(replacementClass);
        }
        return updated;
    }

    private String rewriteInstanceCalls(String code, String method,
                                        String replacementClass, String replacementMethod) {
        StringBuilder out = new StringBuilder(code.length());
        int index = 0;
        while (index < code.length()) {
            int dotIndex = findDotMethod(code, method, index);
            if (dotIndex < 0) {
                out.append(code.substring(index));
                break;
            }
            int receiverEnd = dotIndex;
            int receiverStart = findReceiverStart(code, receiverEnd - 1);
            if (receiverStart < 0) {
                out.append(code.substring(index, dotIndex + 1));
                index = dotIndex + 1;
                continue;
            }
            int methodStart = skipWhitespace(code, dotIndex + 1);
            int parenIndex = skipWhitespace(code, methodStart + method.length());
            if (parenIndex >= code.length() || code.charAt(parenIndex) != '(') {
                out.append(code.substring(index, dotIndex + 1));
                index = dotIndex + 1;
                continue;
            }
            int closeParen = findMatchingParen(code, parenIndex);
            if (closeParen < 0) {
                out.append(code.substring(index));
                break;
            }

            String receiver = code.substring(receiverStart, receiverEnd).trim();
            String args = code.substring(parenIndex + 1, closeParen);
            boolean hasArgs = hasArguments(args);

            out.append(code, index, receiverStart);
            out.append(simpleName(replacementClass))
                    .append('.')
                    .append(replacementMethod)
                    .append('(')
                    .append(receiver);
            if (hasArgs) {
                out.append(", ");
                out.append(args);
            }
            out.append(')');
            index = closeParen + 1;
        }
        return out.toString();
    }

    private String rewriteInstanceCallsNoArgs(String code, String method,
                                              String replacementClass, String replacementMethod) {
        StringBuilder out = new StringBuilder(code.length());
        int index = 0;
        while (index < code.length()) {
            int dotIndex = findDotMethod(code, method, index);
            if (dotIndex < 0) {
                out.append(code.substring(index));
                break;
            }
            int receiverEnd = dotIndex;
            int receiverStart = findReceiverStart(code, receiverEnd - 1);
            if (receiverStart < 0) {
                out.append(code.substring(index, dotIndex + 1));
                index = dotIndex + 1;
                continue;
            }
            int methodStart = skipWhitespace(code, dotIndex + 1);
            int parenIndex = skipWhitespace(code, methodStart + method.length());
            if (parenIndex >= code.length() || code.charAt(parenIndex) != '(') {
                out.append(code.substring(index, dotIndex + 1));
                index = dotIndex + 1;
                continue;
            }
            int closeParen = findMatchingParen(code, parenIndex);
            if (closeParen < 0) {
                out.append(code.substring(index));
                break;
            }

            String receiver = code.substring(receiverStart, receiverEnd).trim();
            String args = code.substring(parenIndex + 1, closeParen);
            if (hasArguments(args)) {
                out.append(code, index, closeParen + 1);
                index = closeParen + 1;
                continue;
            }

            out.append(code, index, receiverStart);
            out.append(simpleName(replacementClass))
                    .append('.')
                    .append(replacementMethod)
                    .append('(')
                    .append(receiver)
                    .append(')');
            index = closeParen + 1;
        }
        return out.toString();
    }

    private int findDotMethod(String code, String method, int start) {
        int index = start;
        while (index < code.length()) {
            int dot = code.indexOf('.', index);
            if (dot < 0) {
                return -1;
            }
            int methodStart = skipWhitespace(code, dot + 1);
            if (code.regionMatches(methodStart, method, 0, method.length())) {
                int afterMethod = methodStart + method.length();
                if (afterMethod < code.length()
                        && !Character.isJavaIdentifierPart(code.charAt(afterMethod))) {
                    return dot;
                }
            }
            index = dot + 1;
        }
        return -1;
    }

    private int findReceiverStart(String code, int index) {
        int depth = 0;
        for (int i = index; i >= 0; i--) {
            char c = code.charAt(i);
            if (c == ')' || c == ']' || c == '}') {
                depth++;
            } else if (c == '(' || c == '[' || c == '{') {
                depth--;
            }
            if (depth > 0) {
                continue;
            }
            if (depth == 0 && isBoundary(c)) {
                return i + 1;
            }
        }
        return 0;
    }

    private boolean isBoundary(char c) {
        return Character.isWhitespace(c)
                || c == ';' || c == ',' || c == '=' || c == ':'
                || c == '?' || c == '+' || c == '-' || c == '*' || c == '/'
                || c == '%' || c == '&' || c == '|' || c == '^'
                || c == '!' || c == '<' || c == '>' || c == '\n';
    }

    private int skipWhitespace(String code, int index) {
        int i = index;
        while (i < code.length() && Character.isWhitespace(code.charAt(i))) {
            i++;
        }
        return i;
    }

    private int findMatchingParen(String code, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int countTopLevelArgs(String args) {
        int depth = 0;
        int commas = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth = Math.max(0, depth - 1);
            } else if (c == ',' && depth == 0) {
                commas++;
            }
        }
        String stripped = stripComments(args).trim();
        if (stripped.isEmpty()) {
            return 0;
        }
        return commas + 1;
    }

    private boolean hasArguments(String args) {
        String stripped = stripComments(args);
        return !stripped.trim().isEmpty();
    }

    private String stripComments(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inLine = false;
        boolean inBlock = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                }
                continue;
            }
            if (inBlock) {
                if (c == '*' && next == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                inLine = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlock = true;
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private String simpleName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
    }
}
