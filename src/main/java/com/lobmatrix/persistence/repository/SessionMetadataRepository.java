package com.lobmatrix.persistence.repository;

import com.lobmatrix.persistence.entity.SessionMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface SessionMetadataRepository extends JpaRepository<SessionMetadataEntity, Long> {
    Optional<SessionMetadataEntity> findByTradeDate(LocalDate tradeDate);
}
