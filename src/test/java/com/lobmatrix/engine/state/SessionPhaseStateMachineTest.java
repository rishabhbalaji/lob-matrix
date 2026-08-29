package com.lobmatrix.engine.state;

import com.lobmatrix.core.model.SessionPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

class SessionPhaseStateMachineTest {

    private Instant makeIstInstant(int year, int month, int day, int hour, int minute, int second) {
        LocalDateTime ldt = LocalDateTime.of(year, month, day, hour, minute, second);
        return ldt.atZone(SessionPhaseStateMachine.IST_ZONE).toInstant();
    }

    @Test
    @DisplayName("Verify NSE trading phases throughout a standard weekday (Wednesday)")
    void testWeekdayTradingPhases() {
        // Wednesday: 2026-08-26
        int y = 2026, m = 8, d = 26;

        // 08:30 IST -> CLOSED
        assertThat(SessionPhaseStateMachine.evaluatePhase(makeIstInstant(y, m, d, 8, 30, 0)))
                .isEqualTo(SessionPhase.CLOSED);

        // 09:05 IST -> PRE_OPEN_ORDER_ENTRY
        assertThat(SessionPhaseStateMachine.evaluatePhase(makeIstInstant(y, m, d, 9, 5, 0)))
                .isEqualTo(SessionPhase.PRE_OPEN_ORDER_ENTRY);

        // 09:10 IST -> PRE_OPEN_MATCHING
        assertThat(SessionPhaseStateMachine.evaluatePhase(makeIstInstant(y, m, d, 9, 10, 0)))
                .isEqualTo(SessionPhase.PRE_OPEN_MATCHING);

        // 09:15:00 IST -> CONTINUOUS_TRADING
        Instant openInstant = makeIstInstant(y, m, d, 9, 15, 0);
        assertThat(SessionPhaseStateMachine.evaluatePhase(openInstant))
                .isEqualTo(SessionPhase.CONTINUOUS_TRADING);
        assertThat(SessionPhaseStateMachine.isContinuousTrading(openInstant)).isTrue();

        // 14:45:00 IST -> CONTINUOUS_TRADING
        assertThat(SessionPhaseStateMachine.evaluatePhase(makeIstInstant(y, m, d, 14, 45, 0)))
                .isEqualTo(SessionPhase.CONTINUOUS_TRADING);

        // 15:30:00 IST -> POST_CLOSE
        assertThat(SessionPhaseStateMachine.evaluatePhase(makeIstInstant(y, m, d, 15, 30, 0)))
                .isEqualTo(SessionPhase.POST_CLOSE);

        // 15:45:00 IST -> POST_CLOSE
        assertThat(SessionPhaseStateMachine.evaluatePhase(makeIstInstant(y, m, d, 15, 45, 0)))
                .isEqualTo(SessionPhase.POST_CLOSE);

        // 16:05:00 IST -> CLOSED
        assertThat(SessionPhaseStateMachine.evaluatePhase(makeIstInstant(y, m, d, 16, 5, 0)))
                .isEqualTo(SessionPhase.CLOSED);
    }

    @Test
    @DisplayName("Verify Weekend trading timestamps evaluate to CLOSED")
    void testWeekendDetection() {
        // Saturday: 2026-08-29 at 11:00 AM IST
        Instant saturday = makeIstInstant(2026, 8, 29, 11, 0, 0);
        assertThat(SessionPhaseStateMachine.evaluatePhase(saturday)).isEqualTo(SessionPhase.CLOSED);
        assertThat(SessionPhaseStateMachine.isContinuousTrading(saturday)).isFalse();

        // Sunday: 2026-08-30 at 14:00 PM IST
        Instant sunday = makeIstInstant(2026, 8, 30, 14, 0, 0);
        assertThat(SessionPhaseStateMachine.evaluatePhase(sunday)).isEqualTo(SessionPhase.CLOSED);
    }
}
