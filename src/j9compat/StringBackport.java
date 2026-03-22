package j9compat;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Java 8-compatible backport of {@link java.lang.String} methods added in
 * Java 11.
 */
public final class StringBackport {

    private StringBackport() {}

    public static boolean isBlank(String input) {
        Objects.requireNonNull(input, "input");
        return indexOfNonWhitespace(input) == input.length();
    }

    public static Stream<String> lines(String input) {
        Objects.requireNonNull(input, "input");
        BufferedReader reader = new BufferedReader(new StringReader(input));
        return reader.lines();
    }

    public static String strip(String input) {
        Objects.requireNonNull(input, "input");
        int start = indexOfNonWhitespace(input);
        if (start == input.length()) {
            return "";
        }
        int end = lastIndexOfNonWhitespace(input);
        return input.substring(start, end);
    }

    public static String stripLeading(String input) {
        Objects.requireNonNull(input, "input");
        int start = indexOfNonWhitespace(input);
        return input.substring(start);
    }

    public static String stripTrailing(String input) {
        Objects.requireNonNull(input, "input");
        int end = lastIndexOfNonWhitespace(input);
        return input.substring(0, end);
    }

    public static String repeat(String input, int count) {
        Objects.requireNonNull(input, "input");
        if (count < 0) {
            throw new IllegalArgumentException("count is negative: " + count);
        }
        if (count == 0 || input.isEmpty()) {
            return "";
        }
        if (count == 1) {
            return input;
        }
        long size = (long) input.length() * (long) count;
        if (size > Integer.MAX_VALUE) {
            throw new OutOfMemoryError("Required array size too large");
        }
        StringBuilder builder = new StringBuilder((int) size);
        for (int i = 0; i < count; i++) {
            builder.append(input);
        }
        return builder.toString();
    }

    private static int indexOfNonWhitespace(String input) {
        int length = input.length();
        int index = 0;
        while (index < length) {
            int codePoint = input.codePointAt(index);
            if (!Character.isWhitespace(codePoint)) {
                return index;
            }
            index += Character.charCount(codePoint);
        }
        return length;
    }

    private static int lastIndexOfNonWhitespace(String input) {
        int index = input.length();
        while (index > 0) {
            int codePoint = input.codePointBefore(index);
            if (!Character.isWhitespace(codePoint)) {
                return index;
            }
            index -= Character.charCount(codePoint);
        }
        return 0;
    }
}
