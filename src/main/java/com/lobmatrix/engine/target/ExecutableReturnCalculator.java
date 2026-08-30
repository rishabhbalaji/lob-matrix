package com.lobmatrix.engine.target;

import com.lobmatrix.core.model.CanonicalMarketSnapshot;

/**
 * Calculates Spread-Crossed Executable Returns deducting versioned regulatory transaction costs.
 */
public class ExecutableReturnCalculator {

    /**
     * Calculates Net Executable Return for an aggressive Long trade (Buy at Ask1, Sell at forward Bid1):
     * Gross = (ForwardBid1 - EntryAsk1) / EntryAsk1
     * Net = Gross - EffectiveRegulatoryFeeRate
     */
    public static double calculateLongExecutableReturn(CanonicalMarketSnapshot entrySnapshot, 
                                                        double forwardBestBid, 
                                                        CostSchedule costSchedule, 
                                                        double tradeValueInr) {
        if (entrySnapshot == null || forwardBestBid <= 0.0 || costSchedule == null) {
            return Double.NaN;
        }

        double[] askPrices = entrySnapshot.askPrices();
        if (askPrices.length == 0 || askPrices[0] <= 0.0) {
            return Double.NaN;
        }

        double entryAsk = askPrices[0];
        double grossReturn = (forwardBestBid - entryAsk) / entryAsk;
        double costRate = costSchedule.calculateEffectiveRoundTripCostRate(tradeValueInr);

        return grossReturn - costRate;
    }

    /**
     * Calculates Net Executable Return for an aggressive Short trade (Sell at Bid1, Buy back at forward Ask1):
     * Gross = (EntryBid1 - ForwardAsk1) / EntryBid1
     * Net = Gross - EffectiveRegulatoryFeeRate
     */
    public static double calculateShortExecutableReturn(CanonicalMarketSnapshot entrySnapshot, 
                                                         double forwardBestAsk, 
                                                         CostSchedule costSchedule, 
                                                         double tradeValueInr) {
        if (entrySnapshot == null || forwardBestAsk <= 0.0 || costSchedule == null) {
            return Double.NaN;
        }

        double[] bidPrices = entrySnapshot.bidPrices();
        if (bidPrices.length == 0 || bidPrices[0] <= 0.0) {
            return Double.NaN;
        }

        double entryBid = bidPrices[0];
        double grossReturn = (entryBid - forwardBestAsk) / entryBid;
        double costRate = costSchedule.calculateEffectiveRoundTripCostRate(tradeValueInr);

        return grossReturn - costRate;
    }
}
