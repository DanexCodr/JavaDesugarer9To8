package j9compat;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Java 8-compatible backport of {@link java.util.function.Predicate#not}.
 */
public final class PredicateBackport {

    private PredicateBackport() {}

    public static <T> Predicate<T> not(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        @SuppressWarnings("unchecked")
        Predicate<T> negated = (Predicate<T>) predicate.negate();
        return negated;
    }
}
