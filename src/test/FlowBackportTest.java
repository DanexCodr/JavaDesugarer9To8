package test;

import j9compat.Flow;

import static test.BackportTestRunner.*;

/**
 * Tests for {@link j9compat.Flow}.
 */
public final class FlowBackportTest {

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
    }
}
