package desugarer;

import java.util.ArrayList;
import java.util.List;

public final class TryWithResourcesTransformer implements SourceTransformer {
    @Override
    public String transform(String source, SourceContext context) {
        return SourceDesugarer.transformCodeSegments(source, this::transformCode, context);
    }

    private String transformCode(String code, SourceContext context) {
        StringBuilder out = new StringBuilder(code.length());
        int index = 0;
        while (index < code.length()) {
            int tryIndex = SourceDesugarer.findKeyword(code, null, "try", index);
            if (tryIndex < 0) {
                out.append(code.substring(index));
                break;
            }
            int afterTry = tryIndex + 3;
            int parenIndex = SourceDesugarer.skipWhitespace(code, afterTry);
            if (parenIndex >= code.length() || code.charAt(parenIndex) != '(') {
                out.append(code.substring(index, afterTry));
                index = afterTry;
                continue;
            }
            int closeParen = SourceDesugarer.findMatching(code, null, parenIndex, '(', ')');
            if (closeParen < 0) {
                out.append(code.substring(index));
                break;
            }
            String resources = code.substring(parenIndex + 1, closeParen);
            String rewritten = rewriteResources(resources);
            if (rewritten == null) {
                out.append(code.substring(index, closeParen + 1));
            } else {
                out.append(code, index, parenIndex + 1);
                out.append(rewritten);
                out.append(')');
            }
            index = closeParen + 1;
        }
        return out.toString();
    }

    private String rewriteResources(String resources) {
        List<String> parts = splitResources(resources);
        if (parts.isEmpty()) {
            return null;
        }
        for (String part : parts) {
            if (!isSimpleIdentifier(part.trim())) {
                return null;
            }
        }
        StringBuilder rewritten = new StringBuilder();
        int counter = 0;
        for (String part : parts) {
            String name = part.trim();
            if (counter > 0) {
                rewritten.append("; ");
            }
            rewritten.append("AutoCloseable __desugar_resource_").append(counter++)
                    .append(" = ").append(name);
        }
        return rewritten.toString();
    }

    private List<String> splitResources(String resources) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < resources.length(); i++) {
            char c = resources.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth = Math.max(0, depth - 1);
            } else if (c == ';' && depth == 0) {
                parts.add(resources.substring(start, i));
                start = i + 1;
            }
        }
        if (start < resources.length()) {
            String tail = resources.substring(start);
            if (!tail.trim().isEmpty()) {
                parts.add(tail);
            }
        }
        return parts;
    }

    private boolean isSimpleIdentifier(String value) {
        if (value.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!Character.isJavaIdentifierPart(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
