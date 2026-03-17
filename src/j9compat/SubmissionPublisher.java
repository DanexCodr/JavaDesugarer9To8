package j9compat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Java 8-compatible backport of {@link java.util.concurrent.SubmissionPublisher}.
 */
public class SubmissionPublisher<T> implements Flow.Publisher<T>, AutoCloseable {

    static final int MAX_BUFFER_CAPACITY = 1 << 30;
    static final int INITIAL_CAPACITY = 16;

    private final CopyOnWriteArrayList<BufferedSubscription<T>> subscribers =
            new CopyOnWriteArrayList<BufferedSubscription<T>>();
    private final Executor executor;
    private final BiConsumer<? super Flow.Subscriber<? super T>, ? super Throwable> onNextHandler;
    private final int maxBufferCapacity;
    private volatile boolean closed;
    private volatile Throwable closedException;

    public SubmissionPublisher() {
        this(ForkJoinPool.commonPool(), Flow.defaultBufferSize(), null);
    }

    public SubmissionPublisher(Executor executor, int maxBufferCapacity) {
        this(executor, maxBufferCapacity, null);
    }

    public SubmissionPublisher(Executor executor, int maxBufferCapacity,
                               BiConsumer<? super Flow.Subscriber<? super T>, ? super Throwable> onNextHandler) {
        this.executor = Objects.requireNonNull(executor, "executor");
        if (maxBufferCapacity <= 0) {
            throw new IllegalArgumentException("maxBufferCapacity <= 0");
        }
        this.maxBufferCapacity = roundCapacity(maxBufferCapacity);
        this.onNextHandler = onNextHandler;
    }

    static int roundCapacity(int capacity) {
        int rounded = 1;
        while (rounded < capacity && rounded < MAX_BUFFER_CAPACITY) {
            rounded <<= 1;
        }
        return Math.min(rounded, MAX_BUFFER_CAPACITY);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber");
        if (closed) {
            subscriber.onSubscribe(EmptySubscription.INSTANCE);
            Throwable error = closedException;
            if (error != null) {
                subscriber.onError(error);
            } else {
                subscriber.onComplete();
            }
            return;
        }
        BufferedSubscription<T> subscription = new BufferedSubscription<T>(subscriber, this);
        subscribers.add(subscription);
        try {
            subscriber.onSubscribe(subscription);
        } catch (Throwable t) {
            subscribers.remove(subscription);
            subscription.cancel();
            handleSubscriberError(subscriber, t);
        }
    }

    public int submit(T item) {
        return offer(item, null);
    }

    public int offer(T item, BiPredicate<Flow.Subscriber<? super T>, ? super T> onDrop) {
        return offer(item, 0L, TimeUnit.NANOSECONDS, onDrop);
    }

    public int offer(T item, long timeout, TimeUnit unit,
                     BiPredicate<Flow.Subscriber<? super T>, ? super T> onDrop) {
        Objects.requireNonNull(item, "item");
        if (closed) {
            throw new IllegalStateException("SubmissionPublisher is closed");
        }
        int maxLag = 0;
        for (BufferedSubscription<T> subscription : subscribers) {
            int lag = subscription.offer(item, timeout, unit, onDrop);
            if (lag > maxLag) {
                maxLag = lag;
            }
        }
        return maxLag;
    }

    @Override
    public void close() {
        closed = true;
        for (BufferedSubscription<T> subscription : subscribers) {
            subscription.signalClose(null);
        }
    }

    public void closeExceptionally(Throwable error) {
        closedException = error == null ? new NullPointerException("error") : error;
        closed = true;
        for (BufferedSubscription<T> subscription : subscribers) {
            subscription.signalClose(closedException);
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public Throwable getClosedException() {
        return closedException;
    }

    public boolean hasSubscribers() {
        return !subscribers.isEmpty();
    }

    public int getNumberOfSubscribers() {
        return subscribers.size();
    }

    public Executor getExecutor() {
        return executor;
    }

    public int getMaxBufferCapacity() {
        return maxBufferCapacity;
    }

    public List<Flow.Subscriber<? super T>> getSubscribers() {
        List<Flow.Subscriber<? super T>> list = new ArrayList<Flow.Subscriber<? super T>>();
        for (BufferedSubscription<T> subscription : subscribers) {
            list.add(subscription.subscriber);
        }
        return Collections.unmodifiableList(list);
    }

    public boolean isSubscribed(Flow.Subscriber<? super T> subscriber) {
        if (subscriber == null) {
            return false;
        }
        for (BufferedSubscription<T> subscription : subscribers) {
            if (subscription.subscriber == subscriber) {
                return true;
            }
        }
        return false;
    }

    public long estimateMinimumDemand() {
        long min = Long.MAX_VALUE;
        for (BufferedSubscription<T> subscription : subscribers) {
            long demand = subscription.demand();
            if (demand < min) {
                min = demand;
            }
        }
        return min == Long.MAX_VALUE ? 0L : min;
    }

    public int estimateMaximumLag() {
        int max = 0;
        for (BufferedSubscription<T> subscription : subscribers) {
            int lag = subscription.lag();
            if (lag > max) {
                max = lag;
            }
        }
        return max;
    }

    public CompletableFuture<Void> consume(final Consumer<? super T> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        final CompletableFuture<Void> future = new CompletableFuture<Void>();
        subscribe(new Flow.Subscriber<T>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T item) {
                consumer.accept(item);
            }

            @Override
            public void onError(Throwable throwable) {
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                future.complete(null);
            }
        });
        return future;
    }

