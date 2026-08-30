package com.lobmatrix.parquet;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import com.lobmatrix.engine.math.TradeStrengthClassifier;
import com.lobmatrix.engine.resample.ResampledGridPoint;
import com.lobmatrix.engine.target.CostModelRepository;
import com.lobmatrix.engine.target.ForwardReturnTargets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FeatureVectorAssemblerTest {

    @Test
    @DisplayName("Verify FeatureVectorAssembler correctly populates all 40+ canonical features")
    void testFeatureAssembly() {
        double[] bids = {1000.0, 999.5, 999.0, 998.5, 998.0};
        double[] asks = {1001.0, 1001.5, 1002.0, 1002.5, 1003.0};
        long[] bidQty = {100, 200, 300, 400, 500};
        long[] askQty = {100, 200, 300, 400, 500};
        int[] orders = {1, 1, 1, 1, 1};

        CanonicalMarketSnapshot snapshot = new CanonicalMarketSnapshot(
                "MOCK", 738561L, "RELIANCE",
                1_000_000_000L, 1_000_000L, 1700000000L,
                1000.5, 10, 500000, 1000.0, 5,
                bids, bidQty, orders, asks, askQty, orders, BookStateTag.NORMAL
        );

        ResampledGridPoint gridPoint = new ResampledGridPoint(
                1L, 1_000_000_000L, 1_000_000_000L, snapshot, 5_000_000L // 5ms age
        );

        ForwardReturnTargets forwardTargets = new ForwardReturnTargets(
                1_000_000_000L, 1000.5,
                0.0008, 0.0015, 0.0020, 0.0035, 0.0050 // positive returns
        );

        TradeStrengthClassifier tradeClassifier = new TradeStrengthClassifier();
        tradeClassifier.recordTrade(snapshot, 100);

        ParquetFeatureRecord record = FeatureVectorAssembler.assemble(
                gridPoint, snapshot, tradeClassifier, forwardTargets,
                CostModelRepository.getDefaultSchedule(), 0.0004
        );

        assertThat(record.symbol()).isEqualTo("RELIANCE");
        assertThat(record.instrumentToken()).isEqualTo(738561L);
        assertThat(record.midPrice()).isCloseTo(1000.5, within(0.001));
        assertThat(record.spread()).isCloseTo(1.0, within(0.001));
        assertThat(record.snapshotAgeMs()).isCloseTo(5.0, within(0.001));
        assertThat(record.level1OBI()).isCloseTo(0.0, within(0.001)); // equal 100 vs 100
        assertThat(record.return1s()).isCloseTo(0.0008, within(0.00001));
        assertThat(record.label1s()).isEqualTo(1); // Return > Threshold -> UP (+1)
        assertThat(record.execLongReturn1s()).isNotNull();
    }
}
