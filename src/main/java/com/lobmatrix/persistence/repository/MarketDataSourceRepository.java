package com.lobmatrix.persistence.repository;

import com.lobmatrix.persistence.entity.MarketDataSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MarketDataSourceRepository extends JpaRepository<MarketDataSourceEntity, Long> {
    Optional<MarketDataSourceEntity> findBySourceCode(String sourceCode);
}
