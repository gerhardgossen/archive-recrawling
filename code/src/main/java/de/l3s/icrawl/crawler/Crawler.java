package de.l3s.icrawl.crawler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import de.l3s.icrawl.crawler.analysis.ResourceAnalyser;
import de.l3s.icrawl.crawler.analysis.ResourceAnalyser.WeightingMethod;
import de.l3s.icrawl.crawler.analysis.ResourceAnalyserFactory;
import de.l3s.icrawl.crawler.frontier.FileBasedFrontier;
import de.l3s.icrawl.crawler.frontier.Frontier;
import de.l3s.icrawl.crawler.io.ArchiveFetcher;
import de.l3s.icrawl.crawler.io.ResultStorer;
import de.l3s.icrawl.crawler.scheduling.StoppingCriterion;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class Crawler {
    private static final String PROGRESS_METRIC_KEY = "progress";
    public static final String NUM_URLS = "de_l3s_icrawl_crawler_numUrls";
    public static final String WEIGHTING_METHOD = "de_l3s_icrawl_crawler_weightingMethod";
    public static final String RELEVANCE_THRESHOLD = "de_l3s_icrawl_crawler_relevanceThreshold";
    public static final String SNAPSHOTS_TO_ANALYZE = "de_l3s_icrawl_crawler_snapshotsToAnalyze";
    private static final Logger logger = LoggerFactory.getLogger(Crawler.class);
    private static final int PRIORITY_STEPS = 100;
    private static final float INJECT_PRIORITY = 1.0f;
    private final MetricRegistry metrics;
    private final int numThreads;
    private final ResourceAnalyserFactory analyserFactory;
    private final String indexPath;
    private final String dataPath;
    private final ResultStorer.Factory storerFactory;
    private final ExecutorService threadPool;
    private List<CrawlerThread> threads;
    private ArchiveCrawlSpecification spec;
    private List<Future<?>> threadFutures;
    private final Configuration conf;

    public Crawler(Configuration conf, String indexPath, String dataPath, ResourceAnalyserFactory analyserFactory,
            ResultStorer.Factory storerFactory, MetricRegistry metrics, int numThreads) throws IOException {
        this.conf = conf;
        this.indexPath = indexPath;
        this.dataPath = dataPath;
        this.analyserFactory = analyserFactory;
        this.storerFactory = storerFactory;
        this.metrics = metrics;
        this.numThreads = numThreads;
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
            .setNameFormat("fetcher-%d")
            .setUncaughtExceptionHandler((t, e) -> logger.warn("Uncaught exception in {} ", t, e))
            .build();
        threadPool = Executors.newFixedThreadPool(numThreads + 1, threadFactory);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Running shutdown hook");
            Crawler.this.shutdown();
        }));
    }

    public void crawlContinuously(ArchiveCrawlSpecification spec, StoppingCriterion stoppingCriterion, WeightingMethod method,
            double relevanceThreshold, int snapshotsToAnalyze) throws IOException {
        this.spec = spec;
        logger.info("Starting crawl with {} threads", numThreads);
        stoppingCriterion.addListener(() -> Crawler.this.stop(false));
        metrics.register(PROGRESS_METRIC_KEY, stoppingCriterion);
        File queueDirectory = new File(spec.getName() + ".frontier");
        String outputName = String.format("%s-%s-%d", spec.getName(), method.name(), snapshotsToAnalyze);
        try (ResultStorer storer = storerFactory.get(outputName);
                Frontier queue = new FileBasedFrontier(queueDirectory, metrics, PRIORITY_STEPS, false)) {

            Set<CrawlUrl> seeds = spec
                .getSeedUrls()
                .stream()
                .map(url -> CrawlUrl.fromSeed(url, INJECT_PRIORITY))
                .collect(toSet());
            queue.push(seeds);
            threads = new ArrayList<>(numThreads);
            CountDownLatch barrier = new CountDownLatch(numThreads);
            for (int i = 0; i < numThreads; i++) {
                ResourceAnalyser analyser = analyserFactory.get(spec, method);
                ArchiveFetcher fetcher = new ArchiveFetcher(conf, indexPath, dataPath, metrics, snapshotsToAnalyze);
                threads.add(new CrawlerThread(queue, fetcher, storer, analyser, metrics, spec, barrier, stoppingCriterion,
                    relevanceThreshold));
            }
            threadFutures = threads.stream().map(threadPool::submit).collect(toList());
            logger.info("Started {} crawler threads, waiting for them to finish", barrier.getCount());
            try {
                barrier.await();
                logger.info("All threads finished, done.");
            } catch (InterruptedException e) {
                logger.info("Interrupted while waiting for crawl to finish, stopping crawl");
                stop(true);
            }
        } finally {
            this.spec = null;
            metrics.remove(PROGRESS_METRIC_KEY);
        }
    }

    public void stop(boolean interruptRunningFetches) {
        logger.info("Stopping crawler");
        threadPool.submit(() -> {
            if (threads != null) {
                threads.forEach(CrawlerThread::stop);
            }
            if (threadFutures != null) {
                for (Future<?> threadFuture : threadFutures) {
                    try {
                        threadFuture.get(30, TimeUnit.SECONDS);
                    } catch (InterruptedException | ExecutionException e1) {
                        logger.info("Exception while waiting for stopped thread", e1);
                    } catch (TimeoutException e2) {
                        logger.info("CrawlerThread {} did not shut down in the allocated time", threadFuture, e2);
                    }
                }
            }
        });
    }

    public void shutdown() {
        stop(true);
        boolean stopped = false;
        try {
            threadPool.shutdown();
            stopped = threadPool.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            stopped = false;
        }
        if (!stopped) {
            logger.warn("Could not stop all running fetchers");
            threadPool.shutdownNow();
        } else {
            logger.info("Stopped crawler");
        }
    }

    public Optional<ArchiveCrawlSpecification> getCurrentSpec() {
        return Optional.ofNullable(this.spec);
    }
}
