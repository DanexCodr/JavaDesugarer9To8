package j9compat;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.DoubleConsumer;
import java.util.stream.DoubleStream;

/**
 * Java 8-compatible backport of {@link java.util.OptionalDouble} methods added
 * in Java 9/11.
 */
public final class OptionalDoubleBackport {

    private OptionalDoubleBackport() {}

    public static void ifPresentOrElse(OptionalDouble optional,
                                       DoubleConsumer action,
                                       Runnable emptyAction) {
        Objects.requireNonNull(optional, "optional");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(emptyAction, "emptyAction");

        if (optional.isPresent()) {
            action.accept(optional.getAsDouble());
        } else {
            emptyAction.run();
        }
    }

    public static DoubleStream stream(OptionalDouble optional) {
        Objects.requireNonNull(optional, "optional");
        return optional.isPresent()
                ? DoubleStream.of(optional.getAsDouble())
                : DoubleStream.empty();
    }

    public static boolean isEmpty(OptionalDouble optional) {
        Objects.requireNonNull(optional, "optional");
        return !optional.isPresent();
    }

    public static double orElseThrow(OptionalDouble optional) {
        Objects.requireNonNull(optional, "optional");
        if (optional.isPresent()) {
            return optional.getAsDouble();
        }
        throw new NoSuchElementException("No value present");
    }
}
