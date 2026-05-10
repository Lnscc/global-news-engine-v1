package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.ingestion.SourceBatch;
import com.example.globalnewsenginev1.ingestion.StagingRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GdeltGkgRepository extends JpaRepository<GdeltGkg, Long> {

    Optional<GdeltGkg> findByGkgRecordId(String gkgRecordId);

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
    List<StagingRow> findUnnormalizedRows(@Param("fileType") String fileType, Pageable pageable);

    @Query("""
            select gkg
            from GdeltGkg gkg
            where gkg.articleProjectedAt is null
            order by gkg.date desc, gkg.id desc
            """)
    List<GdeltGkg> findUnprojectedArticleRows(Pageable pageable);
}
