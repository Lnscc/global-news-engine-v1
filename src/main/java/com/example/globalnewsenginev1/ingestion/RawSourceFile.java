package com.example.globalnewsenginev1.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "raw_source_file",
        uniqueConstraints = @UniqueConstraint(name = "uk_raw_source_file_batch_type", columnNames = {
                "batch_id",
                "file_type"
        })
)
public class RawSourceFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private SourceBatch batch;

    @Column(name = "file_type", nullable = false, length = 64)
    private String fileType;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(length = 128)
    private String fileHash;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private IngestionStatus status = IngestionStatus.DISCOVERED;

    @Column(length = 1000)
    private String localPath;

    private Instant downloadedAt;

    @Column(length = 2000)
    private String errorMessage;

    protected RawSourceFile() {
    }

    RawSourceFile(SourceBatch batch, String fileType) {
        this.batch = batch;
        this.fileType = fileType;
    }

    public Long getId() {
        return id;
    }

    public SourceBatch getBatch() {
        return batch;
    }

    public String getFileType() {
        return fileType;
    }

    public String getUrl() {
        return url;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getFileHash() {
        return fileHash;
    }

    public IngestionStatus getStatus() {
        return status == null ? IngestionStatus.DISCOVERED : status;
    }

    public String getLocalPath() {
        return localPath;
    }

    public Instant getDownloadedAt() {
        return downloadedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    void update(String url, long sizeBytes, String fileHash) {
        this.url = url;
        this.sizeBytes = sizeBytes;
        this.fileHash = fileHash;
        if (status == IngestionStatus.FAILED) {
            status = IngestionStatus.DISCOVERED;
            errorMessage = null;
        }
    }

    public void markDownloading() {
        status = IngestionStatus.DOWNLOADING;
        errorMessage = null;
    }

    public void markDownloaded(String localPath) {
        this.localPath = localPath;
        downloadedAt = Instant.now();
        status = IngestionStatus.DOWNLOADED;
        errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        status = IngestionStatus.FAILED;
        this.errorMessage = errorMessage;
    }
}
