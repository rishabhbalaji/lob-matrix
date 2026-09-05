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

    @Bean(destroyMethod = "disconnect")
    public MockMarketReplayFeeder dashboardMockMarketReplayFeeder(
            UiFrameDispatcher uiFrameDispatcher
    ) {
        MockMarketReplayFeeder feeder = new MockMarketReplayFeeder();
        feeder.registerListener(uiFrameDispatcher::publish);
        feeder.subscribe(Set.of(DASHBOARD_TOKEN));
        feeder.connect();
        return feeder;
    }
}
