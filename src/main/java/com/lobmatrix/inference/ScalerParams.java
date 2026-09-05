package com.lobmatrix.inference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * M5P1S2 fail-closed loader for the M4P3S2 standard-score scaler contract.
 *
 * <p>The scaler file's SHA-256 must match the value recorded in
 * modelmetadata.json before any statistic is trusted, preventing a stale or
 * hand-edited scaler from silently drifting from the model it was fit for.
 */
public final class ScalerParams {

    public static final String DEFAULT_SCALER_PATH = "data/models/scalerparams.json";
    public static final String DEFAULT_METADATA_PATH = "data/models/modelmetadata.json";

    private final List<String> featureOrder;
    private final double[] mean;
    private final double[] std;

    private ScalerParams(List<String> featureOrder, double[] mean, double[] std) {
        this.featureOrder = List.copyOf(featureOrder);
        this.mean = mean.clone();
        this.std = std.clone();
    }

    public static ScalerParams loadDefault() {
        return load(Path.of(DEFAULT_SCALER_PATH), Path.of(DEFAULT_METADATA_PATH));
    }

    public static ScalerParams load(Path scalerPath, Path metadataPath) {
        Objects.requireNonNull(scalerPath, "scalerPath");
        Objects.requireNonNull(metadataPath, "metadataPath");

        if (!Files.isRegularFile(scalerPath)) {
            throw new IllegalStateException(
                    "Scaler artifact is missing: " + scalerPath
                            + ". Generate it with scripts/generate_model_metadata.py."
            );
        }
        if (!Files.isRegularFile(metadataPath)) {
            throw new IllegalStateException(
                    "Model metadata is missing: " + metadataPath
                            + ". Generate it with scripts/generate_model_metadata.py."
            );
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode metadata = mapper.readTree(Files.readString(metadataPath));
            JsonNode featureContract = required(metadata, "feature_contract");
            String expectedSha256 = requiredText(featureContract, "scaler_sha256");
            String actualSha256 = sha256(scalerPath);
            if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
                throw new IllegalStateException(
                        "Scaler SHA-256 mismatch: metadata=" + expectedSha256
                                + ", actual=" + actualSha256
                );
            }

            JsonNode scaler = mapper.readTree(Files.readString(scalerPath));
            List<String> featureOrder = stringList(required(scaler, "feature_order"));
            int nFeatures = required(scaler, "n_features").asInt(-1);
            if (featureOrder.size() != nFeatures) {
                throw new IllegalStateException(
                        "Scaler feature_order length " + featureOrder.size()
                                + " does not equal declared n_features " + nFeatures
                );
            }

            List<String> metadataFeatureOrder = stringList(required(featureContract, "feature_order"));
            if (!featureOrder.equals(metadataFeatureOrder)) {
                throw new IllegalStateException(
                        "Scaler feature order " + featureOrder
                                + " does not match model metadata feature order "
                                + metadataFeatureOrder
                );
            }

            JsonNode statsNode = required(scaler, "feature_statistics");
            if (!statsNode.isArray() || statsNode.size() != nFeatures) {
                throw new IllegalStateException(
                        "feature_statistics must be an array of length " + nFeatures
                );
            }

            double[] mean = new double[nFeatures];
            double[] std = new double[nFeatures];
            boolean[] seen = new boolean[nFeatures];

            for (JsonNode stat : statsNode) {
                int index = required(stat, "index").asInt(-1);
                if (index < 0 || index >= nFeatures) {
                    throw new IllegalStateException("feature_statistics has out-of-range index: " + index);
                }
                String name = requiredText(stat, "name");
                if (!name.equals(featureOrder.get(index))) {
                    throw new IllegalStateException(
                            "feature_statistics[" + index + "].name=" + name
                                    + " does not match feature_order[" + index + "]="
                                    + featureOrder.get(index)
                    );
                }
                double meanValue = required(stat, "mean").asDouble(Double.NaN);
                double stdValue = required(stat, "std").asDouble(Double.NaN);
                if (!Double.isFinite(meanValue) || !Double.isFinite(stdValue) || stdValue <= 0.0) {
                    throw new IllegalStateException(
                            "feature_statistics[" + index + "] has invalid mean/std: mean="
                                    + meanValue + ", std=" + stdValue
                    );
                }
                mean[index] = meanValue;
                std[index] = stdValue;
                seen[index] = true;
            }

            for (int index = 0; index < nFeatures; index++) {
                if (!seen[index]) {
                    throw new IllegalStateException("feature_statistics is missing index " + index);
                }
            }

            return new ScalerParams(featureOrder, mean, std);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load scaler contract from " + scalerPath, exception);
        }
    }

    /** Applies z = (x - mean) / std, in the declared feature order. */
    public double[] transform(double[] rawFeatures) {
        if (rawFeatures.length != mean.length) {
            throw new IllegalArgumentException(
                    "Expected " + mean.length + " raw features; got " + rawFeatures.length
            );
        }
        double[] scaled = new double[mean.length];
        for (int i = 0; i < mean.length; i++) {
            double raw = rawFeatures[i];
            if (!Double.isFinite(raw)) {
                throw new IllegalArgumentException(
                        "Raw feature '" + featureOrder.get(i) + "' at index " + i
                                + " is non-finite: " + raw
                );
            }
            double z = (raw - mean[i]) / std[i];
            if (!Double.isFinite(z)) {
                throw new IllegalStateException(
                        "Scaled feature '" + featureOrder.get(i) + "' at index " + i
                                + " became non-finite"
                );
            }
            scaled[i] = z;
        }
        return scaled;
    }

    public List<String> featureOrder() {
        return featureOrder;
    }

    private static JsonNode required(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new IllegalStateException("Missing required scaler/metadata field: " + field);
        }
        return value;
    }

    private static String requiredText(JsonNode parent, String field) {
        JsonNode value = required(parent, field);
        if (!value.isTextual()) {
            throw new IllegalStateException("Field must be text: " + field);
        }
        return value.asText();
    }

    private static List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            throw new IllegalStateException("Expected JSON array: " + node);
        }
        List<String> result = new ArrayList<>();
        node.forEach(item -> result.add(item.asText()));
        return List.copyOf(result);
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
