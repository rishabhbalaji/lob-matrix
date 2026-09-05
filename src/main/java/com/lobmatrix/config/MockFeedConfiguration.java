package com.lobmatrix.config;

import com.lobmatrix.core.adapter.MockMarketReplayFeeder;
import com.lobmatrix.websocket.UiFrameDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
@ConditionalOnProperty(
        prefix = "lobmatrix.dashboard",
        name = "mock-feed-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MockFeedConfiguration {

    public static final long DASHBOARD_TOKEN = 1001L;

    /**
     * M5P3S1: The live dashboard renders a Level-20 depth ladder (20 bid
     * rows and 20 ask rows). The mock feed must therefore emit 20 depth
     * levels per side rather than the adapter's Top-5 default, so the
     * WebSocket payload always has enough levels for the UI to render.
     */
    public static final int DASHBOARD_DEPTH_LEVELS = 20;
    public static final long DASHBOARD_EMISSION_INTERVAL_MS = 10L;
    public static final long DASHBOARD_RANDOM_SEED = 42L;

    @Bean(destroyMethod = "disconnect")
    public MockMarketReplayFeeder dashboardMockMarketReplayFeeder(
            UiFrameDispatcher uiFrameDispatcher
    ) {
        MockMarketReplayFeeder feeder = new MockMarketReplayFeeder(
                "MOCK",
                DASHBOARD_DEPTH_LEVELS,
                DASHBOARD_EMISSION_INTERVAL_MS,
                DASHBOARD_RANDOM_SEED
        );
        feeder.registerListener(uiFrameDispatcher::publish);
        feeder.subscribe(Set.of(DASHBOARD_TOKEN));
        feeder.connect();
        return feeder;
    }
}
