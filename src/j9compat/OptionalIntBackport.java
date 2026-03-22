package j9compat;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * Java 8-compatible backport of {@link java.util.OptionalInt} methods added
 * in Java 9.
 */
public final class OptionalIntBackport {

    private OptionalIntBackport() {}

    public static void ifPresentOrElse(OptionalInt optional,
                                       IntConsumer action,
                                       Runnable emptyAction) {
        Objects.requireNonNull(optional, "optional");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(emptyAction, "emptyAction");

        if (optional.isPresent()) {
            action.accept(optional.getAsInt());
        } else {
            emptyAction.run();
        }
    }

    public static IntStream stream(OptionalInt optional) {
        Objects.requireNonNull(optional, "optional");
        return optional.isPresent()
                ? IntStream.of(optional.getAsInt())
                : IntStream.empty();
    }

    public static int orElseThrow(OptionalInt optional) {
        Objects.requireNonNull(optional, "optional");
        if (optional.isPresent()) {
            return optional.getAsInt();
        }
        throw new NoSuchElementException("No value present");
    }
}
