package com.lobmatrix.websocket;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class UiFrameDispatcherTest {

    @Test
    void publishIsNonBlockingWhenConsumerIsSlow() throws InterruptedException {
        CountDownLatch consumerStarted = new CountDownLatch(1);
        UiFrameDispatcher dispatcher = new UiFrameDispatcher(2, 0, snapshot -> {
            consumerStarted.countDown();
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });

        dispatcher.start();
        try {
            dispatcher.publish(snapshot(1));
            assertThat(consumerStarted.await(1, TimeUnit.SECONDS)).isTrue();

            long startedAt = System.nanoTime();
            assertThatCode(() -> {
                for (int index = 2; index <= 10_000; index++) {
                    dispatcher.publish(snapshot(index));
                }
            }).doesNotThrowAnyException();
            long publishElapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertThat(publishElapsedMillis).isLessThan(250L);
            assertThat(dispatcher.publishedFrameCount()).isEqualTo(10_000);
            assertThat(dispatcher.droppedOldestFrameCount()).isGreaterThan(0);
        } finally {
            dispatcher.stop();
        }
    }

    @Test
    void dispatchesNewestFrameAndDropsObsoleteBacklog() throws InterruptedException {
        List<Long> deliveredTokens = new CopyOnWriteArrayList<>();
        CountDownLatch delivered = new CountDownLatch(1);
        UiFrameDispatcher dispatcher = new UiFrameDispatcher(8, 50, snapshot -> {
            deliveredTokens.add(snapshot.instrumentToken());
            delivered.countDown();
        });

        for (int token = 1; token <= 8; token++) {
            dispatcher.publish(snapshot(token));
        }

        dispatcher.start();
        try {
            assertThat(delivered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(deliveredTokens).containsExactly(8L);
            assertThat(dispatcher.droppedOldestFrameCount()).isEqualTo(7L);
        } finally {
            dispatcher.stop();
        }
    }

    @Test
    void rejectsInvalidDispatcherConfiguration() {
        assertThatCode(() -> new UiFrameDispatcher(1, 0, ignored -> { }))
                .doesNotThrowAnyException();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new UiFrameDispatcher(0, 0, ignored -> { })
        ).isInstanceOf(IllegalArgumentException.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new UiFrameDispatcher(1, -1, ignored -> { })
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private static CanonicalMarketSnapshot snapshot(long token) {
        return new CanonicalMarketSnapshot(
                "MOCK", token, "SYM_" + token, token, token, token,
                1000.0 + token, 1L, token, 1000.0, 1,
                new double[]{999.5}, new long[]{100L}, new int[]{1},
                new double[]{1000.5}, new long[]{100L}, new int[]{1},
                BookStateTag.NORMAL
        );
    }
}
