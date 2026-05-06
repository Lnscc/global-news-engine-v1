package com.example.globalnewsenginev1.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SourceBatchRepository extends JpaRepository<SourceBatch, Long> {

    Optional<SourceBatch> findBySourceAndExternalBatchId(String source, String externalBatchId);

    List<SourceBatch> findTop10BySourceAndStatusOrderByExternalBatchIdDesc(String source, IngestionStatus status);
}
