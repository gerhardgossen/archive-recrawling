package de.l3s.gossen.crawler;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.springframework.core.style.ToStringCreator;

import de.l3s.gossen.snapshots.Snapshot;

public class CrawledResource {

    private final String url;
    private final String path;
    private final float crawlPriority;
    private final Snapshot resource;
    private final double relevance;
    private final ZonedDateTime modifiedDate;
    private Duration snapshotsDuration;
    private double minRelevance = -1.0;
    private double maxRelevance = -1.0;

    public CrawledResource(CrawlUrl url, Snapshot resource, double relevance, ZonedDateTime modifiedDate) {
        this.resource = resource;
        this.relevance = relevance;
        this.modifiedDate = modifiedDate;
        this.url = url.getUrl();
        path = url.getPath();
        crawlPriority = url.getPriority();
    }

    public CrawledResource(CrawlUrl url, Snapshot resource, double relevance, ZonedDateTime modifiedDate,
            Duration snapshotsDuration, double minRelevance, double maxRelevance) {
        this(url, resource, relevance, modifiedDate);
        this.snapshotsDuration = snapshotsDuration;
        this.minRelevance = minRelevance;
        this.maxRelevance = maxRelevance;
    }

    public String getUrl() {
        return url;
    }

    public String getPath() {
        return path;
    }

    public float getCrawlPriority() {
        return crawlPriority;
    }

    public Snapshot getResource() {
        return resource;
    }

    public double getRelevance() {
        return relevance;
    }

    public ZonedDateTime getModifiedDate() {
        return modifiedDate;
    }

    public Duration getSnapshotsDuration() {
        return snapshotsDuration;
    }

    public double getMinRelevance() {
        return minRelevance;
    }

    public double getMaxRelevance() {
        return maxRelevance;
    }

    @Override
    public String toString() {
        return new ToStringCreator(this).append("url", url)
            .append("crawlTime", resource.getCrawlTime())
            .append("relevance", relevance)
            .append("mimeType", resource.getMimeType())
            .append("status", resource.getStatus())
            .append("path", path)
            .append("crawlPriority", crawlPriority)
            .toString();
    }
}
