package com.lobmatrix.inference;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import com.lobmatrix.engine.math.TradeStrengthClassifier;
import com.lobmatrix.engine.resample.ResampledGridPoint;
import com.lobmatrix.engine.target.CostModelRepository;
import com.lobmatrix.engine.target.ForwardReturnTargets;
import com.lobmatrix.parquet.FeatureVectorAssembler;
import com.lobmatrix.parquet.ParquetFeatureRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AdaptivePredictionServiceTest {

    private static final Path REAL_MODEL_PATH = Path.of("data/models/champion_model.onnx");
    private static final Path REAL_METADATA_PATH = Path.of("data/models/modelmetadata.json");
    private static final Path REAL_SCALER_PATH = Path.of("data/models/scalerparams.json");

    private static ParquetFeatureRecord sampleRecord() {
        double[] bids = {1000.0, 999.5, 999.0, 998.5, 998.0};
        double[] asks = {1001.0, 1001.5, 1002.0, 1002.5, 1003.0};
        long[] bidQty = {100, 200, 300, 400, 500};
        long[] askQty = {120, 200, 300, 400, 500};
        int[] orders = {1, 1, 1, 1, 1};

        CanonicalMarketSnapshot snapshot = new CanonicalMarketSnapshot(
                "MOCK", 738561L, "RELIANCE",
                1_000_000_000L, 1_000_000L, 1700000000L,
                1000.5, 10, 500000, 1000.0, 5,
                bids, bidQty, orders, asks, askQty, orders, BookStateTag.NORMAL
        );

        ResampledGridPoint gridPoint = new ResampledGridPoint(
                1L, 1_000_000_000L, 1_000_000_000L, snapshot, 5_000_000L
        );

        ForwardReturnTargets forwardTargets = new ForwardReturnTargets(
                1_000_000_000L, 1000.5,
                0.0004, 0.0008, 0.0012, 0.0020, 0.0030
        );

        TradeStrengthClassifier tradeClassifier = new TradeStrengthClassifier();
        tradeClassifier.recordTrade(snapshot, 100);

        return FeatureVectorAssembler.assemble(
                gridPoint, snapshot, tradeClassifier, forwardTargets,
                CostModelRepository.getDefaultSchedule(), 0.0004
        );
    }

    @Test
    void coldStartWithMissingModelFallsBackToBaselineWithoutThrowing(@TempDir Path emptyDir) {
        Path missingModel = emptyDir.resolve("champion_model.onnx");
        Path missingMetadata = emptyDir.resolve("modelmetadata.json");
        Path missingScaler = emptyDir.resolve("scalerparams.json");

        assertThat(Files.exists(missingModel)).isFalse();

        try (AdaptivePredictionService service =
                     AdaptivePredictionService.initialize(missingModel, missingMetadata, missingScaler)) {

            assertThat(service.mode()).isEqualTo(InferenceMode.MODE_BASELINE_ACTIVE);

            PredictionResult result = service.evaluate(sampleRecord());

            assertThat(result.mode()).isEqualTo(InferenceMode.MODE_BASELINE_ACTIVE);
            assertThat(result.predictedLabel()).isBetween(-1, 1);
            assertThat(result.bullishScore()).isBetween(-1.0, 1.0);
            assertThat(result.probabilityDown()).isNull();
            assertThat(result.probabilityNeutral()).isNull();
            assertThat(result.probabilityUp()).isNull();
        }
    }

    @Test
    void activatesAiModeWhenValidArtifactsArePresent() {
        assumeTrue(
                Files.isRegularFile(REAL_MODEL_PATH)
                        && Files.isRegularFile(REAL_METADATA_PATH)
                        && Files.isRegularFile(REAL_SCALER_PATH),
                "M4P3 generated artifacts absent; generate them before runtime verification"
        );

        try (AdaptivePredictionService service = AdaptivePredictionService.initialize(
                REAL_MODEL_PATH, REAL_METADATA_PATH, REAL_SCALER_PATH)) {

            assertThat(service.mode()).isEqualTo(InferenceMode.MODE_AI_PREDICTIVE_ACTIVE);

            PredictionResult result = service.evaluate(sampleRecord());

            assertThat(result.mode()).isEqualTo(InferenceMode.MODE_AI_PREDICTIVE_ACTIVE);
            assertThat(result.probabilityDown()).isNotNull();
            assertThat(result.probabilityNeutral()).isNotNull();
            assertThat(result.probabilityUp()).isNotNull();
            assertThat(
                    result.probabilityDown() + result.probabilityNeutral() + result.probabilityUp()
            ).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(1e-4f));
        }
    }

    @Test
    void corruptedOnnxFileFallsBackToBaselineInsteadOfCrashing(@TempDir Path tempDir) throws Exception {
        Path corruptModel = tempDir.resolve("champion_model.onnx");
        Files.writeString(corruptModel, "not a real onnx file");
        Path missingMetadata = tempDir.resolve("modelmetadata.json");
        Path missingScaler = tempDir.resolve("scalerparams.json");

        try (AdaptivePredictionService service =
                     AdaptivePredictionService.initialize(corruptModel, missingMetadata, missingScaler)) {

            assertThat(service.mode()).isEqualTo(InferenceMode.MODE_BASELINE_ACTIVE);
            assertThat(service.evaluate(sampleRecord()).predictedLabel()).isBetween(-1, 1);
        }
    }
}
