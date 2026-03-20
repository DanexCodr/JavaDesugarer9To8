package desugarer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PrivateInterfaceMethodTransformer implements SourceTransformer {
    private static final Pattern INTERFACE_PATTERN = Pattern.compile("\\binterface\\b");
    private static final String INTERFACE_SELF_PARAM_NAME = "__desugar_j9_interface_self__";

    @Override
    public String transform(String source, SourceContext context) {
        if (!source.contains("interface") || !source.contains("private")) {
            return source;
        }
        boolean[] codeMask = SourceDesugarer.buildCodeMask(source);
        Matcher matcher = INTERFACE_PATTERN.matcher(source);
        StringBuilder out = new StringBuilder(source.length());
        int index = 0;

        while (matcher.find()) {
            int interfaceIndex = matcher.start();
            if (!isCode(codeMask, interfaceIndex)) {
                continue;
            }
            String interfaceName = parseInterfaceName(source, interfaceIndex);
            if (interfaceName == null) {
                continue;
            }
            int bodyStart = findInterfaceBodyStart(source, interfaceIndex, codeMask);
            if (bodyStart < 0) {
                continue;
            }
            int bodyEnd = SourceDesugarer.findMatching(source, codeMask, bodyStart, '{', '}');
            if (bodyEnd < 0) {
                continue;
            }
            int declStart = findDeclarationStart(source, interfaceIndex);

            String body = source.substring(bodyStart + 1, bodyEnd);
            List<PrivateMethod> privateMethods = extractPrivateMethods(
                    body, bodyStart + 1, codeMask, interfaceName, context);
            if (privateMethods.isEmpty()) {
                continue;
            }

            String transformedBody = removePrivateMethods(body, privateMethods);
            transformedBody = rewritePrivateCalls(transformedBody, privateMethods,
                    interfaceName + "Helper", "this");

            String helperClass = buildHelperClass(interfaceName, privateMethods);

            out.append(source, index, declStart);
            out.append(helperClass);
            out.append(source, declStart, bodyStart + 1);
            out.append(transformedBody);
            out.append(source, bodyEnd, bodyEnd + 1);
            index = bodyEnd + 1;
            matcher = INTERFACE_PATTERN.matcher(source);
            matcher.region(index, source.length());
        }

        out.append(source.substring(index));
        return out.toString();
    }

    private List<PrivateMethod> extractPrivateMethods(String body, int bodyOffset,
                                                      boolean[] codeMask,
                                                      String interfaceName,
                                                      SourceContext context) {
        List<PrivateMethod> methods = new ArrayList<>();
        int depth = 0;
        for (int i = 0; i < body.length(); i++) {
            if (!isCode(codeMask, bodyOffset + i)) {
                continue;
            }
            char c = body.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth = Math.max(0, depth - 1);
            }
            if (depth != 0) {
                continue;
            }
            if (startsWithKeyword(body, i, "private")) {
                int declStart = findDeclarationStart(body, i);
                MethodBounds bounds = parseMethod(body, declStart, bodyOffset, codeMask);
                if (bounds == null) {
                    context.warn("Unable to parse private interface method in " + interfaceName);
                    continue;
                }
                String methodText = body.substring(bounds.startIndex, bounds.endIndex + 1);
                methods.add(new PrivateMethod(bounds, interfaceName, methodText));
                i = bounds.endIndex;
            }
        }
        return methods;
    }

    private MethodBounds parseMethod(String body, int declStart, int bodyOffset, boolean[] mask) {
        int index = declStart;
        boolean isStatic = false;
        String methodName = null;
        int parenIndex = -1;
        int parenClose = -1;
        int bodyStart = -1;

        while (index < body.length()) {
            if (!isCode(mask, bodyOffset + index)) {
                index++;
                continue;
            }
            if (startsWithKeyword(body, index, "static")) {
                isStatic = true;
            }
            if (body.charAt(index) == '(') {
                parenIndex = index;
                parenClose = findMatching(body, index, '(', ')');
                if (parenClose < 0) {
                    return null;
                }
                break;
            }
            if (Character.isJavaIdentifierStart(body.charAt(index))) {
                int end = index + 1;
                while (end < body.length() && Character.isJavaIdentifierPart(body.charAt(end))) {
                    end++;
                }
                methodName = body.substring(index, end);
                index = end;
                continue;
            }
            index++;
        }

        if (parenIndex < 0 || methodName == null) {
            return null;
        }

        int scan = parenClose + 1;
        while (scan < body.length()) {
            if (!isCode(mask, bodyOffset + scan)) {
                scan++;
                continue;
            }
            char c = body.charAt(scan);
            if (c == '{') {
                bodyStart = scan;
                break;
            }
            scan++;
        }
        if (bodyStart < 0) {
            return null;
        }
        int bodyEnd = findMatching(body, bodyStart, '{', '}');
        if (bodyEnd < 0) {
            return null;
        }

        return new MethodBounds(declStart, bodyEnd, parenIndex, parenClose, bodyStart,
                methodName, isStatic);
    }

    private String removePrivateMethods(String body, List<PrivateMethod> methods) {
        StringBuilder out = new StringBuilder(body.length());
        int index = 0;
        for (PrivateMethod method : methods) {
            MethodBounds bounds = method.bounds;
            if (bounds.startIndex >= index) {
                out.append(body, index, bounds.startIndex);
                index = bounds.endIndex + 1;
            }
        }
        out.append(body.substring(index));
        return out.toString();
    }

    private String rewritePrivateCalls(String body, List<PrivateMethod> methods,
                                       String helperName, String selfArg) {
        String updated = body;
        for (PrivateMethod method : methods) {
            updated = replaceCalls(updated, method.methodName, helperName,
                    method.needsSelf ? selfArg : null);
        }
        return updated;
    }

    private String replaceCalls(String body, String methodName, String helper, String selfArg) {
        return SourceDesugarer.transformCodeSegments(body, (code, context) -> {
            String rewritten = code;
            String qualified = helper + "." + methodName;
            String quoted = Pattern.quote(methodName);
            if (selfArg == null) {
                rewritten = rewritten.replaceAll("\\bthis\\s*\\.\\s*" + quoted + "\\s*\\(",
                        qualified + "(");
                rewritten = rewritten.replaceAll("(?<![\\w.])" + quoted + "\\s*\\(",
                        qualified + "(");
                return rewritten;
            }
            rewritten = rewritten.replaceAll("\\bthis\\s*\\.\\s*" + quoted + "\\s*\\(\\s*\\)",
                    qualified + "(" + selfArg + ")");
            rewritten = rewritten.replaceAll("\\bthis\\s*\\.\\s*" + quoted + "\\s*\\(",
                    qualified + "(" + selfArg + ", ");
            rewritten = rewritten.replaceAll("(?<![\\w.])" + quoted + "\\s*\\(\\s*\\)",
                    qualified + "(" + selfArg + ")");
            rewritten = rewritten.replaceAll("(?<![\\w.])" + quoted + "\\s*\\(",
                    qualified + "(" + selfArg + ", ");
            return rewritten;
        }, null);
    }

    private String buildHelperClass(String interfaceName, List<PrivateMethod> methods) {
        StringBuilder helper = new StringBuilder();
        helper.append("class ").append(interfaceName).append("Helper {\n");
        for (PrivateMethod method : methods) {
            String helperMethod = method.toHelperMethod(methods, interfaceName + "Helper");
            helper.append(helperMethod);
            if (!method.helperEndsWithNewline) {
                helper.append("\n");
            }
        }
        helper.append("}\n\n");
        return helper.toString();
    }

    private String parseInterfaceName(String source, int interfaceIndex) {
        int scan = interfaceIndex + "interface".length();
        scan = SourceDesugarer.skipWhitespace(source, scan);
        int start = scan;
        while (scan < source.length() && Character.isJavaIdentifierPart(source.charAt(scan))) {
            scan++;
        }
        if (start == scan) {
            return null;
        }
        return source.substring(start, scan);
    }

    private int findInterfaceBodyStart(String source, int interfaceIndex, boolean[] mask) {
        int scan = interfaceIndex;
        while (scan < source.length()) {
            if (!isCode(mask, scan)) {
                scan++;
                continue;
            }
            if (source.charAt(scan) == '{') {
                return scan;
            }
            scan++;
        }
        return -1;
    }

    private int findDeclarationStart(String text, int index) {
        int lineStart = text.lastIndexOf('\n', index - 1) + 1;
        int start = lineStart;
        int scan = lineStart;
        while (scan > 0) {
            int previousLineStart = text.lastIndexOf('\n', scan - 1) + 1;
            String line = text.substring(previousLineStart, scan).trim();
            if (line.startsWith("@")) {
                start = previousLineStart;
                scan = previousLineStart;
            } else {
                break;
            }
        }
        return start;
    }

    private boolean startsWithKeyword(String text, int index, String keyword) {
        int len = keyword.length();
        if (index < 0 || index + len > text.length()) {
            return false;
        }
        if (!text.regionMatches(index, keyword, 0, len)) {
            return false;
        }
        boolean startOk = index == 0 || !Character.isJavaIdentifierPart(text.charAt(index - 1));
        boolean endOk = index + len >= text.length()
                || !Character.isJavaIdentifierPart(text.charAt(index + len));
        return startOk && endOk;
    }

    private boolean isCode(boolean[] mask, int index) {
        return mask != null && index >= 0 && index < mask.length && mask[index];
    }

    private int findMatching(String text, int openIndex, char openChar, char closeChar) {
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == openChar) {
                depth++;
            } else if (c == closeChar) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String replaceThisOutsideNestedTypes(String body, String replacement) {
        StringBuilder out = new StringBuilder(body.length());
        List<Boolean> stack = new ArrayList<>();
        int typeDepth = 0;
        boolean pendingType = false;
        boolean pendingAnon = false;
        boolean anonReady = false;
        int parenDepth = 0;
        boolean sawParen = false;

        for (int i = 0; i < body.length(); ) {
            if (isKeywordAt(body, i, "class")) {
                pendingType = true;
                out.append("class");
                i += 5;
                continue;
            }
            if (isKeywordAt(body, i, "interface")) {
                pendingType = true;
                out.append("interface");
                i += 9;
                continue;
            }
            if (isKeywordAt(body, i, "enum")) {
                pendingType = true;
                out.append("enum");
                i += 4;
                continue;
            }
            if (isKeywordAt(body, i, "new")) {
                pendingAnon = true;
                anonReady = false;
                parenDepth = 0;
                sawParen = false;
                out.append("new");
                i += 3;
                continue;
            }

            char c = body.charAt(i);

            if (pendingAnon) {
                if (c == '(') {
                    parenDepth++;
                    sawParen = true;
                } else if (c == ')' && sawParen) {
                    parenDepth = Math.max(0, parenDepth - 1);
                    if (parenDepth == 0) {
                        anonReady = true;
                        pendingAnon = false;
                    }
                } else if (c == ';') {
                    pendingAnon = false;
                    anonReady = false;
                } else if (!sawParen && c == '{') {
                    pendingAnon = false;
                    anonReady = false;
                } else if (!sawParen && c == ',') {
                    pendingAnon = false;
                    anonReady = false;
                }
            }

            if (anonReady && !Character.isWhitespace(c)) {
                if (c != '{') {
                    anonReady = false;
                }
            }

            if (c == '{') {
                boolean isType = false;
                if (pendingType) {
                    isType = true;
                    pendingType = false;
                } else if (anonReady) {
                    isType = true;
                    anonReady = false;
                }
                stack.add(isType);
                if (isType) {
                    typeDepth++;
                }
            } else if (c == '}') {
                if (!stack.isEmpty()) {
                    boolean wasType = stack.remove(stack.size() - 1);
                    if (wasType) {
                        typeDepth = Math.max(0, typeDepth - 1);
                    }
                }
                pendingType = false;
                pendingAnon = false;
                anonReady = false;
                parenDepth = 0;
                sawParen = false;
            }

            if (typeDepth == 0 && isKeywordAt(body, i, "this")) {
                out.append(replacement);
                i += 4;
                continue;
            }

            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static boolean isKeywordAt(String text, int index, String keyword) {
        int len = keyword.length();
        if (index < 0 || index + len > text.length()) {
            return false;
        }
        if (!text.regionMatches(index, keyword, 0, len)) {
            return false;
        }
        boolean startOk = index == 0 || !Character.isJavaIdentifierPart(text.charAt(index - 1));
        boolean endOk = index + len >= text.length()
                || !Character.isJavaIdentifierPart(text.charAt(index + len));
        return startOk && endOk;
    }

    private static final class MethodBounds {
        final int startIndex;
        final int endIndex;
        final int parenIndex;
        final int parenClose;
        final int bodyStart;
        final String methodName;
        final boolean isStatic;

        MethodBounds(int startIndex, int endIndex, int parenIndex, int parenClose,
                     int bodyStart, String methodName, boolean isStatic) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.parenIndex = parenIndex;
            this.parenClose = parenClose;
            this.bodyStart = bodyStart;
            this.methodName = methodName;
            this.isStatic = isStatic;
        }
    }

    private static final class PrivateMethod {
        final MethodBounds bounds;
        final String interfaceName;
        final boolean needsSelf;
        final String methodName;
        final boolean helperEndsWithNewline;
        final String methodText;

        PrivateMethod(MethodBounds bounds, String interfaceName, String methodText) {
            this.bounds = bounds;
            this.interfaceName = interfaceName;
            this.methodName = bounds.methodName;
            this.needsSelf = !bounds.isStatic;
            this.methodText = methodText;
            this.helperEndsWithNewline = methodText.endsWith("\n");
        }

        String toHelperMethod(List<PrivateMethod> methods, String helperName) {
            String header = buildHelperHeader();
            String body = buildHelperBody(methods, helperName);
            return header + body;
        }

        private String buildHelperHeader() {
            int localParenIndex = bounds.parenIndex - bounds.startIndex;
            int localParenClose = bounds.parenClose - bounds.startIndex;
            int localBodyStart = bounds.bodyStart - bounds.startIndex;

            String headerPrefix = methodText.substring(0, localParenIndex);
            String params = methodText.substring(localParenIndex + 1, localParenClose);
            String headerSuffix = methodText.substring(localParenClose + 1, localBodyStart);

            String modifiedPrefix = headerPrefix.replaceFirst("\\bprivate\\b\\s*", "");
            String trimmed = modifiedPrefix.trim();
            String indent = modifiedPrefix.substring(0, modifiedPrefix.indexOf(trimmed));
            if (!trimmed.contains("static")) {
                trimmed = "static " + trimmed;
            }

            String newParams = params.trim();
            if (needsSelf) {
                String selfParam = interfaceName + " " + INTERFACE_SELF_PARAM_NAME;
                newParams = newParams.isEmpty() ? selfParam : selfParam + ", " + newParams;
            }

            return indent + trimmed + "(" + newParams + ")" + headerSuffix;
        }

        private String buildHelperBody(List<PrivateMethod> methods, String helperName) {
            int localBodyStart = bounds.bodyStart - bounds.startIndex;
            String body = methodText.substring(localBodyStart);
            if (needsSelf) {
                body = replaceThisOutsideNestedTypes(body, INTERFACE_SELF_PARAM_NAME);
            }
            body = new PrivateInterfaceMethodTransformer().rewritePrivateCalls(
                    body, methods, helperName, needsSelf ? INTERFACE_SELF_PARAM_NAME : null);
            return body;
        }
    }
}
