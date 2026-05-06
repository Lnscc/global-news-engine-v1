package com.example.globalnewsenginev1.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "staging_row",
        uniqueConstraints = @UniqueConstraint(name = "uk_staging_row_file_line", columnNames = {
                "raw_source_file_id",
                "line_number"
        })
)
public class StagingRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_batch_id", nullable = false)
    private SourceBatch sourceBatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raw_source_file_id", nullable = false)
    private RawSourceFile rawSourceFile;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(nullable = false, length = 128)
    private String externalBatchId;

    @Column(nullable = false, length = 64)
    private String fileType;

    @Column(nullable = false)
    private long lineNumber;

    @Column(nullable = false, columnDefinition = "text")
    private String rawLine;

    @Column(nullable = false)
    private Instant parsedAt;

    protected StagingRow() {
    }

    public StagingRow(SourceBatch sourceBatch, RawSourceFile rawSourceFile, long lineNumber, String rawLine) {
        this.sourceBatch = sourceBatch;
        this.rawSourceFile = rawSourceFile;
        this.source = sourceBatch.getSource();
        this.externalBatchId = sourceBatch.getExternalBatchId();
        this.fileType = rawSourceFile.getFileType();
        this.lineNumber = lineNumber;
        this.rawLine = rawLine;
    }

    @PrePersist
    void prePersist() {
        parsedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public SourceBatch getSourceBatch() {
        return sourceBatch;
    }

    public RawSourceFile getRawSourceFile() {
        return rawSourceFile;
    }

    public String getSource() {
        return source;
    }

    public String getExternalBatchId() {
        return externalBatchId;
    }

    public String getFileType() {
        return fileType;
    }

    public long getLineNumber() {
        return lineNumber;
    }

    public String getRawLine() {
        return rawLine;
    }

    public Instant getParsedAt() {
        return parsedAt;
    }
}
