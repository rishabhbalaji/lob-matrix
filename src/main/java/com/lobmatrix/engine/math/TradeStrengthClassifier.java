package com.lobmatrix.engine.math;

import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classifies aggressor trade direction using the Lee-Ready rule and calculates
 * rolling Trade Strength and Buy/Sell Pressure metrics.
 */
public class TradeStrengthClassifier {

    private double prevLtp = 0.0;
    private AggressorSide lastSide = AggressorSide.UNKNOWN;
    private double cumulativeBuyVolume = 0.0;
    private double cumulativeSellVolume = 0.0;
    private long lastTradeCount = 0L;

    /**
     * Classifies a trade tick using the Lee-Ready algorithm.
     *
     * @param ltp Current Last Traded Price
     * @param bestBid Current Best Bid price
     * @param bestAsk Current Best Ask price
     * @return AggressorSide (BUY, SELL, UNKNOWN)
     */
    public AggressorSide classifyTrade(double ltp, double bestBid, double bestAsk) {
        if (ltp <= 0.0) {
            return AggressorSide.UNKNOWN;
        }

        AggressorSide side;

        // 1. Quote Rule
        if (bestAsk > 0.0 && ltp >= bestAsk) {
            side = AggressorSide.BUY;
        } else if (bestBid > 0.0 && ltp <= bestBid) {
            side = AggressorSide.SELL;
        }
        // 2. Tick Rule (Inside spread: B1 < ltp < A1)
        else {
            if (prevLtp > 0.0) {
                if (ltp > prevLtp) {
                    side = AggressorSide.BUY; // Uptick
                } else if (ltp < prevLtp) {
                    side = AggressorSide.SELL; // Downtick
                } else {
                    side = lastSide; // Zero-tick: carry forward last known side
                }
            } else {
                side = AggressorSide.UNKNOWN;
            }
        }

        this.prevLtp = ltp;
        this.lastSide = side;
        return side;
    }

    /**
     * Updates rolling trade volume attribution from an incoming snapshot.
     */
    public void recordTrade(CanonicalMarketSnapshot snapshot, long tradeVolume) {
        if (snapshot == null || tradeVolume <= 0) return;

        double bestBid = snapshot.bidPrices().length > 0 ? snapshot.bidPrices()[0] : 0.0;
        double bestAsk = snapshot.askPrices().length > 0 ? snapshot.askPrices()[0] : 0.0;

        AggressorSide side = classifyTrade(snapshot.ltp(), bestBid, bestAsk);

        if (side == AggressorSide.BUY) {
            cumulativeBuyVolume += tradeVolume;
        } else if (side == AggressorSide.SELL) {
            cumulativeSellVolume += tradeVolume;
        }
        lastTradeCount++;
    }

    /**
     * Calculates Trade Strength: (BuyVol - SellVol) / (BuyVol + SellVol) in range [-1.0, +1.0].
     */
    public double calculateTradeStrength() {
        double denominator = cumulativeBuyVolume + cumulativeSellVolume;
        if (denominator <= 0.0) {
            return 0.0;
        }
        return (cumulativeBuyVolume - cumulativeSellVolume) / denominator;
    }

    /**
     * Calculates Buy Pressure: BuyVol / (BuyVol + SellVol) in range [0.0, 1.0].
     */
    public double calculateBuyPressure() {
        double denominator = cumulativeBuyVolume + cumulativeSellVolume;
        if (denominator <= 0.0) {
            return 0.5; // 50% neutral
        }
        return cumulativeBuyVolume / denominator;
    }

    /**
     * Calculates Sell Pressure: SellVol / (BuyVol + SellVol) in range [0.0, 1.0].
     */
    public double calculateSellPressure() {
        double denominator = cumulativeBuyVolume + cumulativeSellVolume;
        if (denominator <= 0.0) {
            return 0.5; // 50% neutral
        }
        return cumulativeSellVolume / denominator;
    }

    public double getCumulativeBuyVolume() { return cumulativeBuyVolume; }
    public double getCumulativeSellVolume() { return cumulativeSellVolume; }
    public long getLastTradeCount() { return lastTradeCount; }
    public AggressorSide getLastSide() { return lastSide; }

    public void reset() {
        this.prevLtp = 0.0;
        this.lastSide = AggressorSide.UNKNOWN;
        this.cumulativeBuyVolume = 0.0;
        this.cumulativeSellVolume = 0.0;
        this.lastTradeCount = 0L;
    }
}
