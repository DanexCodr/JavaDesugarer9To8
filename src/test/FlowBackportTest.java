package test;

import j9compat.Flow;
import j9compat.SubmissionPublisher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static test.BackportTestRunner.*;

/**
 * Tests for {@link j9compat.Flow}.
 */
public final class FlowBackportTest {

    private static final int PUBLISHER_BUFFER_SIZE = 16;
    private static final int OVERFLOW_TEST_BUFFER_SIZE = 1;

    static void run() {
        section("FlowBackport");

        assertEquals(256, Flow.defaultBufferSize(), "Flow.defaultBufferSize: default is 256");

        final boolean[] subscribed = {false};
        Flow.Subscription subscription = new Flow.Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
        };
        Flow.Subscriber<String> subscriber = new Flow.Subscriber<String>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscribed[0] = true;
            }

            @Override
            public void onNext(String item) {
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        };
        Flow.Publisher<String> publisher = new Flow.Publisher<String>() {
            @Override
            public void subscribe(Flow.Subscriber<? super String> target) {
                target.onSubscribe(subscription);
            }
        };

        publisher.subscribe(subscriber);
        assertTrue(subscribed[0], "Flow.Publisher.subscribe: delivers onSubscribe");

        testSubmissionPublisher();
        testSubmissionPublisherDrop();
    }

    private static void testSubmissionPublisher() {
        SubmissionPublisher<String> publisher = new SubmissionPublisher<String>(
                new DirectExecutor(), PUBLISHER_BUFFER_SIZE);
        List<String> received = new ArrayList<String>();
        final boolean[] completed = {false};

        Flow.Subscriber<String> subscriber = new Flow.Subscriber<String>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {
                received.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                fail("SubmissionPublisher.onError: unexpected error " + throwable);
            }

            @Override
            public void onComplete() {
                completed[0] = true;
            }
        };

        publisher.subscribe(subscriber);
        publisher.submit("one");
        publisher.submit("two");
        publisher.close();

        assertEquals(Arrays.asList("one", "two"), received,
                "SubmissionPublisher.submit: delivers submitted items");
        assertTrue(completed[0], "SubmissionPublisher.close: completes subscriber");
    }

    private static void testSubmissionPublisherDrop() {
        SubmissionPublisher<String> publisher = new SubmissionPublisher<String>(
                new DirectExecutor(), OVERFLOW_TEST_BUFFER_SIZE);
        final int[] drops = {0};

        HoldingSubscriber<String> subscriber = new HoldingSubscriber<String>();

        publisher.subscribe(subscriber);
        publisher.offer("first", (target, item) -> {
            drops[0]++;
            return true;
        });
        publisher.offer("second", (target, item) -> {
            drops[0]++;
            return true;
        });
        assertEquals(1, drops[0], "SubmissionPublisher.offer: drops when buffer full");
        subscriber.cancel();
        publisher.close();
    }

    private static final class HoldingSubscriber<T> implements Flow.Subscriber<T> {
        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void onNext(T item) {
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onComplete() {
        }

        void cancel() {
            if (subscription != null) {
                subscription.cancel();
            }
        }
    }

    private static final class DirectExecutor implements java.util.concurrent.Executor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
