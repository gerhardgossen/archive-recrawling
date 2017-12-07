package de.l3s.icrawl.crawler;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Ordering;

import de.l3s.icrawl.crawler.analysis.ResourceAnalyser;
import de.l3s.icrawl.crawler.analysis.ResourceAnalyser.Result;
import de.l3s.icrawl.crawler.frontier.Frontier;
import de.l3s.icrawl.crawler.io.ArchiveFetcher;
import de.l3s.icrawl.crawler.io.ResultStorer;
import de.l3s.icrawl.crawler.scheduling.StoppingCriterion;
import de.l3s.icrawl.snapshots.Snapshot;

import static com.codahale.metrics.MetricRegistry.name;

public class CrawlerThread implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(CrawlerThread.class);
    private final Frontier frontier;
    private final ArchiveFetcher fetcher;
    private final ResourceAnalyser analyser;
    private final ResultStorer storer;
    private final double relevanceThreshold;
    private final Counter retrieved;
    private final Counter notFound;
    private final Counter irrelevant;
    private final Meter crawlRate;
    private final ArchiveCrawlSpecification spec;
    private boolean stopped = false;
    private final CountDownLatch barrier;
    private final StoppingCriterion stoppingCriterion;

    public CrawlerThread(Frontier frontier, ArchiveFetcher fetcher, ResultStorer storer, ResourceAnalyser analyser,
            MetricRegistry metrics, ArchiveCrawlSpecification spec, CountDownLatch barrier,
            StoppingCriterion stoppingCriterion, double relevanceThreshold) {
        this.frontier = frontier;
        this.fetcher = fetcher;
        this.storer = storer;
        this.analyser = analyser;
        this.spec = spec;
        this.barrier = barrier;
        this.stoppingCriterion = stoppingCriterion;
        this.relevanceThreshold = relevanceThreshold;
        crawlRate = metrics.meter(name(getClass(), "crawlRate"));
        retrieved = metrics.counter(name(getClass(), "retrieved"));
        notFound = metrics.counter(name(getClass(), "notFound"));
        irrelevant = metrics.counter(name(getClass(), "irrelevant"));
    }

    @Override
    public void run() {
        logger.info("Starting crawler thread");
        try {
            for (;;) {
                if (Thread.interrupted()) {
                    logger.info("Stopping because of thread interrupt");
                    break;
                }
                if (stopped) {
                    logger.info("Stopping because of external stop");
                    break;
                }
                Optional<CrawlUrl> url = frontier.pop();
                if (!url.isPresent()) {
                    try {
                        stoppingCriterion.updateEmptyQueue();
                        TimeUnit.MILLISECONDS.sleep(10);
                        continue;
                    } catch (InterruptedException e) {
                        logger.info("Interrupted while waiting for URLs to arrive, stopping");
                        break;
                    }
                }
                fetch(url.get());
            }
            fetcher.close();
        } catch (Throwable t) {
            logger.info("Very unexpected exception", t);
        } finally {
            logger.info("Crawler thread finished");
            barrier.countDown();
        }
    }

    private void fetch(CrawlUrl crawlUrl) {
        try {
            crawlRate.mark();
            List<Snapshot> snapshots = fetcher.get(crawlUrl, spec.getReferenceTime());
            if (snapshots.isEmpty()) {
                storer.storeNotFound(crawlUrl);
                notFound.inc();
                stoppingCriterion.updateFailure();
            } else {
                Result bestResult = null;
                Snapshot bestSnapshot = null;
                double minRelevance = Double.POSITIVE_INFINITY;
                double maxRelevance = Double.NEGATIVE_INFINITY;
                ZonedDateTime earliestDate = ZonedDateTime.now();
                ZonedDateTime latestDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);

                for (Snapshot snapshot : snapshots) {
                    Result result = analyser.analyse(snapshot, crawlUrl);
                    double relevance = result.getRelevance();
                    if (bestResult == null || bestResult.getRelevance() < relevance) {
                        bestResult = result;
                        bestSnapshot = snapshot;
                    }

                    if (relevance >= 0) {
                        minRelevance = Double.min(minRelevance, relevance);
                        maxRelevance = Double.max(maxRelevance, relevance);
                    }
                    if (result.getModifiedDate() != null) {
                        earliestDate = Ordering.natural().min(earliestDate, result.getModifiedDate());
                        latestDate = Ordering.natural().max(latestDate, result.getModifiedDate());
                    }
                }
                if (stopped) {
                    return;
                }

                assert (bestResult != null);

                if (bestResult.getRelevance() < relevanceThreshold) {
                    irrelevant.inc();
                    stoppingCriterion.updateIrrelevant(bestResult.getRelevance());
                } else {
                    frontier.push(bestResult.getOutlinks());
                    storer.store(new CrawledResource(crawlUrl, bestSnapshot, bestResult.getRelevance(),
                        bestResult.getModifiedDate(), Duration.between(earliestDate, latestDate), minRelevance,
                        maxRelevance));
                    retrieved.inc();
                    stoppingCriterion.updateSuccess(bestResult.getRelevance());
                }
            }
        } catch (IOException e) {
            logger.info("Exception while fetching '{}', skipping ", crawlUrl, e);
        } catch (Exception e) {
            logger.info("Unexpected exception ", e);
        }
    }

    public void stop() {
        this.stopped = true;
    }
}
