package j9compat;

/**
 * Java 8-compatible backport of {@link java.util.concurrent.Flow} (interfaces
 * and default buffer size only).
 */
public final class Flow {

    static final int DEFAULT_BUFFER_SIZE = 256;

    private Flow() {}

    public static int defaultBufferSize() {
        return DEFAULT_BUFFER_SIZE;
    }

    public interface Publisher<T> {
        void subscribe(Subscriber<? super T> subscriber);
    }

    public interface Subscriber<T> {
        void onSubscribe(Subscription subscription);
        void onNext(T item);
        void onError(Throwable throwable);
        void onComplete();
    }

    public interface Subscription {
        void request(long n);
        void cancel();
    }

    public interface Processor<T, R> extends Subscriber<T>, Publisher<R> {}
}
