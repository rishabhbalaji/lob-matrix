package com.lobmatrix.engine.target;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Versioned repository of Indian regulatory cost schedules.
 */
public class CostModelRepository {

    // Official Post-October 1, 2024 Revised Schedule (STT 0.025% on sell, NSE 0.00297%, GST 18%, Stamp Duty 0.003%)
    public static final CostSchedule NSE_EQUITY_OCT_2024 = new CostSchedule(
            "NSE_EQUITY_OCT_2024",
            LocalDate.of(2024, 10, 1),
            20.0,      // Flat 20 INR
            0.0003,    // 0.03% max brokerage
            0.00025,   // 0.025% STT sell
            0.0000297, // 0.00297% NSE Turnover
            0.0000010, // 0.00010% SEBI
            0.18,      // 18% GST
            0.00003    // 0.003% Stamp Duty buy
    );

    // Pre-October 2024 Schedule
    public static final CostSchedule NSE_EQUITY_LEGACY_2023 = new CostSchedule(
            "NSE_EQUITY_LEGACY_2023",
            LocalDate.of(2023, 1, 1),
            20.0,
            0.0003,
            0.00025,
            0.0000345, // Legacy NSE 0.00345%
            0.0000010,
            0.18,
            0.00003
    );

    private static final Map<String, CostSchedule> SCHEDULES = new ConcurrentHashMap<>();

    static {
        SCHEDULES.put(NSE_EQUITY_OCT_2024.scheduleName(), NSE_EQUITY_OCT_2024);
        SCHEDULES.put(NSE_EQUITY_LEGACY_2023.scheduleName(), NSE_EQUITY_LEGACY_2023);
    }

    public static CostSchedule getDefaultSchedule() {
        return NSE_EQUITY_OCT_2024;
    }

    public static CostSchedule getSchedule(LocalDate date) {
        if (date != null && date.isBefore(LocalDate.of(2024, 10, 1))) {
            return NSE_EQUITY_LEGACY_2023;
        }
        return NSE_EQUITY_OCT_2024;
    }
}
