package de.l3s.icrawl.crawler.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openimaj.text.nlp.language.LanguageDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;

import de.l3s.icrawl.contentanalysis.DocumentVectorSimilarity;
import de.l3s.icrawl.contentanalysis.LanguageModels;
import de.l3s.icrawl.contentanalysis.WebPageDateExtractor;
import de.l3s.icrawl.contentanalysis.WebPageDateExtractor.WebPageDate;
import de.l3s.icrawl.crawler.ArchiveCrawlSpecification;
import de.l3s.icrawl.crawler.ArchiveCrawler;
import de.l3s.icrawl.crawler.CrawlUrl;
import de.l3s.icrawl.crawler.TimeSpecification;
import de.l3s.icrawl.crawler.urls.RegexUrlNormalizer;
import de.l3s.icrawl.crawler.urls.UrlCanonicalizerNormalizer;
import de.l3s.icrawl.crawler.urls.UrlFilter;
import de.l3s.icrawl.crawler.urls.UrlNormalizer;
import de.l3s.icrawl.crawler.urls.UrlNormalizers;
import de.l3s.icrawl.snapshots.Snapshot;
import de.l3s.icrawl.util.TextExtractor;

import static com.codahale.metrics.MetricRegistry.name;

public class ResourceAnalyser {
    public static class Result {
        public final static Result EMPTY = new Result(ImmutableSet.<CrawlUrl> of(), -1, null);
        private final Collection<CrawlUrl> outlinks;
        private final double relevance;
        private final ZonedDateTime modifiedDate;

        Result(Collection<CrawlUrl> outlinks, double relevance, ZonedDateTime modifiedDate) {
            this.outlinks = outlinks;
            this.relevance = relevance;
            this.modifiedDate = modifiedDate;
        }

        public Collection<CrawlUrl> getOutlinks() {
            return outlinks;
        }

        public double getRelevance() {
            return relevance;
        }

        public ZonedDateTime getModifiedDate() {
            return modifiedDate;
        }
    }

    public enum WeightingMethod {
        CONTENT(false), TIME(true), TIME_EXP(true), CONTENT_AND_TIME(true), CONTENT_AND_TIME_EXP(true), UNFOCUSED(false);

        private WeightingMethod(boolean timeSensitive) {
            this.timeSensitive = timeSensitive;
        }

        private final boolean timeSensitive;

