package com.example.globalnewsenginev1.gdelt;

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
        name = "gdelt_gkg",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_gdelt_gkg_record_id", columnNames = "gkg_record_id"),
                @UniqueConstraint(name = "uk_gdelt_gkg_staging_row", columnNames = "staging_row_id")
        }
)
public class GdeltGkg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staging_row_id", nullable = false)
    private StagingRow stagingRow;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_batch_id", nullable = false)
    private SourceBatch sourceBatch;

    @Column(name = "gkg_record_id", nullable = false, length = 128)
    private String gkgRecordId;

    @Column(nullable = false)
    private LocalDateTime date;

    private Integer sourceCollectionIdentifier;

    @Column(length = 512)
    private String sourceCommonName;

    @Column(length = 2000)
    private String documentIdentifier;

    @Column(columnDefinition = "text")
    private String counts;

    @Column(columnDefinition = "text")
    private String v2Counts;

    @Column(columnDefinition = "text")
    private String themes;

    @Column(columnDefinition = "text")
    private String v2Themes;

    @Column(columnDefinition = "text")
    private String locations;

    @Column(columnDefinition = "text")
    private String v2Locations;

    @Column(columnDefinition = "text")
    private String persons;

    @Column(columnDefinition = "text")
    private String v2Persons;

    @Column(columnDefinition = "text")
    private String organizations;

    @Column(columnDefinition = "text")
    private String v2Organizations;

    @Column(precision = 10, scale = 3)
    private BigDecimal tone;

    @Column(precision = 10, scale = 3)
    private BigDecimal positiveScore;

    @Column(precision = 10, scale = 3)
    private BigDecimal negativeScore;

    @Column(precision = 10, scale = 3)
    private BigDecimal polarity;

    @Column(precision = 10, scale = 3)
    private BigDecimal activityDensity;

    @Column(precision = 10, scale = 3)
    private BigDecimal selfGroupReferenceDensity;

    @Column(precision = 12, scale = 3)
    private BigDecimal wordCount;

    @Column(columnDefinition = "text")
    private String dates;

    @Column(columnDefinition = "text")
    private String gcam;

    @Column(length = 2000)
    private String sharingImage;

    @Column(columnDefinition = "text")
    private String relatedImages;

    @Column(columnDefinition = "text")
    private String socialImageEmbeds;

    @Column(columnDefinition = "text")
    private String socialVideoEmbeds;

    @Column(columnDefinition = "text")
    private String quotations;

    @Column(columnDefinition = "text")
    private String allNames;

    @Column(columnDefinition = "text")
    private String amounts;

    @Column(length = 2000)
    private String translationInfo;

    @Column(columnDefinition = "text")
    private String extras;

    @Column(nullable = false)
    private Instant normalizedAt;

    protected GdeltGkg() {
    }

    public GdeltGkg(StagingRow stagingRow, GdeltGkgRecord record) {
        this.stagingRow = stagingRow;
        this.sourceBatch = stagingRow.getSourceBatch();
        this.gkgRecordId = record.gkgRecordId();
        this.date = record.date();
        this.sourceCollectionIdentifier = record.sourceCollectionIdentifier();
        this.sourceCommonName = record.sourceCommonName();
        this.documentIdentifier = record.documentIdentifier();
        this.counts = record.counts();
        this.v2Counts = record.v2Counts();
        this.themes = record.themes();
        this.v2Themes = record.v2Themes();
        this.locations = record.locations();
        this.v2Locations = record.v2Locations();
        this.persons = record.persons();
        this.v2Persons = record.v2Persons();
        this.organizations = record.organizations();
        this.v2Organizations = record.v2Organizations();
        this.tone = record.tone();
        this.positiveScore = record.positiveScore();
        this.negativeScore = record.negativeScore();
        this.polarity = record.polarity();
        this.activityDensity = record.activityDensity();
        this.selfGroupReferenceDensity = record.selfGroupReferenceDensity();
        this.wordCount = record.wordCount();
        this.dates = record.dates();
        this.gcam = record.gcam();
        this.sharingImage = record.sharingImage();
        this.relatedImages = record.relatedImages();
        this.socialImageEmbeds = record.socialImageEmbeds();
        this.socialVideoEmbeds = record.socialVideoEmbeds();
        this.quotations = record.quotations();
        this.allNames = record.allNames();
        this.amounts = record.amounts();
        this.translationInfo = record.translationInfo();
        this.extras = record.extras();
    }

    @PrePersist
    void prePersist() {
        normalizedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getGkgRecordId() {
        return gkgRecordId;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public String getSourceCommonName() {
        return sourceCommonName;
    }

    public String getDocumentIdentifier() {
        return documentIdentifier;
    }

    public BigDecimal getTone() {
        return tone;
    }

    public String getThemes() {
        return themes;
    }

    public String getPersons() {
        return persons;
    }

    public String getOrganizations() {
        return organizations;
    }
}
