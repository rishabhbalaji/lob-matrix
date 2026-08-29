package com.lobmatrix.engine.state;

import com.lobmatrix.core.model.SessionPhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.util.Objects;

/**
 * Deterministic state machine governing Indian Equity (NSE) market sessions in Asia/Kolkata timezone.
 */
public class SessionPhaseStateMachine {

    private static final Logger log = LoggerFactory.getLogger(SessionPhaseStateMachine.class);
    public static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    // NSE Official Session Boundaries
    public static final LocalTime PRE_OPEN_START = LocalTime.of(9, 0, 0);
    public static final LocalTime PRE_OPEN_MATCH = LocalTime.of(9, 8, 0);
    public static final LocalTime CONTINUOUS_START = LocalTime.of(9, 15, 0);
    public static final LocalTime CONTINUOUS_END = LocalTime.of(15, 30, 0);
    public static final LocalTime POST_CLOSE_END = LocalTime.of(16, 0, 0);
    public static final LocalTime PARQUET_CONVERT_TIME = LocalTime.of(15, 45, 0);

    private final Clock clock;
    private SessionPhase currentPhase = SessionPhase.CLOSED;

    public SessionPhaseStateMachine(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.currentPhase = evaluatePhase(Instant.now(clock));
    }

    public SessionPhaseStateMachine() {
        this(Clock.system(IST_ZONE));
    }

    /**
     * Evaluates the NSE session phase for an explicit Instant in time.
     */
    public static SessionPhase evaluatePhase(Instant instant) {
        ZonedDateTime istTime = instant.atZone(IST_ZONE);
        DayOfWeek day = istTime.getDayOfWeek();

        // Weekend check (Saturday / Sunday)
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return SessionPhase.CLOSED;
        }

        LocalTime time = istTime.toLocalTime();

        if (time.isBefore(PRE_OPEN_START)) {
            return SessionPhase.CLOSED;
        } else if (time.isBefore(PRE_OPEN_MATCH)) {
            return SessionPhase.PRE_OPEN_ORDER_ENTRY;
        } else if (time.isBefore(CONTINUOUS_START)) {
            return SessionPhase.PRE_OPEN_MATCHING;
        } else if (time.isBefore(CONTINUOUS_END)) {
            return SessionPhase.CONTINUOUS_TRADING;
        } else if (time.isBefore(POST_CLOSE_END)) {
            return SessionPhase.POST_CLOSE;
        } else {
            return SessionPhase.CLOSED;
        }
    }

    /**
     * Evaluates phase from epoch milliseconds.
     */
    public static SessionPhase evaluatePhase(long epochMillis) {
        return evaluatePhase(Instant.ofEpochMilli(epochMillis));
    }

    /**
     * Returns true if continuous regular trading is currently active.
     */
    public boolean isContinuousTradingActive() {
        return updateAndGetPhase() == SessionPhase.CONTINUOUS_TRADING;
    }

    /**
     * Returns true if a given timestamp falls in continuous trading.
     */
    public static boolean isContinuousTrading(Instant instant) {
        return evaluatePhase(instant) == SessionPhase.CONTINUOUS_TRADING;
    }

    /**
     * Polls the current clock, updates phase state, and detects transitions.
     */
    public SessionPhase updateAndGetPhase() {
        SessionPhase newPhase = evaluatePhase(Instant.now(clock));
        if (newPhase != this.currentPhase) {
            log.info("NSE Session Phase Transition: {} -> {}", this.currentPhase, newPhase);
            this.currentPhase = newPhase;
        }
        return this.currentPhase;
    }

    public SessionPhase getCurrentPhase() {
        return currentPhase;
    }
}
