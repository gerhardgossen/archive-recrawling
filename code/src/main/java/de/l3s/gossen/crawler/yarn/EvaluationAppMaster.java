package de.l3s.gossen.crawler.yarn;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.filefilter.SuffixFileFilter;
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

import static java.util.Collections.singletonList;

public class EvaluationAppMaster extends BaseAppMaster {

    @Override
    protected void addDefaultProps(ImmutableMap.Builder<String, Object> defaultProps) {
        defaultProps.put("maxUrls", getMaxUrls()).put("outputDirectory", System.getenv(ResultStorer.OUTPUT_DIRECTORY));
    }

    @Override
    protected void crawl(ApplicationContext context, Crawler crawler) throws IOException {
        FilenameFilter filter = new SuffixFileFilter(".json");
        File[] specFiles = new File("specs").listFiles(filter);
        for (File specFile : specFiles) {
            ArchiveCrawlSpecification spec = ArchiveCrawlSpecification.readFile(specFile);
            for (WeightingMethod method : WeightingMethod.values()) {
                for (int snapshotsToAnalyze : snapshotVariants(method)) {
                    StoppingCriterion stoppingCriterion = new CompositeStoppingCriterion(
                        new NumberOfUrlsStoppingCriterion(getMaxUrls()), new QueueEmptyCriterion(30, TimeUnit.SECONDS));
                    crawler.crawlContinuously(spec, stoppingCriterion, method, -Double.MAX_VALUE, snapshotsToAnalyze);
                    context.getBean(ScheduledReporter.class).report();
                }
            }
            progress.addAndGet(1.0 / specFiles.length);
        }
    }

    private List<Integer> snapshotVariants(WeightingMethod method) {
        switch (method) {
        case CONTENT:
        case CONTENT_AND_TIME:
        case CONTENT_AND_TIME_EXP:
            return Arrays.asList(1, 10, 100);
        case TIME:
        case TIME_EXP:
        case UNFOCUSED:
        default:
            return singletonList(1);
        }
    }

    @Override
    protected Set<String> getProfiles() {
        return Collections.singleton(ArchiveCrawler.PROFILE_EVALUATION);
    }

    public static void main(String[] args) throws Exception {
        new EvaluationAppMaster().run(args);
    }

}
