package com.lobmatrix.persistence;

import com.lobmatrix.persistence.entity.CostScheduleEntity;
import com.lobmatrix.persistence.entity.ExperimentRunEntity;
import com.lobmatrix.persistence.entity.SessionMetadataEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EntityMappingTest {

    @Test
    @DisplayName("Verify SessionMetadata, CostSchedule, and ExperimentRun entity instantiations")
    void testEntityMapping() {
        LocalDate today = LocalDate.of(2026, 8, 29);
        LocalDateTime now = LocalDateTime.now();

        SessionMetadataEntity session = new SessionMetadataEntity(
                today, "ZERODHA", now, now.plusHours(6), 1_500_000L, 100_000L, 0L, "FINALIZED"
        );
        assertThat(session.getTradeDate()).isEqualTo(today);
        assertThat(session.getTotalRawFrames()).isEqualTo(1_500_000L);
        assertThat(session.getStatus()).isEqualTo("FINALIZED");

        CostScheduleEntity schedule = new CostScheduleEntity(
                "NSE_OCT_2024", today, 20.0, 0.0003, 0.00025, 0.0000297, 0.000001, 0.18, 0.00003
        );
        assertThat(schedule.getScheduleName()).isEqualTo("NSE_OCT_2024");
        assertThat(schedule.getSttRateSell()).isEqualTo(0.00025);

        ExperimentRunEntity exp = new ExperimentRunEntity(
                "EXP_2D_SURFACE_001", now, 1000L, 5, "LIGHTGBM", 0.085, 1.42, 0.584, "{\"n_estimators\": 100}"
        );
        assertThat(exp.getModelType()).isEqualTo("LIGHTGBM");
        assertThat(exp.getRankIC()).isEqualTo(0.085);
    }
}
