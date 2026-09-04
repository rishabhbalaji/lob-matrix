package com.lobmatrix.inference;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OnnxModelServiceTest {

    private static final Path MODEL_PATH = Path.of("data/models/champion_model.onnx");
    private static final Path METADATA_PATH = Path.of("data/models/modelmetadata.json");

    @Test
    void loadsVerifiedArtifactAndRunsSingleTensorInference() {
        assumeTrue(
                Files.isRegularFile(MODEL_PATH) && Files.isRegularFile(METADATA_PATH),
                "M4P3 generated artifacts absent; generate them before runtime verification"
        );

        float[] features = {
                0.10f, 0.10f, 0.10f, 0.10f,
                100.0f, 0.01f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 10.0f
        };

        try (OnnxModelService service = OnnxModelService.load(MODEL_PATH, METADATA_PATH)) {
            OnnxModelService.Prediction prediction = service.predict(features);

            assertThat(service.featureCount()).isEqualTo(12);
            assertThat(service.featureOrder()).hasSize(12);
            assertThat(prediction.predictedOriginalLabel()).isBetween(-1, 1);
            assertThat(prediction.predictedRemappedLabel()).isBetween(0, 2);
            assertThat(prediction.probabilityDown()).isBetween(0.0f, 1.0f);
            assertThat(prediction.probabilityNeutral()).isBetween(0.0f, 1.0f);
            assertThat(prediction.probabilityUp()).isBetween(0.0f, 1.0f);
            assertThat(
                    prediction.probabilityDown()
                            + prediction.probabilityNeutral()
                            + prediction.probabilityUp()
            ).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(1e-5f));
        }
    }

    @Test
    void rejectsFeatureVectorsWithWrongDimension() {
        assumeTrue(
                Files.isRegularFile(MODEL_PATH) && Files.isRegularFile(METADATA_PATH),
                "M4P3 generated artifacts absent; generate them before runtime verification"
        );

        try (OnnxModelService service = OnnxModelService.load(MODEL_PATH, METADATA_PATH)) {
            assertThatThrownBy(() -> service.predict(new float[11]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expected 12");
        }
    }
}
