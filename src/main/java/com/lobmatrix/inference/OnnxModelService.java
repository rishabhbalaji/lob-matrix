package com.lobmatrix.inference;

import ai.onnxruntime.OnnxMap;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * M5P1S1 fail-closed ONNX Runtime loader for the M4P3 LightGBM classifier.
 *
 * <p>Expected local artifacts:
 * <ul>
 *   <li>data/models/champion_model.onnx</li>
 *   <li>data/models/modelmetadata.json</li>
 *   <li>data/models/scalerparams.json</li>
 * </ul>
 *
 * <p>The metadata is authoritative for the feature count, feature order,
 * model checksum, input name, label mapping, and ONNX-ML ZipMap probability
 * output. A caller must provide raw features in the metadata's exact order.
 * Scaling is intentionally handled by the next integration layer; this stage
 * verifies safe model loading and direct tensor inference.
 */
public final class OnnxModelService implements AutoCloseable {

    public static final int EXPECTED_FEATURE_COUNT = 12;
    public static final String DEFAULT_MODEL_PATH = "data/models/champion_model.onnx";
    public static final String DEFAULT_METADATA_PATH = "data/models/modelmetadata.json";

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String inputName;
    private final int featureCount;
    private final List<String> featureOrder;
    private final Map<Integer, Integer> reverseLabelMapping;

    private OnnxModelService(
            OrtEnvironment environment,
            OrtSession session,
            String inputName,
            int featureCount,
            List<String> featureOrder,
            Map<Integer, Integer> reverseLabelMapping
    ) {
        this.environment = environment;
        this.session = session;
        this.inputName = inputName;
        this.featureCount = featureCount;
        this.featureOrder = List.copyOf(featureOrder);
        this.reverseLabelMapping = Map.copyOf(reverseLabelMapping);
    }

    /**
     * Loads the default locally generated M4P3 ONNX artifact and metadata.
     *
     * @throws IllegalStateException if required artifacts are missing,
     *                               invalid, checksum-inconsistent, or
     *                               contract-incompatible.
     */
    public static OnnxModelService loadDefault() {
        return load(Path.of(DEFAULT_MODEL_PATH), Path.of(DEFAULT_METADATA_PATH));
    }

