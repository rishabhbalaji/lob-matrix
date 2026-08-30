package com.lobmatrix.persistence.repository;

import com.lobmatrix.persistence.entity.ExperimentRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExperimentRunRepository extends JpaRepository<ExperimentRunEntity, Long> {
    List<ExperimentRunEntity> findByExperimentId(String experimentId);
    List<ExperimentRunEntity> findByModelType(String modelType);
}
