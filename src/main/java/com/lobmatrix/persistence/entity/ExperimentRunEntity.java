package com.lobmatrix.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persisted scientific evaluation metrics and 2D Information Surface results.
 */
@Entity
@Table(name = "experiment_runs")
public class ExperimentRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "experiment_id", nullable = false)
    private String experimentId;

    @Column(name = "run_timestamp", nullable = false)
    private LocalDateTime runTimestamp;

    @Column(name = "sampling_delta_t_ms", nullable = false)
    private long samplingDeltaTMs;

    @Column(name = "forward_horizon_tau_sec", nullable = false)
    private int forwardHorizonTauSec;

    @Column(name = "model_type", nullable = false)
    private String modelType; // "LIGHTGBM", "XGBOOST", "CATBOOST", "LOGISTIC"

    @Column(name = "rank_ic", nullable = false)
    private double rankIC;

    @Column(name = "information_ratio")
    private double informationRatio;

    @Column(name = "directional_accuracy")
    private double directionalAccuracy;

    @Lob
    @Column(name = "metrics_json")
    private String metricsJson;

    public ExperimentRunEntity() {}

    public ExperimentRunEntity(String experimentId, LocalDateTime runTimestamp, long samplingDeltaTMs, 
                               int forwardHorizonTauSec, String modelType, double rankIC, 
                               double informationRatio, double directionalAccuracy, String metricsJson) {
        this.experimentId = experimentId;
        this.runTimestamp = runTimestamp;
        this.samplingDeltaTMs = samplingDeltaTMs;
        this.forwardHorizonTauSec = forwardHorizonTauSec;
        this.modelType = modelType;
        this.rankIC = rankIC;
        this.informationRatio = informationRatio;
        this.directionalAccuracy = directionalAccuracy;
        this.metricsJson = metricsJson;
    }

    public Long getId() { return id; }
    public String getExperimentId() { return experimentId; }
    public LocalDateTime getRunTimestamp() { return runTimestamp; }
    public long getSamplingDeltaTMs() { return samplingDeltaTMs; }
    public int getForwardHorizonTauSec() { return forwardHorizonTauSec; }
    public String getModelType() { return modelType; }
    public double getRankIC() { return rankIC; }
    public double getInformationRatio() { return informationRatio; }
    public double getDirectionalAccuracy() { return directionalAccuracy; }
    public String getMetricsJson() { return metricsJson; }
}
