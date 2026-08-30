package com.lobmatrix.engine.target;

import java.time.LocalDate;

/**
 * Immutable regulatory cost schedule modeling Indian equity market statutory fees.
 */
public record CostSchedule(
        String scheduleName,          // e.g. "NSE_OCT_2024_REVISED"
        LocalDate effectiveDate,      // Effective start date
        double flatBrokeragePerOrder, // Flat brokerage in INR (e.g. 20.0)
        double brokerageRate,         // Variable brokerage cap (e.g. 0.0003 = 0.03%)
        double sttRateSell,           // STT on sell side (0.00025 = 0.025% revised Oct 2024)
        double exchangeTurnoverRate,  // NSE transaction charge (0.0000297 = 0.00297%)
        double sebiTurnoverRate,      // SEBI fee (0.0000010 = 0.0001%)
        double gstRate,               // GST on (Brokerage + Exchange + SEBI) = 18% (0.18)
        double stampDutyRateBuy       // Stamp duty on buy side (0.00003 = 0.003%)
) {
    /**
     * Calculates total round-trip statutory transaction costs as an effective decimal percentage of trade value.
     *
     * @param tradeValueInr Total traded value in INR (e.g. 100,000 INR)
     * @return Effective percentage friction (e.g. 0.00042 = 4.2 bps)
     */
    public double calculateEffectiveRoundTripCostRate(double tradeValueInr) {
        if (tradeValueInr <= 0.0) {
            return 0.0;
        }

        // Buy + Sell legs
        double brokerage = Math.min(flatBrokeragePerOrder * 2.0, tradeValueInr * 2.0 * brokerageRate);
        double stt = tradeValueInr * sttRateSell; // STT only on sell leg in intraday equity
        double exchangeFee = tradeValueInr * 2.0 * exchangeTurnoverRate;
        double sebiFee = tradeValueInr * 2.0 * sebiTurnoverRate;
        double gst = (brokerage + exchangeFee + sebiFee) * gstRate;
        double stampDuty = tradeValueInr * stampDutyRateBuy; // Stamp duty only on buy leg

        double totalFeesInr = brokerage + stt + exchangeFee + sebiFee + gst + stampDuty;
        return totalFeesInr / tradeValueInr;
    }
}
