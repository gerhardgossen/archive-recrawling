package de.l3s.gossen.crawler.yarn;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.context.ApplicationContext;

import com.codahale.metrics.ScheduledReporter;
import com.google.common.collect.ImmutableMap;

import de.l3s.gossen.crawler.ArchiveCrawlSpecification;
import de.l3s.gossen.crawler.ArchiveCrawler;
import de.l3s.gossen.crawler.Crawler;
import de.l3s.gossen.crawler.analysis.ResourceAnalyser.WeightingMethod;
import de.l3s.gossen.crawler.io.ResultStorer;
import de.l3s.gossen.crawler.scheduling.CompositeStoppingCriterion;
import de.l3s.gossen.crawler.scheduling.NumberOfUrlsStoppingCriterion;
import de.l3s.gossen.crawler.scheduling.QueueEmptyCriterion;
import de.l3s.gossen.crawler.scheduling.StoppingCriterion;

public class AppMaster extends BaseAppMaster {

    @Override
    protected void addDefaultProps(ImmutableMap.Builder<String, Object> defaultProps) {
        defaultProps.put("maxUrls", getMaxUrls()).put("outputDirectory", System.getenv(ResultStorer.OUTPUT_DIRECTORY));
    }

    @Override
    protected void crawl(ApplicationContext context, Crawler crawler) throws IOException {
        File specFile = new File("spec.json");
        ArchiveCrawlSpecification spec = ArchiveCrawlSpecification.readFile(specFile);
        WeightingMethod method = Optional.ofNullable(System.getenv(Crawler.WEIGHTING_METHOD))
            .map(WeightingMethod::valueOf)
            .orElse(WeightingMethod.CONTENT_AND_TIME);
        double relevanceThreshold = Optional.ofNullable(System.getenv(Crawler.RELEVANCE_THRESHOLD))
            .map(Double::valueOf)
            .orElse(-Double.MAX_VALUE);
        int snapshotsToAnalyze = Optional.ofNullable(System.getenv(Crawler.SNAPSHOTS_TO_ANALYZE))
            .map(Integer::valueOf)
            .orElse(1);

        StoppingCriterion urlsStoppingCriterion = new NumberOfUrlsStoppingCriterion(getMaxUrls());
        StoppingCriterion stoppingCriterion = new CompositeStoppingCriterion(urlsStoppingCriterion,
                new QueueEmptyCriterion(30, TimeUnit.SECONDS));
        ScheduledFuture<?> future = Executors.newSingleThreadScheduledExecutor()
            .schedule(() -> progress.set(urlsStoppingCriterion.getProgress()), 1, TimeUnit.SECONDS);
        crawler.crawlContinuously(spec, stoppingCriterion, method, relevanceThreshold, snapshotsToAnalyze);
        context.getBean(ScheduledReporter.class).report();
        future.cancel(false);
        progress.set(1.0);
    }

    @Override
    protected Set<String> getProfiles() {
        return Collections.singleton(ArchiveCrawler.PROFILE_EXTRACT);
    }

    public static void main(String[] args) throws Exception {
        new AppMaster().run(args);
    }

}
