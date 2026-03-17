package j9compat;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Java 8-compatible backport of the {@link java.util.Optional} methods added
 * in Java 9.
 *
 * <h2>Covered methods</h2>
 * <ul>
 *   <li>{@code Optional.ifPresentOrElse(action, emptyAction)} – runs
 *       {@code action} with the value if present, otherwise runs
 *       {@code emptyAction}.</li>
 *   <li>{@code Optional.or(supplier)} – returns this optional if a value is
 *       present; otherwise returns the optional produced by {@code supplier}.</li>
 *   <li>{@code Optional.stream()} – returns a one-element stream if a value is
 *       present, otherwise an empty stream.</li>
 * </ul>
 *
 * <p>Because these are instance methods on {@code Optional}, the desugarer
 * converts each call to an INVOKESTATIC with the {@code Optional} receiver
 * prepended as the first argument.
 */
public final class OptionalBackport {

    private OptionalBackport() {}

    /**
     * Backport of {@code Optional.ifPresentOrElse(action, emptyAction)}.
     *
     * <p>If a value is present, performs {@code action} with the value;
     * otherwise performs {@code emptyAction}.
     */
    public static <T> void ifPresentOrElse(Optional<T> optional,
                                            Consumer<? super T> action,
                                            Runnable emptyAction) {
        Objects.requireNonNull(optional,    "optional");
        Objects.requireNonNull(action,      "action");
        Objects.requireNonNull(emptyAction, "emptyAction");

        if (optional.isPresent()) {
            action.accept(optional.get());
        } else {
            emptyAction.run();
        }
    }

    /**
     * Backport of {@code Optional.or(supplier)}.
     *
     * <p>If a value is present, returns {@code optional}; otherwise returns
     * the {@code Optional} produced by {@code supplier}.
     *
     * @throws NullPointerException if {@code supplier} is null or if the
     *                              supplier returns null
     */
    public static <T> Optional<T> or(Optional<T> optional,
                                      Supplier<? extends Optional<? extends T>> supplier) {
        Objects.requireNonNull(optional,  "optional");
        Objects.requireNonNull(supplier,  "supplier");

        if (optional.isPresent()) {
            return optional;
        }
        @SuppressWarnings("unchecked")
        Optional<T> result = (Optional<T>) supplier.get();
        return Objects.requireNonNull(result, "supplier returned null Optional");
    }

    /**
     * Backport of {@code Optional.stream()}.
     *
     * <p>If a value is present, returns a sequential {@link Stream} containing
     * only that value; otherwise returns an empty stream.
     */
    public static <T> Stream<T> stream(Optional<T> optional) {
        Objects.requireNonNull(optional, "optional");
        return optional.isPresent() ? Stream.of(optional.get()) : Stream.empty();
    }
}
