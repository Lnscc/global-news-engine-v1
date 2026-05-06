package com.example.globalnewsenginev1.ingestion;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(
        name = "source_batch",
        uniqueConstraints = @UniqueConstraint(name = "uk_source_batch_source_external_id", columnNames = {
                "source",
                "external_batch_id"
        })
)
public class SourceBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "external_batch_id", nullable = false, length = 128)
    private String externalBatchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IngestionStatus status = IngestionStatus.DISCOVERED;

    @Column(nullable = false)
    private Instant discoveredAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<RawSourceFile> files = new ArrayList<>();

    protected SourceBatch() {
    }

    public SourceBatch(String source, String externalBatchId) {
        this.source = source;
        this.externalBatchId = externalBatchId;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        discoveredAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public String getExternalBatchId() {
        return externalBatchId;
    }

    public IngestionStatus getStatus() {
        return status;
    }

    public Instant getDiscoveredAt() {
        return discoveredAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<RawSourceFile> getFiles() {
        return List.copyOf(files);
    }

    public Optional<RawSourceFile> findFile(String fileType) {
        return files.stream()
                .filter(file -> file.getFileType().equals(fileType))
                .findFirst();
    }

    public void putFile(String fileType, String url, long sizeBytes, String fileHash) {
        RawSourceFile file = findFile(fileType)
                .orElseGet(() -> {
                    RawSourceFile newFile = new RawSourceFile(this, fileType);
                    files.add(newFile);
                    return newFile;
                });

        file.update(url, sizeBytes, fileHash);
        if (status == IngestionStatus.FAILED) {
            status = IngestionStatus.DISCOVERED;
        }
    }

    public void markDownloading() {
        status = IngestionStatus.DOWNLOADING;
    }

    public void markDownloaded() {
        status = IngestionStatus.DOWNLOADED;
    }

    public void markFailed() {
        status = IngestionStatus.FAILED;
    }
}