    void remove(BufferedSubscription<T> subscription) {
        subscribers.remove(subscription);
    }

    void handleSubscriberError(Flow.Subscriber<? super T> subscriber, Throwable error) {
        if (onNextHandler != null) {
            onNextHandler.accept(subscriber, error);
            return;
        }
        subscriber.onError(error);
    }

    private static final class EmptySubscription implements Flow.Subscription {
        private static final EmptySubscription INSTANCE = new EmptySubscription();

        @Override
        public void request(long n) {
        }

        @Override
        public void cancel() {
        }
    }

    private static final class BufferedSubscription<T> implements Flow.Subscription {
        private final Flow.Subscriber<? super T> subscriber;
        private final SubmissionPublisher<T> publisher;
        private final ArrayDeque<T> queue = new ArrayDeque<T>(INITIAL_CAPACITY);
        private long demand;
        private boolean cancelled;
        private boolean closing;
        private boolean draining;
        private Throwable terminalError;

        private BufferedSubscription(Flow.Subscriber<? super T> subscriber,
                                     SubmissionPublisher<T> publisher) {
            this.subscriber = subscriber;
            this.publisher = publisher;
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                cancel();
                subscriber.onError(new IllegalArgumentException("non-positive subscription request"));
                return;
            }
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                if (demand + n < 0L) {
                    demand = Long.MAX_VALUE;
                } else {
                    demand += n;
                }
            }
            signalDrain();
        }

        @Override
        public void cancel() {
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                cancelled = true;
                queue.clear();
            }
            publisher.remove(this);
        }

        int offer(T item, long timeout, TimeUnit unit,
                  BiPredicate<Flow.Subscriber<? super T>, ? super T> onDrop) {
            synchronized (this) {
                if (cancelled) {
                    return 0;
                }
                if (queue.size() >= publisher.maxBufferCapacity) {
                    if (onDrop != null && onDrop.test(subscriber, item)) {
                        return queue.size();
                    }
                    if (timeout > 0L && unit != null) {
                        try {
                            unit.timedWait(this, timeout);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        if (queue.size() < publisher.maxBufferCapacity) {
                            queue.add(item);
                        } else if (onDrop != null && onDrop.test(subscriber, item)) {
                            return queue.size();
                        } else {
                            throw new IllegalStateException("SubmissionPublisher buffer full");
                        }
                    } else {
                        throw new IllegalStateException("SubmissionPublisher buffer full");
                    }
                } else {
                    queue.add(item);
                }
                int lag = queue.size();
                signalDrain();
                return lag;
            }
        }

        long demand() {
            synchronized (this) {
                return demand;
            }
        }

        int lag() {
            synchronized (this) {
                return queue.size();
            }
        }

        void signalClose(Throwable error) {
            synchronized (this) {
                if (cancelled || closing) {
                    return;
                }
                closing = true;
                terminalError = error;
            }
            signalDrain();
        }

        private void signalDrain() {
            synchronized (this) {
                if (draining || cancelled) {
                    return;
                }
                draining = true;
            }
            publisher.executor.execute(new Runnable() {
                @Override
                public void run() {
                    drain();
                }
            });
        }

        private void drain() {
            while (true) {
                T item = null;
                boolean complete = false;
                Throwable error = null;
                synchronized (this) {
                    if (cancelled) {
                        draining = false;
                        return;
                    }
                    if (demand > 0 && !queue.isEmpty()) {
                        item = queue.poll();
                        demand--;
                    } else {
                        if (closing && queue.isEmpty()) {
                            complete = true;
                            error = terminalError;
                            cancelled = true;
                            draining = false;
                        } else {
                            draining = false;
                        }
                    }
                }

                if (item != null) {
                    try {
                        subscriber.onNext(item);
                    } catch (Throwable t) {
                        cancel();
                        publisher.handleSubscriberError(subscriber, t);
                        return;
                    }
                    continue;
                }

                if (complete) {
                    if (error != null) {
                        subscriber.onError(error);
                    } else {
                        subscriber.onComplete();
                    }
                    publisher.remove(this);
                }
                return;
            }
        }
    }
}
