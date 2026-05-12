package com.example.globalnewsenginev1.gdelt.model;

import com.example.globalnewsenginev1.gdelt.parser.GdeltMentionRecord;
import com.example.globalnewsenginev1.ingestion.SourceBatch;
import com.example.globalnewsenginev1.ingestion.StagingRow;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "gdelt_mention",
        uniqueConstraints = @UniqueConstraint(name = "uk_gdelt_mention_staging_row", columnNames = "staging_row_id")
)
public class GdeltMention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staging_row_id", nullable = false)
    private StagingRow stagingRow;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_batch_id", nullable = false)
    private SourceBatch sourceBatch;

    @Column(nullable = false)
    private long globalEventId;

    @Column(nullable = false)
    private LocalDateTime eventTimeDate;

    @Column(nullable = false)
    private LocalDateTime mentionTimeDate;

    @Column(nullable = false)
    private int mentionType;

    @Column(length = 512)
    private String mentionSourceName;

    @Column(length = 2000)
    private String mentionIdentifier;

    private Integer sentenceId;

    private Integer actor1CharOffset;

    private Integer actor2CharOffset;

    private Integer actionCharOffset;

    @Column(nullable = false)
    private boolean inRawText;

    private Integer confidence;

    private Integer mentionDocLen;

    @Column(precision = 10, scale = 3)
    private BigDecimal mentionDocTone;

    @Column(length = 2000)
    private String mentionDocTranslationInfo;

    @Column(columnDefinition = "text")
    private String extras;

    @Column(nullable = false)
    private Instant normalizedAt;

    protected GdeltMention() {
    }

    public GdeltMention(StagingRow stagingRow, GdeltMentionRecord record) {
        this.stagingRow = stagingRow;
        this.sourceBatch = stagingRow.getSourceBatch();
        this.globalEventId = record.globalEventId();
        this.eventTimeDate = record.eventTimeDate();
        this.mentionTimeDate = record.mentionTimeDate();
        this.mentionType = record.mentionType();
        this.mentionSourceName = record.mentionSourceName();
        this.mentionIdentifier = record.mentionIdentifier();
        this.sentenceId = record.sentenceId();
        this.actor1CharOffset = record.actor1CharOffset();
        this.actor2CharOffset = record.actor2CharOffset();
        this.actionCharOffset = record.actionCharOffset();
        this.inRawText = record.inRawText();
        this.confidence = record.confidence();
        this.mentionDocLen = record.mentionDocLen();
        this.mentionDocTone = record.mentionDocTone();
        this.mentionDocTranslationInfo = record.mentionDocTranslationInfo();
        this.extras = record.extras();
    }

    @PrePersist
    void prePersist() {
        normalizedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public StagingRow getStagingRow() {
        return stagingRow;
    }

    public SourceBatch getSourceBatch() {
        return sourceBatch;
    }

    public long getGlobalEventId() {
        return globalEventId;
    }

    public LocalDateTime getEventTimeDate() {
        return eventTimeDate;
    }

    public LocalDateTime getMentionTimeDate() {
        return mentionTimeDate;
    }

    public int getMentionType() {
        return mentionType;
    }

    public String getMentionSourceName() {
        return mentionSourceName;
    }

    public String getMentionIdentifier() {
        return mentionIdentifier;
    }

    public BigDecimal getMentionDocTone() {
        return mentionDocTone;
    }
}
