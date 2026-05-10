package com.example.globalnewsenginev1.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StagingRowRepository extends JpaRepository<StagingRow, Long> {

    boolean existsByRawSourceFileAndLineNumber(RawSourceFile rawSourceFile, long lineNumber);

    long countBySourceBatch(SourceBatch sourceBatch);

    long countBySourceBatchAndFileType(SourceBatch sourceBatch, String fileType);

    @Query("""
            select count(row)
            from StagingRow row
            where row.sourceBatch = :sourceBatch
              and row.fileType = :fileType
              and row.normalizedAt is null
              and row.normalizationSkippedAt is null
            """)
    long countUnhandledBySourceBatchAndFileType(
            @Param("sourceBatch") SourceBatch sourceBatch,
            @Param("fileType") String fileType
    );
}
