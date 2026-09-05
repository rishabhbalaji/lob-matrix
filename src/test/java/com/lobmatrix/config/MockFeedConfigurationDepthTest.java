package com.lobmatrix.config;

import com.lobmatrix.core.adapter.MockMarketReplayFeeder;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5P3S1: The dashboard's mock market feed must emit 20 depth levels per
 * side so the Level-20 depth ladder always has real data to render. This
 * test exercises the feed with the exact parameters MockFeedConfiguration
 * uses in production, without requiring a full Spring context.
 */
class MockFeedConfigurationDepthTest {

    @Test
    void dashboardMockFeedEmitsTwentyLevelsPerSide() {
        MockMarketReplayFeeder feeder = new MockMarketReplayFeeder(
                "MOCK",
                MockFeedConfiguration.DASHBOARD_DEPTH_LEVELS,
                MockFeedConfiguration.DASHBOARD_EMISSION_INTERVAL_MS,
                MockFeedConfiguration.DASHBOARD_RANDOM_SEED
        );

        CanonicalMarketSnapshot snapshot = feeder.generateNextSnapshot(
                MockFeedConfiguration.DASHBOARD_TOKEN, "SYM1001");

        assertThat(snapshot.depthLevels()).isEqualTo(20);
        assertThat(snapshot.bidPrices()).hasSize(20);
        assertThat(snapshot.askPrices()).hasSize(20);
        assertThat(snapshot.bidQuantities()).hasSize(20);
        assertThat(snapshot.askQuantities()).hasSize(20);
    }
}