        public boolean isTimeSensitive() {
            return timeSensitive;
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(ResourceAnalyser.class);

    public static class Factory implements ResourceAnalyserFactory {

        private final MetricRegistry metrics;
        private final float timeRelevanceThreshold;
        private final float docSimilarityWeight;

        public Factory(MetricRegistry metrics, float timeRelevanceTreshold, float docSimilarityWeight) {
            this.metrics = metrics;
            timeRelevanceThreshold = timeRelevanceTreshold;
            this.docSimilarityWeight = docSimilarityWeight;
        }

        @Override
        public ResourceAnalyser get(ArchiveCrawlSpecification spec, WeightingMethod method) throws IOException {
            return new ResourceAnalyser(spec, method, metrics, timeRelevanceThreshold, docSimilarityWeight);
        }

    }

    private final UrlFilter urlFilter;
    private final UrlNormalizer urlNormalizer;
    private final Histogram outlinkCount;
    private final Counter unknowns;
    private final Counter empty;
    private final DocumentVectorSimilarity similarity;
    private final LanguageDetector languageDetector = new LanguageDetector();
    private final Timer parseTime;
    private final Timer textExtractTime;
    private final Timer analysisTime;
    private final WeightingMethod method;
    private final TimeSpecification referenceTime;
    private final Timer dateExtractionTime;
    private final float timeRelevanceThreshold;
    private final float docSimilarityWeight;

    public ResourceAnalyser(ArchiveCrawlSpecification spec, WeightingMethod method, MetricRegistry metrics,
            float timeRelevanceThreshold, float docSimilarityWeight) throws IOException {
        Preconditions.checkArgument(0 <= docSimilarityWeight && docSimilarityWeight <= 1.0, "docSimilarityWeight");
        this.method = method;
        this.docSimilarityWeight = docSimilarityWeight;
        this.timeRelevanceThreshold = timeRelevanceThreshold;
        Map<String, Double> dictionary;
        try (InputStream is = new GZIPInputStream(Resources.getResource(ArchiveCrawler.IDF_DICTIONARY_DE).openStream())) {
            dictionary = LanguageModels.readIdfDictionary(is);
        }
        LanguageModels models = new LanguageModels(Locale.GERMAN, dictionary, spec.getDefaultLanguage());
        similarity = DocumentVectorSimilarity.fromVectors(spec.getReferenceVectors(), spec.getKeywords(),
            spec.getDefaultLanguage(), models, spec.getCorrectionFactors());
        referenceTime = spec.getReferenceTime();
        urlFilter = UrlFilter.ONLY_HTTP;
        urlNormalizer = new UrlNormalizers(new UrlCanonicalizerNormalizer(),
            new RegexUrlNormalizer(Resources.getResource("default-regex-normalizers.xml")));
        outlinkCount = metrics.histogram(name(getClass(), "numOutlinks"));
        unknowns = metrics.counter(name(getClass(), "unknownType"));
        empty = metrics.counter(name(getClass(), "empty"));
        parseTime = metrics.timer(name(getClass(), "parseTime"));
        textExtractTime = metrics.timer(name(getClass(), "textExtractTime"));
        analysisTime = metrics.timer(name(getClass(), "analysisTime"));
        dateExtractionTime = metrics.timer(name(getClass(), "dateExtractionTime"));
    }

    public Result analyse(Snapshot resource, CrawlUrl url) {
        Object content = resource.getContent();
        if (content instanceof String) {
            Timer.Context timer = parseTime.time();
            Document doc = Jsoup.parse((String) content, url.getUrl());
            timer.stop();
            timer = textExtractTime.time();
            String text = TextExtractor.extractText(doc);
            timer.stop();
            if (text.trim().isEmpty()) {
                logger.debug("No content for URL '{}", url);
                empty.inc();
                return Result.EMPTY;
            }
            timer = analysisTime.time();
            Locale language = languageDetector.classify(text).getLocale();
            float docSimilarity = (float) similarity.getSimilarity(language, text);
            timer.stop();

            float timeRelevance = 1.0f;
            WebPageDate modifiedDate;
            try (Timer.Context t = dateExtractionTime.time()) {
                long crawlTimeMs = resource.getCrawlTime().toInstant().toEpochMilli();
                modifiedDate = WebPageDateExtractor.getModifiedDate(resource.getOriginalUrl(), doc, crawlTimeMs, null);
                if (modifiedDate != null && modifiedDate.getDate() != null) {
                    if (method == WeightingMethod.TIME || method == WeightingMethod.CONTENT_AND_TIME) {
                        timeRelevance = (float) referenceTime.getRelevance(modifiedDate.getDate());
                    } else {
                        timeRelevance = (float) referenceTime.getRelevanceExp(modifiedDate.getDate());
                    }
                }
            } catch (InterruptedException e) {
                logger.info("Interrupted while extracting date", e);
                return Result.EMPTY;
            }

            float outlinkScore = outlinkScore(docSimilarity, timeRelevance);

            ImmutableMultiset.Builder<CrawlUrl> outlinks = ImmutableMultiset.builder();
            for (Element link : doc.select("a[href]")) {
                String docUrl = link.absUrl("href");
                if (docUrl.trim().isEmpty() || !docUrl.startsWith("http")) {
                    logger.trace("Skipping URL '{}'", docUrl);
                    continue;
                }
                String outUrl = urlNormalizer.normalize(docUrl);
                if (urlFilter.apply(outUrl)) {
                    outlinks.add(url.outlink(outUrl, outlinkScore, resource.getCrawlTime()));
                }
            }
            Set<CrawlUrl> outUrls = outlinks.build().elementSet();
            outlinkCount.update(outUrls.size());
            logger.debug("Extracted outlinks for URL {}, got {}", url, outUrls.size());
            ZonedDateTime modifiedDateDate = modifiedDate != null ? modifiedDate.getDate() : null;
            return new Result(outUrls, docSimilarity, modifiedDateDate);
        } else {
            logger.debug("Unhandled content type '{}' for URL '{}'", resource.getMimeType(), url);
            unknowns.inc();
            return Result.EMPTY;
        }
    }

    private float outlinkScore(float docSimilarity, float timeRelevance) {
        switch (method) {
        case CONTENT:
            return docSimilarity;

        case CONTENT_AND_TIME:
        case CONTENT_AND_TIME_EXP:
            if (docSimilarity > timeRelevanceThreshold) {
                return (docSimilarityWeight * docSimilarity) + ((1 - docSimilarityWeight) * timeRelevance);
            } else {
                return docSimilarity;
            }
        case TIME:
        case TIME_EXP:
            return timeRelevance;
        case UNFOCUSED:
            return 1f;
        default:
            throw new IllegalStateException("Unhandled weighting method " + method);
        }
    }

}
