package com.example.globalnewsenginev1.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StagingRowRepository extends JpaRepository<StagingRow, Long> {

    boolean existsByRawSourceFileAndLineNumber(RawSourceFile rawSourceFile, long lineNumber);

    long countBySourceBatch(SourceBatch sourceBatch);

    long countBySourceBatchAndFileType(SourceBatch sourceBatch, String fileType);
}
