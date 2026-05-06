package com.example.globalnewsenginev1.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SourceBatchRepository extends JpaRepository<SourceBatch, Long> {

    Optional<SourceBatch> findBySourceAndExternalBatchId(String source, String externalBatchId);
}
