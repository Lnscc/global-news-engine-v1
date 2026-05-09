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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "gdelt_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_gdelt_event_global_event_id", columnNames = "global_event_id"),
                @UniqueConstraint(name = "uk_gdelt_event_staging_row", columnNames = "staging_row_id")
        }
)
public class GdeltEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staging_row_id", nullable = false)
    private StagingRow stagingRow;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_batch_id", nullable = false)
    private SourceBatch sourceBatch;

    @Column(name = "global_event_id", nullable = false)
    private long globalEventId;

    @Column(nullable = false)
    private LocalDate eventDate;

    @Column(nullable = false)
    private int eventYear;

    @Column(length = 128)
    private String actor1Code;

    @Column(length = 512)
    private String actor1Name;

    @Column(length = 16)
    private String actor1CountryCode;

    @Column(length = 128)
    private String actor2Code;

    @Column(length = 512)
    private String actor2Name;

    @Column(length = 16)
    private String actor2CountryCode;

    @Column(nullable = false)
    private boolean rootEvent;

    @Column(length = 32)
    private String eventCode;

    @Column(length = 32)
    private String eventBaseCode;

    @Column(length = 32)
    private String eventRootCode;

    private Integer quadClass;

    @Column(precision = 10, scale = 3)
    private BigDecimal goldsteinScale;

    private Integer numMentions;

    private Integer numSources;

    private Integer numArticles;

    @Column(precision = 10, scale = 3)
    private BigDecimal averageTone;

    @Column(length = 1000)
    private String actionGeoFullName;

    @Column(length = 16)
    private String actionGeoCountryCode;

    @Column(precision = 10, scale = 6)
    private BigDecimal actionGeoLatitude;

    @Column(precision = 10, scale = 6)
    private BigDecimal actionGeoLongitude;

    private LocalDateTime dateAdded;

    @Column(length = 2000)
    private String sourceUrl;

    @Column(nullable = false)
    private Instant normalizedAt;

    protected GdeltEvent() {
    }

    public GdeltEvent(StagingRow stagingRow, GdeltEventRecord record) {
        this.stagingRow = stagingRow;
        this.sourceBatch = stagingRow.getSourceBatch();
        this.globalEventId = record.globalEventId();
        this.eventDate = record.eventDate();
        this.eventYear = record.eventYear();
        this.actor1Code = record.actor1Code();
        this.actor1Name = record.actor1Name();
        this.actor1CountryCode = record.actor1CountryCode();
        this.actor2Code = record.actor2Code();
        this.actor2Name = record.actor2Name();
        this.actor2CountryCode = record.actor2CountryCode();
        this.rootEvent = record.rootEvent();
        this.eventCode = record.eventCode();
        this.eventBaseCode = record.eventBaseCode();
        this.eventRootCode = record.eventRootCode();
        this.quadClass = record.quadClass();
        this.goldsteinScale = record.goldsteinScale();
        this.numMentions = record.numMentions();
        this.numSources = record.numSources();
        this.numArticles = record.numArticles();
        this.averageTone = record.averageTone();
        this.actionGeoFullName = record.actionGeoFullName();
        this.actionGeoCountryCode = record.actionGeoCountryCode();
        this.actionGeoLatitude = record.actionGeoLatitude();
        this.actionGeoLongitude = record.actionGeoLongitude();
        this.dateAdded = record.dateAdded();
        this.sourceUrl = record.sourceUrl();
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

    public LocalDate getEventDate() {
        return eventDate;
    }

    public int getEventYear() {
        return eventYear;
    }

    public String getActor1Code() {
        return actor1Code;
    }

    public String getActor1Name() {
        return actor1Name;
    }

    public String getActor1CountryCode() {
        return actor1CountryCode;
    }

    public String getActor2Code() {
        return actor2Code;
    }

    public String getActor2Name() {
        return actor2Name;
    }

    public String getActor2CountryCode() {
        return actor2CountryCode;
    }

    public boolean isRootEvent() {
        return rootEvent;
    }

    public String getEventCode() {
        return eventCode;
    }

    public String getEventBaseCode() {
        return eventBaseCode;
    }

    public String getEventRootCode() {
        return eventRootCode;
    }

    public Integer getQuadClass() {
        return quadClass;
    }

    public BigDecimal getGoldsteinScale() {
        return goldsteinScale;
    }

    public Integer getNumMentions() {
        return numMentions;
    }

    public Integer getNumSources() {
        return numSources;
    }

    public Integer getNumArticles() {
        return numArticles;
    }

    public BigDecimal getAverageTone() {
        return averageTone;
    }

    public String getActionGeoFullName() {
        return actionGeoFullName;
    }

    public String getActionGeoCountryCode() {
        return actionGeoCountryCode;
    }

    public BigDecimal getActionGeoLatitude() {
        return actionGeoLatitude;
    }

    public BigDecimal getActionGeoLongitude() {
        return actionGeoLongitude;
    }

    public LocalDateTime getDateAdded() {
        return dateAdded;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public Instant getNormalizedAt() {
        return normalizedAt;
    }
}
