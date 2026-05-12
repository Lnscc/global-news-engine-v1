package com.example.globalnewsenginev1.gdelt.repository;

import com.example.globalnewsenginev1.gdelt.model.GdeltEvent;
import com.example.globalnewsenginev1.ingestion.SourceBatch;
import com.example.globalnewsenginev1.ingestion.StagingRow;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GdeltEventRepository extends JpaRepository<GdeltEvent, Long> {

    Optional<GdeltEvent> findByGlobalEventId(long globalEventId);

    boolean existsByStagingRow(StagingRow stagingRow);

    long countBySourceBatch(SourceBatch sourceBatch);

    @Query("""
            select row
            from StagingRow row
            where row.fileType = :fileType
              and row.normalizedAt is null
              and row.normalizationSkippedAt is null
            order by row.id
            """)
    List<StagingRow> findUnnormalizedRows(@Param("fileType") String fileType);
}
