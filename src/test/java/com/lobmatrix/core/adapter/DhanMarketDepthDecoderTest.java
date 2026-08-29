package com.lobmatrix.core.adapter;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DhanMarketDepthDecoderTest {

    @Test
    @DisplayName("Verify 20-level Dhan JSON market depth decodes into 20-level CanonicalMarketSnapshot")
    void testDhanTop20Decoding() throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{")
            .append("\"securityId\": 1333,")
            .append("\"LTP\": 1450.25,")
            .append("\"LTQ\": 100,")
            .append("\"avgPrice\": 1448.80,")
            .append("\"volume\": 3200000,")
            .append("\"exchangeTime\": 1700001234,")
            .append("\"depth\": {")
            .append("\"buy\": [");

        // 20 Buy Levels
        for (int i = 0; i < 20; i++) {
            double price = 1450.00 - (i * 0.10);
            long qty = 500 + (i * 50);
            int orders = 5 + i;
            json.append(String.format("{\"price\": %.2f, \"quantity\": %d, \"orders\": %d}", price, qty, orders));
            if (i < 19) json.append(",");
        }
        json.append("], \"sell\": [");

        // 20 Sell Levels
        for (int i = 0; i < 20; i++) {
            double price = 1450.50 + (i * 0.10);
            long qty = 400 + (i * 40);
            int orders = 3 + i;
            json.append(String.format("{\"price\": %.2f, \"quantity\": %d, \"orders\": %d}", price, qty, orders));
            if (i < 19) json.append(",");
        }
        json.append("]}}");

        byte[] payload = json.toString().getBytes(StandardCharsets.UTF_8);

        DhanMarketDepthDecoder decoder = new DhanMarketDepthDecoder(Map.of(1333L, "HDFCBANK"));
        long arrivalNanos = 123_456_789L;
        long arrivalMicros = 1_700_001_234_000_000L;

        CanonicalMarketSnapshot snapshot = decoder.decode(payload, arrivalNanos, arrivalMicros);

        // Assertions
        assertThat(snapshot.sourceId()).isEqualTo("DHAN");
        assertThat(snapshot.instrumentToken()).isEqualTo(1333L);
        assertThat(snapshot.symbol()).isEqualTo("HDFCBANK");
        assertThat(snapshot.depthLevels()).isEqualTo(20);
        assertThat(snapshot.bidPrices().length).isEqualTo(20);
        assertThat(snapshot.askPrices().length).isEqualTo(20);

        // Level 1 checks
        assertThat(snapshot.bidPrices()[0]).isCloseTo(1450.00, within(0.001));
        assertThat(snapshot.askPrices()[0]).isCloseTo(1450.50, within(0.001));
        assertThat(snapshot.spread()).isCloseTo(0.50, within(0.001));
        assertThat(snapshot.midPrice()).isCloseTo(1450.25, within(0.001));
        assertThat(snapshot.stateTag()).isEqualTo(BookStateTag.NORMAL);

        // Level 20 checks
        assertThat(snapshot.bidPrices()[19]).isCloseTo(1448.10, within(0.001));
        assertThat(snapshot.askPrices()[19]).isCloseTo(1452.40, within(0.001));
    }
}
