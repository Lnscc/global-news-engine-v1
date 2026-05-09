package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.ingestion.StagingRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GdeltMentionRepository extends JpaRepository<GdeltMention, Long> {

    boolean existsByStagingRow(StagingRow stagingRow);

    @Query("""
            select row
            from StagingRow row
            where row.fileType = :fileType
              and not exists (
                  select mention.id
                  from GdeltMention mention
                  where mention.stagingRow = row
              )
            order by row.id
            """)
    List<StagingRow> findUnnormalizedRows(@Param("fileType") String fileType, Pageable pageable);
}
