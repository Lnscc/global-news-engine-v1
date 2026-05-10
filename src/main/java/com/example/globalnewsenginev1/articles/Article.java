package com.example.globalnewsenginev1.articles;

import com.example.globalnewsenginev1.gdelt.GdeltGkg;
import com.example.globalnewsenginev1.ingestion.SourceBatch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "article",
        uniqueConstraints = @UniqueConstraint(name = "uk_article_canonical_url", columnNames = "canonical_url")
)
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "canonical_url", nullable = false, length = 2000)
    private String canonicalUrl;

    @Column(nullable = false, length = 2000)
    private String originalUrl;

    @Column(length = 512)
    private String sourceName;

    @Column(length = 512)
    private String sourceDomain;

    @Column(nullable = false)
    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(columnDefinition = "text")
    private String themes;

    @Column(columnDefinition = "text")
    private String persons;

    @Column(columnDefinition = "text")
    private String organizations;

    @Column(precision = 10, scale = 3)
    private BigDecimal tone;

    @Column(precision = 10, scale = 3)
    private BigDecimal positiveScore;

    @Column(precision = 10, scale = 3)
    private BigDecimal negativeScore;

    @Column(precision = 10, scale = 3)
    private BigDecimal polarity;

    @Column(precision = 12, scale = 3)
    private BigDecimal wordCount;

    @Column(length = 2000)
    private String sharingImage;

    @Column(columnDefinition = "text")
    private String relatedImages;

    @Column(columnDefinition = "text")
    private String extras;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "first_gkg_id", nullable = false)
    private GdeltGkg firstGkg;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "first_seen_batch_id", nullable = false)
    private SourceBatch firstSeenBatch;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Article() {
    }

    public Article(String canonicalUrl, GdeltGkg gkg) {
        this.canonicalUrl = canonicalUrl;
        this.originalUrl = gkg.getDocumentIdentifier();
        this.sourceName = gkg.getSourceCommonName();
        this.sourceDomain = ArticleUrlNormalizer.domainOf(canonicalUrl).orElse(gkg.getSourceCommonName());
        this.publishedAt = gkg.getDate();
        this.firstSeenAt = gkg.getDate();
        this.lastSeenAt = gkg.getDate();
        this.themes = gkg.getThemes();
        this.persons = gkg.getPersons();
        this.organizations = gkg.getOrganizations();
        this.tone = gkg.getTone();
        this.positiveScore = gkg.getPositiveScore();
        this.negativeScore = gkg.getNegativeScore();
        this.polarity = gkg.getPolarity();
        this.wordCount = gkg.getWordCount();
        this.sharingImage = gkg.getSharingImage();
        this.relatedImages = gkg.getRelatedImages();
        this.extras = gkg.getExtras();
        this.firstGkg = gkg;
        this.firstSeenBatch = gkg.getSourceBatch();
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void recordSeen(GdeltGkg gkg) {
        if (gkg.getDate().isBefore(firstSeenAt)) {
            firstSeenAt = gkg.getDate();
        }
        if (gkg.getDate().isAfter(lastSeenAt)) {
            lastSeenAt = gkg.getDate();
        }
    }

    public Long getId() {
        return id;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourceDomain() {
        return sourceDomain;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public LocalDateTime getFirstSeenAt() {
        return firstSeenAt;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }
}
