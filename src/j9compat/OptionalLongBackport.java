package j9compat;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.LongConsumer;
import java.util.stream.LongStream;

/**
 * Java 8-compatible backport of {@link java.util.OptionalLong} methods added
 * in Java 9.
 */
public final class OptionalLongBackport {

    private OptionalLongBackport() {}

    public static void ifPresentOrElse(OptionalLong optional,
                                       LongConsumer action,
                                       Runnable emptyAction) {
        Objects.requireNonNull(optional, "optional");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(emptyAction, "emptyAction");

        if (optional.isPresent()) {
            action.accept(optional.getAsLong());
        } else {
            emptyAction.run();
        }
    }

    public static LongStream stream(OptionalLong optional) {
        Objects.requireNonNull(optional, "optional");
        return optional.isPresent()
                ? LongStream.of(optional.getAsLong())
                : LongStream.empty();
    }

    public static long orElseThrow(OptionalLong optional) {
        Objects.requireNonNull(optional, "optional");
        if (optional.isPresent()) {
            return optional.getAsLong();
        }
        throw new NoSuchElementException("No value present");
    }
}
