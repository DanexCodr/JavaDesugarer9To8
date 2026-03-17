package j9compat;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Java 8-compatible backport of the {@link java.util.Objects} methods added
 * in Java 9.
 *
 * <h2>Covered methods</h2>
 * <ul>
 *   <li>{@code Objects.requireNonNullElse(obj, defaultObj)} – returns
 *       {@code obj} if non-null, otherwise {@code defaultObj}.</li>
 *   <li>{@code Objects.requireNonNullElseGet(obj, supplier)} – returns
 *       {@code obj} if non-null, otherwise the value from {@code supplier}.</li>
 *   <li>{@code Objects.checkIndex(index, length)} – checks that
 *       {@code 0 <= index < length} and throws
 *       {@link IndexOutOfBoundsException} if not.</li>
 *   <li>{@code Objects.checkFromToIndex(fromIndex, toIndex, length)} –
 *       validates that {@code [fromIndex, toIndex)} is within
 *       {@code [0, length)}.</li>
 *   <li>{@code Objects.checkFromIndexSize(fromIndex, size, length)} –
 *       validates that {@code [fromIndex, fromIndex+size)} is within
 *       {@code [0, length)}.</li>
 * </ul>
 */
public final class ObjectsBackport {

    private ObjectsBackport() {}

    /**
     * Backport of {@code Objects.requireNonNullElse(T obj, T defaultObj)}.
     *
     * <p>Returns {@code obj} if it is non-null; otherwise returns
     * {@code defaultObj}.
     *
     * @throws NullPointerException if both {@code obj} and
     *                              {@code defaultObj} are {@code null}
     */
    public static <T> T requireNonNullElse(T obj, T defaultObj) {
        if (obj != null) {
            return obj;
        }
        return Objects.requireNonNull(defaultObj, "defaultObj");
    }

    /**
     * Backport of
     * {@code Objects.requireNonNullElseGet(T obj, Supplier<? extends T> supplier)}.
     *
     * <p>Returns {@code obj} if it is non-null; otherwise returns the non-null
     * value supplied by {@code supplier}.
     *
     * @throws NullPointerException if both {@code obj} is null and
     *                              {@code supplier} is null or supplies null
     */
    public static <T> T requireNonNullElseGet(T obj,
                                               Supplier<? extends T> supplier) {
        if (obj != null) {
            return obj;
        }
        Objects.requireNonNull(supplier, "supplier");
        return Objects.requireNonNull(supplier.get(), "supplier.get()");
    }

    /**
     * Backport of {@code Objects.checkIndex(int index, int length)}.
     *
     * <p>Checks that {@code index} is within the bounds of a range from
     * {@code 0} (inclusive) to {@code length} (exclusive).
     *
     * @return {@code index} if valid
     * @throws IndexOutOfBoundsException if {@code index < 0 || index >= length}
     */
    public static int checkIndex(int index, int length) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException(
                    "Index: " + index + ", Length: " + length);
        }
        return index;
    }

    /**
     * Backport of
     * {@code Objects.checkFromToIndex(int fromIndex, int toIndex, int length)}.
     *
     * <p>Checks that the sub-range {@code [fromIndex, toIndex)} is within the
     * bounds of a range {@code [0, length)}.
     *
     * @return {@code fromIndex} if valid
     * @throws IndexOutOfBoundsException if the sub-range is out of bounds
     */
    public static int checkFromToIndex(int fromIndex, int toIndex, int length) {
        if (fromIndex < 0 || fromIndex > toIndex || toIndex > length) {
            throw new IndexOutOfBoundsException(
                    "fromIndex=" + fromIndex + ", toIndex=" + toIndex
                    + ", length=" + length);
        }
        return fromIndex;
    }

    /**
     * Backport of
     * {@code Objects.checkFromIndexSize(int fromIndex, int size, int length)}.
     *
     * <p>Checks that the sub-range {@code [fromIndex, fromIndex+size)} is
     * within the bounds of a range {@code [0, length)}.
     *
     * @return {@code fromIndex} if valid
     * @throws IndexOutOfBoundsException if the sub-range is out of bounds
     */
    public static int checkFromIndexSize(int fromIndex, int size, int length) {
        if (fromIndex < 0 || size < 0 || fromIndex > length - size) {
            throw new IndexOutOfBoundsException(
                    "fromIndex=" + fromIndex + ", size=" + size
                    + ", length=" + length);
        }
        return fromIndex;
    }
}
