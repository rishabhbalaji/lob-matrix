package com.lobmatrix.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Persisted Indian market statutory fee and brokerage schedule.
 */
@Entity
@Table(name = "cost_schedules")
public class CostScheduleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_name", nullable = false, unique = true)
    private String scheduleName;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "flat_brokerage_inr", nullable = false)
    private double flatBrokerageInr;

    @Column(name = "brokerage_rate", nullable = false)
    private double brokerageRate;

    @Column(name = "stt_rate_sell", nullable = false)
    private double sttRateSell;

    @Column(name = "exchange_turnover_rate", nullable = false)
    private double exchangeTurnoverRate;

    @Column(name = "sebi_turnover_rate", nullable = false)
    private double sebiTurnoverRate;

    @Column(name = "gst_rate", nullable = false)
    private double gstRate;

    @Column(name = "stamp_duty_rate_buy", nullable = false)
    private double stampDutyRateBuy;

    public CostScheduleEntity() {}

    public CostScheduleEntity(String scheduleName, LocalDate effectiveDate, double flatBrokerageInr, 
                              double brokerageRate, double sttRateSell, double exchangeTurnoverRate, 
                              double sebiTurnoverRate, double gstRate, double stampDutyRateBuy) {
        this.scheduleName = scheduleName;
        this.effectiveDate = effectiveDate;
        this.flatBrokerageInr = flatBrokerageInr;
        this.brokerageRate = brokerageRate;
        this.sttRateSell = sttRateSell;
        this.exchangeTurnoverRate = exchangeTurnoverRate;
        this.sebiTurnoverRate = sebiTurnoverRate;
        this.gstRate = gstRate;
        this.stampDutyRateBuy = stampDutyRateBuy;
    }

    public Long getId() { return id; }
    public String getScheduleName() { return scheduleName; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public double getFlatBrokerageInr() { return flatBrokerageInr; }
    public double getBrokerageRate() { return brokerageRate; }
    public double getSttRateSell() { return sttRateSell; }
    public double getExchangeTurnoverRate() { return exchangeTurnoverRate; }
    public double getSebiTurnoverRate() { return sebiTurnoverRate; }
    public double getGstRate() { return gstRate; }
    public double getStampDutyRateBuy() { return stampDutyRateBuy; }
}