    /**
     * Loads an ONNX model only after verifying its SHA-256 and metadata
     * contract. This deliberately fails closed rather than deploying a model
     * with an unknown feature or label mapping.
     */
    public static OnnxModelService load(Path modelPath, Path metadataPath) {
        Objects.requireNonNull(modelPath, "modelPath");
        Objects.requireNonNull(metadataPath, "metadataPath");

        if (!Files.isRegularFile(modelPath)) {
            throw new IllegalStateException(
                    "ONNX model artifact is missing: " + modelPath
                            + ". Generate it with scripts/export_model_onnx.py."
            );
        }
        if (!Files.isRegularFile(metadataPath)) {
            throw new IllegalStateException(
                    "ONNX model metadata is missing: " + metadataPath
                            + ". Generate it with scripts/generate_model_metadata.py."
            );
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode metadata = mapper.readTree(Files.readString(metadataPath));

            JsonNode modelNode = required(metadata, "model");
            String expectedSha256 = requiredText(modelNode, "sha256");
            String actualSha256 = sha256(modelPath);
            if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
                throw new IllegalStateException(
                        "ONNX SHA-256 mismatch: metadata=" + expectedSha256
                                + ", actual=" + actualSha256
                );
            }

            JsonNode featureContract = required(metadata, "feature_contract");
            int featureCount = required(featureContract, "n_features").asInt(-1);
            if (featureCount != EXPECTED_FEATURE_COUNT) {
                throw new IllegalStateException(
                        "Unsupported feature count: expected " + EXPECTED_FEATURE_COUNT
                                + ", metadata declares " + featureCount
                );
            }

            List<String> featureOrder = stringList(required(featureContract, "feature_order"));
            if (featureOrder.size() != featureCount) {
                throw new IllegalStateException(
                        "Feature-order length " + featureOrder.size()
                                + " does not equal declared feature count " + featureCount
                );
            }

            JsonNode onnxContract = required(metadata, "onnx_contract");
            JsonNode inputNode = required(onnxContract, "input");
            String inputName = requiredText(inputNode, "name");
            String inputDtype = requiredText(inputNode, "dtype");
            if (!"float32".equals(inputDtype)) {
                throw new IllegalStateException(
                        "Unsupported ONNX input dtype: " + inputDtype + "; expected float32"
                );
            }

            List<String> declaredShape = stringList(required(inputNode, "shape"));
            if (!declaredShape.equals(List.of("N", Integer.toString(featureCount)))) {
                throw new IllegalStateException(
                        "Unexpected input contract: " + declaredShape
                                + "; expected [N, " + featureCount + "]"
                );
            }

            JsonNode outputsNode = required(onnxContract, "outputs");
            JsonNode probabilityNode = required(outputsNode, "probabilities");
            String probabilityType = requiredText(probabilityNode, "onnx_type");
            if (!"sequence<map<int64,float32>>".equals(probabilityType)) {
                throw new IllegalStateException(
                        "Unsupported probability output: " + probabilityType
                );
            }

            Map<Integer, Integer> reverseMapping = integerMap(
                    required(required(metadata, "model"), "reverse_label_mapping")
            );
            requireClassMapping(reverseMapping);

            OrtEnvironment environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            OrtSession session = environment.createSession(modelPath.toString(), options);

            if (!session.getInputNames().contains(inputName)) {
                session.close();
                throw new IllegalStateException(
                        "ONNX input '" + inputName + "' not found; graph inputs="
                                + session.getInputNames()
                );
            }

            return new OnnxModelService(
                    environment,
                    session,
                    inputName,
                    featureCount,
                    featureOrder,
                    reverseMapping
            );
        } catch (IOException | OrtException exception) {
            throw new IllegalStateException(
                    "Unable to load verified ONNX model from " + modelPath, exception
            );
        }
    }

    /**
     * Runs one prediction. Raw feature values must already be in metadata's
     * exact order. M5P1S2 will connect live feature assembly and scaling.
     */
    public Prediction predict(float[] features) {
        Objects.requireNonNull(features, "features");
        return predictBatch(new float[][]{features}).getFirst();
    }

    /**
     * Runs a batch prediction. Every row must have exactly 12 float features
     * in the declared model metadata order.
     */
    public List<Prediction> predictBatch(float[][] rows) {
        Objects.requireNonNull(rows, "rows");
        if (rows.length == 0) {
            return List.of();
        }

        FloatBuffer buffer = FloatBuffer.allocate(Math.multiplyExact(rows.length, featureCount));
        for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
            float[] row = Objects.requireNonNull(rows[rowIndex], "rows[" + rowIndex + "]");
            if (row.length != featureCount) {
                throw new IllegalArgumentException(
                        "Feature row " + rowIndex + " contains " + row.length
                                + " values; expected " + featureCount
                );
            }
            for (float value : row) {
                if (!Float.isFinite(value)) {
                    throw new IllegalArgumentException(
                            "Feature row " + rowIndex + " contains non-finite value: " + value
                    );
                }
                buffer.put(value);
            }
        }
        buffer.rewind();

        long[] shape = {rows.length, featureCount};
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, buffer, shape);
             OrtSession.Result result = session.run(Map.of(inputName, tensor))) {

            long[] labels = decodeLabels(result);
            List<Map<Long, Float>> probabilityMaps = decodeZipMap(result);

            if (labels.length != rows.length || probabilityMaps.size() != rows.length) {
                throw new IllegalStateException(
                        "ONNX output batch mismatch: labels=" + labels.length
                                + ", probabilityMaps=" + probabilityMaps.size()
                                + ", inputs=" + rows.length
                );
            }

            List<Prediction> predictions = new ArrayList<>(rows.length);
            for (int index = 0; index < rows.length; index++) {
                Map<Long, Float> probabilities = probabilityMaps.get(index);
                float down = probability(probabilities, 0L, index);
                float neutral = probability(probabilities, 1L, index);
                float up = probability(probabilities, 2L, index);
                int remappedLabel = Math.toIntExact(labels[index]);
                int originalLabel = reverseLabelMapping.getOrDefault(
                        remappedLabel,
                        Integer.MIN_VALUE
                );
                if (originalLabel == Integer.MIN_VALUE) {
                    throw new IllegalStateException(
                            "Unexpected remapped ONNX label: " + remappedLabel
                    );
                }

                predictions.add(
                        new Prediction(
                                originalLabel,
                                remappedLabel,
                                down,
                                neutral,
                                up,
                                up - down
                        )
                );
            }
            return List.copyOf(predictions);
        } catch (OrtException exception) {
            throw new IllegalStateException("ONNX Runtime inference failed", exception);
        }
    }

    public int featureCount() {
        return featureCount;
    }

    public List<String> featureOrder() {
        return featureOrder;
    }

    public String inputName() {
        return inputName;
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException exception) {
            throw new IllegalStateException("Unable to close ONNX session", exception);
        }
    }

    public record Prediction(
            int predictedOriginalLabel,
            int predictedRemappedLabel,
            float probabilityDown,
            float probabilityNeutral,
            float probabilityUp,
            float bullishScore
    ) {
        public Prediction {
            if (predictedOriginalLabel < -1 || predictedOriginalLabel > 1) {
                throw new IllegalArgumentException(
                        "predictedOriginalLabel must be one of -1, 0, 1"
                );
            }
            if (predictedRemappedLabel < 0 || predictedRemappedLabel > 2) {
                throw new IllegalArgumentException(
                        "predictedRemappedLabel must be one of 0, 1, 2"
                );
            }
            if (!Float.isFinite(probabilityDown)
                    || !Float.isFinite(probabilityNeutral)
                    || !Float.isFinite(probabilityUp)
                    || !Float.isFinite(bullishScore)) {
                throw new IllegalArgumentException("Prediction probabilities must be finite");
            }
        }
    }

    private static long[] decodeLabels(OrtSession.Result result) throws OrtException {
        Object labels = result.get("label")
                .orElseThrow(() -> new IllegalStateException("Missing ONNX output: label"))
                .getValue();

        if (labels instanceof long[] longLabels) {
            return longLabels;
        }
        if (labels instanceof Long[] boxedLabels) {
            long[] decoded = new long[boxedLabels.length];
            for (int i = 0; i < boxedLabels.length; i++) {
                decoded[i] = boxedLabels[i];
            }
            return decoded;
        }
        throw new IllegalStateException(
                "Unexpected ONNX label output type: " + labels.getClass().getName()
        );
    }

    private static List<Map<Long, Float>> decodeZipMap(OrtSession.Result result)
            throws OrtException {
        Object raw = result.get("probabilities")
                .orElseThrow(() -> new IllegalStateException("Missing ONNX output: probabilities"))
                .getValue();

        if (!(raw instanceof List<?> rows)) {
            throw new IllegalStateException(
                    "Expected ZipMap output List; got " + raw.getClass().getName()
            );
        }

        List<Map<Long, Float>> decoded = new ArrayList<>(rows.size());
        for (Object row : rows) {
            Map<?, ?> map;
            if (row instanceof OnnxMap onnxMap) {
                try {
                    map = onnxMap.getValue();
                } finally {
                    onnxMap.close();
                }
            } else if (row instanceof Map<?, ?> javaMap) {
                map = javaMap;
            } else {
                throw new IllegalStateException(
                        "Expected ZipMap row OnnxMap or Map; got "
                                + row.getClass().getName()
                );
            }

            Map<Long, Float> probabilities = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof Number key)
                        || !(entry.getValue() instanceof Number value)) {
                    throw new IllegalStateException(
                            "ZipMap requires numeric class keys and probability values"
                    );
                }
                probabilities.put(key.longValue(), value.floatValue());
            }
            decoded.add(Map.copyOf(probabilities));
        }
        return List.copyOf(decoded);
    }

    private static float probability(Map<Long, Float> probabilities, long classKey, int rowIndex) {
        Float value = probabilities.get(classKey);
        if (value == null || !Float.isFinite(value)) {
            throw new IllegalStateException(
                    "Missing/non-finite class " + classKey + " probability at row " + rowIndex
            );
        }
        return value;
    }

    private static JsonNode required(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new IllegalStateException("Missing required metadata field: " + field);
        }
        return value;
    }

    private static String requiredText(JsonNode parent, String field) {
        JsonNode value = required(parent, field);
        if (!value.isTextual()) {
            throw new IllegalStateException("Metadata field must be text: " + field);
        }
        return value.asText();
    }

    private static List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            throw new IllegalStateException("Metadata field must be an array: " + node);
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual()) {
                result.add(item.asText());
            } else if (item.isIntegralNumber()) {
                result.add(item.asText());
            } else {
                throw new IllegalStateException("Metadata array contains non-scalar value: " + item);
            }
        }
        return List.copyOf(result);
    }

    private static Map<Integer, Integer> integerMap(JsonNode node) {
        if (!node.isObject()) {
            throw new IllegalStateException("Metadata mapping must be an object");
        }
        Map<Integer, Integer> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            try {
                values.put(Integer.parseInt(entry.getKey()), entry.getValue().asInt());
            } catch (NumberFormatException exception) {
                throw new IllegalStateException(
                        "Metadata mapping has non-integer key: " + entry.getKey(), exception
                );
            }
        });
        return Map.copyOf(values);
    }

    private static void requireClassMapping(Map<Integer, Integer> mapping) {
        Map<Integer, Integer> expected = Map.of(0, -1, 1, 0, 2, 1);
        if (!mapping.equals(expected)) {
            throw new IllegalStateException(
                    "Unexpected remapped label mapping: " + mapping + "; expected " + expected
            );
        }
    }

    private static String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var stream = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = stream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest.digest()) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | IOException exception) {
            throw new IllegalStateException("Unable to calculate SHA-256 for " + path, exception);
        }
    }
}
