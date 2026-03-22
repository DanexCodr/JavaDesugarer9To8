package desugarer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SourceDesugarer {
    private final List<SourceTransformer> transformers;

    public SourceDesugarer() {
        this.transformers = Arrays.asList(
                new ModuleInfoTransformer(),
                new TypeReferenceTransformer(),
                new PrivateInterfaceMethodTransformer(),
                new DiamondOperatorTransformer(),
                new TryWithResourcesTransformer(),
                new ApiCallTransformer()
        );
    }

    public String desugar(String source, String fileName, boolean verbose) {
        ImportAnalyzer analyzer = new ImportAnalyzer(source);
        SourceContext context = new SourceContext(fileName, analyzer, verbose);

        String current = source;
        for (SourceTransformer transformer : transformers) {
            current = transformer.transform(current, context);
            if (context.getFileName() != null
                    && context.getFileName().endsWith("module-info.java")) {
                break;
            }
        }

        if (context.getFileName() != null
                && context.getFileName().endsWith("module-info.java")) {
            return current;
        }

        return ImportAnalyzer.applyImports(current, analyzer, context.getNeededImports());
    }

    static String transformCodeSegments(String source, SegmentTransformer transformer, SourceContext context) {
        List<Segment> segments = splitSegments(source);
        StringBuilder out = new StringBuilder(source.length());
        for (Segment segment : segments) {
            if (segment.isCode) {
                out.append(transformer.transform(segment.text, context));
            } else {
                out.append(segment.text);
            }
        }
        return out.toString();
    }

    static List<Segment> splitSegments(String source) {
        List<Segment> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean codeSegment = true;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (inLineComment) {
                current.append(c);
                if (c == '\n') {
                    segments.add(new Segment(false, current.toString()));
                    current.setLength(0);
                    inLineComment = false;
                    codeSegment = true;
                }
                continue;
            }

            if (inBlockComment) {
                current.append(c);
                if (c == '*' && next == '/') {
                    current.append(next);
                    i++;
                    segments.add(new Segment(false, current.toString()));
                    current.setLength(0);
                    inBlockComment = false;
                    codeSegment = true;
                }
                continue;
            }

            if (inString) {
                current.append(c);
                if (c == '\\' && next != '\0') {
                    current.append(next);
                    i++;
                    continue;
                }
                if (c == '"') {
                    segments.add(new Segment(false, current.toString()));
                    current.setLength(0);
                    inString = false;
                    codeSegment = true;
                }
                continue;
            }

            if (inChar) {
                current.append(c);
                if (c == '\\' && next != '\0') {
                    current.append(next);
                    i++;
                    continue;
                }
                if (c == '\'') {
                    segments.add(new Segment(false, current.toString()));
                    current.setLength(0);
                    inChar = false;
                    codeSegment = true;
                }
                continue;
            }

            if (c == '/' && next == '/') {
                if (codeSegment && current.length() > 0) {
                    segments.add(new Segment(true, current.toString()));
                    current.setLength(0);
                }
                current.append(c).append(next);
                i++;
                inLineComment = true;
                codeSegment = false;
                continue;
            }

            if (c == '/' && next == '*') {
                if (codeSegment && current.length() > 0) {
                    segments.add(new Segment(true, current.toString()));
                    current.setLength(0);
                }
                current.append(c).append(next);
                i++;
                inBlockComment = true;
                codeSegment = false;
                continue;
            }

            if (c == '"') {
                if (codeSegment && current.length() > 0) {
                    segments.add(new Segment(true, current.toString()));
                    current.setLength(0);
                }
                current.append(c);
                inString = true;
                codeSegment = false;
                continue;
            }

            if (c == '\'') {
                if (codeSegment && current.length() > 0) {
                    segments.add(new Segment(true, current.toString()));
                    current.setLength(0);
                }
                current.append(c);
                inChar = true;
                codeSegment = false;
                continue;
            }

            current.append(c);
        }

        if (current.length() > 0) {
            segments.add(new Segment(codeSegment, current.toString()));
        }
        return segments;
    }

    static boolean[] buildCodeMask(String source) {
        boolean[] mask = new boolean[source.length()];
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                }
                continue;
            }

            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    i++;
                    inBlockComment = false;
                }
                continue;
            }

            if (inString) {
                if (c == '\\' && next != '\0') {
                    i++;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (inChar) {
                if (c == '\\' && next != '\0') {
                    i++;
                    continue;
                }
                if (c == '\'') {
                    inChar = false;
                }
                continue;
            }

            if (c == '/' && next == '/') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '\'') {
                inChar = true;
                continue;
            }
            mask[i] = true;
        }
        return mask;
    }

    static int findMatching(String source, boolean[] mask, int openIndex,
                            char openChar, char closeChar) {
        int depth = 0;
        for (int i = openIndex; i < source.length(); i++) {
            if (mask != null && (i >= mask.length || !mask[i])) {
                continue;
            }
            char c = source.charAt(i);
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

    static int skipWhitespace(String source, int index) {
        int i = index;
        while (i < source.length() && Character.isWhitespace(source.charAt(i))) {
            i++;
        }
        return i;
    }

    static int findKeyword(String source, boolean[] mask, String keyword, int start) {
        int len = keyword.length();
        int i = start;
        while (i <= source.length() - len) {
            if (mask != null && !mask[i]) {
                i++;
                continue;
            }
            if (source.regionMatches(i, keyword, 0, len)) {
                boolean startOk = i == 0 || !Character.isJavaIdentifierPart(source.charAt(i - 1));
                boolean endOk = i + len >= source.length()
                        || !Character.isJavaIdentifierPart(source.charAt(i + len));
                if (startOk && endOk) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    static final class Segment {
        final boolean isCode;
        final String text;

        Segment(boolean isCode, String text) {
            this.isCode = isCode;
            this.text = text;
        }
    }

    interface SegmentTransformer {
        String transform(String code, SourceContext context);
    }
}
