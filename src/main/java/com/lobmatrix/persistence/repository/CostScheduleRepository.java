package com.lobmatrix.persistence.repository;

import com.lobmatrix.persistence.entity.CostScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CostScheduleRepository extends JpaRepository<CostScheduleEntity, Long> {
    Optional<CostScheduleEntity> findByScheduleName(String scheduleName);
}
