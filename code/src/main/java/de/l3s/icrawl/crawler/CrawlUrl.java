package de.l3s.icrawl.crawler;

import java.time.ZonedDateTime;
import java.util.Objects;

import com.google.common.base.Preconditions;

public class CrawlUrl {
    public enum Path {
        SEED('S'), LINK('L');

        private final char name;

        private Path(char name) {
            this.name = name;
        }

        public char getName() {
            return name;
        }
    }

    private final String url;
    private final String path;
    private final float priority;
    private final String referrer;
    private final ZonedDateTime refererCrawlTime;

    public CrawlUrl(String url, String path, float priority, String referrer, ZonedDateTime refererCrawlTime) {
        Objects.requireNonNull(url);
        Preconditions.checkArgument(path != null && !path.isEmpty(), "Invalid path: '%s'", path);
        Preconditions.checkArgument(0 <= priority && priority <= 1, "Invalid priority, %s not in [0,1]", priority);
        Preconditions.checkArgument((referrer == null) == (refererCrawlTime == null),
            "Referrer and crawl time must be give together or not at all");
        this.url = url;
        this.path = path;
        this.priority = priority;
        this.referrer = referrer;
        this.refererCrawlTime = refererCrawlTime;
    }

    public static CrawlUrl fromSeed(String url, float priority) {
        return new CrawlUrl(url, Character.toString(Path.SEED.getName()), priority, null, null);
    }

    public CrawlUrl outlink(String url, float priority, ZonedDateTime crawlTime) {
        return new CrawlUrl(url, this.path + Path.LINK.getName(), priority, this.url, crawlTime);
    }

    public String getUrl() {
        return url;
    }

    public String getPath() {
        return path;
    }

    public float getPriority() {
        return priority;
    }

    public String getReferrer() {
        return referrer;
    }

    public ZonedDateTime getRefererCrawlTime() {
        return refererCrawlTime;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", url, path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CrawlUrl)) {
            return false;
        }
        return url.equals(((CrawlUrl) obj).url);
    }

    /**
     * Merge with other instance.
     *
     * @param mergee
     *            a different instance (non-null)
     * @return the instance with the shorter path, iff the URL is the same,
     *         <tt>this</tt> otherwise
     */
    public CrawlUrl merge(CrawlUrl mergee) {
        if (!this.url.equals(mergee.url)) {
            return this;
        }
        return path.length() <= mergee.path.length() ? this : mergee;
    }

}
