package com.lobmatrix.core.adapter;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ZerodhaKiteBinaryDecoderTest {

    @Test
    @DisplayName("Verify 184-byte binary Zerodha frame decodes correctly with 5-level depth")
    void testZerodhaBinaryDecoding() {
        byte[] packet = new byte[184];
        ByteBuffer buf = ByteBuffer.wrap(packet);

        // Populate header
        buf.putInt(738561);         // Token: RELIANCE
        buf.putInt(250050);         // LTP: 2500.50
        buf.putInt(50);             // LTQ: 50
        buf.putInt(249820);         // ATP: 2498.20
        buf.putInt(1_500_000);      // Volume
        buf.putInt(80_000);         // Total Buy Qty
        buf.putInt(95_000);         // Total Sell Qty

        // OHLC (16 bytes)
        buf.putInt(248000);         // Open: 2480.00
        buf.putInt(251000);         // High: 2510.00
        buf.putInt(247500);         // Low: 2475.00
        buf.putInt(248500);         // Close: 2485.00

        buf.putInt(1700000000);     // Last trade time
        buf.putInt(0); buf.putInt(0); buf.putInt(0); // OI fields
        buf.putInt(1700000005);     // Exchange timestamp

        // 5 Bid Levels: [Qty(4), Price(4), Orders(2), Padding(2)]
        double[] expectedBids = {2500.00, 2499.50, 2499.00, 2498.50, 2498.00};
        long[] expectedBidQty = {100, 250, 500, 1000, 2000};
        for (int i = 0; i < 5; i++) {
            buf.putInt((int) expectedBidQty[i]);
            buf.putInt((int) (expectedBids[i] * 100));
            buf.putShort((short) (i + 1));
            buf.putShort((short) 0);
        }

        // 5 Ask Levels
        double[] expectedAsks = {2500.50, 2501.00, 2501.50, 2502.00, 2502.50};
        long[] expectedAskQty = {150, 300, 600, 1200, 2400};
        for (int i = 0; i < 5; i++) {
            buf.putInt((int) expectedAskQty[i]);
            buf.putInt((int) (expectedAsks[i] * 100));
            buf.putShort((short) (i + 1));
            buf.putShort((short) 0);
        }

        // Execute decoder
        ZerodhaKiteBinaryDecoder decoder = new ZerodhaKiteBinaryDecoder(Map.of(738561L, "RELIANCE"));
        long arrivalNanos = 999_888_777L;
        long arrivalMicros = 1_700_000_005_000_000L;

        CanonicalMarketSnapshot snapshot = decoder.decode(packet, 0, arrivalNanos, arrivalMicros);

        // Validate decoded canonical snapshot
        assertThat(snapshot.sourceId()).isEqualTo("ZERODHA");
        assertThat(snapshot.instrumentToken()).isEqualTo(738561L);
        assertThat(snapshot.symbol()).isEqualTo("RELIANCE");
        assertThat(snapshot.clientArrivalNanos()).isEqualTo(arrivalNanos);
        assertThat(snapshot.clientArrivalMicros()).isEqualTo(arrivalMicros);
        assertThat(snapshot.exchangeEpochSecs()).isEqualTo(1700000005L);
        assertThat(snapshot.ltp()).isCloseTo(2500.50, within(0.001));
        assertThat(snapshot.ltq()).isEqualTo(50);
        assertThat(snapshot.dayVwap()).isCloseTo(2498.20, within(0.001));
        assertThat(snapshot.cumulativeVolume()).isEqualTo(1_500_000L);
        assertThat(snapshot.depthLevels()).isEqualTo(5);
        assertThat(snapshot.stateTag()).isEqualTo(BookStateTag.NORMAL);

        // Validate depth arrays
        assertThat(snapshot.bidPrices()).containsExactly(expectedBids);
        assertThat(snapshot.bidQuantities()).containsExactly(expectedBidQty);
        assertThat(snapshot.askPrices()).containsExactly(expectedAsks);
        assertThat(snapshot.askQuantities()).containsExactly(expectedAskQty);
        assertThat(snapshot.spread()).isCloseTo(0.50, within(0.001));
        assertThat(snapshot.midPrice()).isCloseTo(2500.25, within(0.001));
    }
}
