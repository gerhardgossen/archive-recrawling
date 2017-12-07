package de.l3s.icrawl.crawler;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import com.codahale.metrics.CsvReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.l3s.icrawl.crawler.analysis.ResourceAnalyser;
import de.l3s.icrawl.crawler.analysis.ResourceAnalyserFactory;
import de.l3s.icrawl.crawler.analysis.ResourceAnalyser.WeightingMethod;
import de.l3s.icrawl.crawler.io.CsvStorer;
import de.l3s.icrawl.crawler.io.ResultStorer;
import de.l3s.icrawl.crawler.io.ZipFileStorer;
import de.l3s.icrawl.crawler.scheduling.NumberOfUrlsStoppingCriterion;
import de.l3s.icrawl.crawler.ui.UiConfig;

@Configuration
@EnableAutoConfiguration(exclude = { HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class })
@Import(UiConfig.class)
public class ArchiveCrawler implements ApplicationListener<EmbeddedServletContainerInitializedEvent> {

    public static final String PROFILE_EVALUATION = "evaluation";
    public static final String PROFILE_EXTRACT = "extract";

    public interface StorerConfig {
        ResultStorer.Factory storerFactory(org.apache.hadoop.conf.Configuration conf) throws IOException;
    }

    @Configuration
    @Profile(PROFILE_EVALUATION)
    public static class CsvStorerConfig implements StorerConfig {
        @Value("${outputDirectory}")
        public String outputDirectory;

        @Bean
        @Override
        public ResultStorer.Factory storerFactory(org.apache.hadoop.conf.Configuration conf) throws IOException {
            Path baseDirectory = new Path(outputDirectory);
            FileSystem fs = FileSystem.get(conf);
            if (!fs.exists(baseDirectory)) {
                fs.mkdirs(baseDirectory, FsPermission.valueOf("-rwxrwxrwx"));
            }
            return (name) -> new CsvStorer(conf, new Path(baseDirectory, name + ".csv"));
        }
    }

    @Configuration
    @Profile("extract")
    public static class ZipFileStorerConfig implements StorerConfig {

        @Value("${maxUrls}")
        public int maxUrls;

        @Value("${outputDirectory}")
        public String outputDirectory;

        @Override
        public ResultStorer.Factory storerFactory(org.apache.hadoop.conf.Configuration conf) throws IOException {
            final Path baseDirectory = new Path(outputDirectory);
            FileSystem fs = FileSystem.get(conf);
            fs.mkdirs(baseDirectory);
            return (name) -> {
                logger.info("Creating new ZipFileStorer for '{}'", name);
                return new ZipFileStorer(fs.create(new Path(baseDirectory, name + ".zip"), true), maxUrls);
            };
        }

    }

    private static final Logger logger = LoggerFactory.getLogger(ArchiveCrawler.class);

    @Value("${cdxPath}")
    String indexPath;

    @Value("${warcRoot}")
    String dataPath;

    @Value("${numThreads:10}")
    int numThreads;

    @Value("${timeRelevanceThreshold:0.25}")
    float timeRelevanceThreshold;
    @Value("${docSimilarityWeight:0.5}")
    float docSimilarityWeight;

    private int serverPort;

    public static final String IDF_DICTIONARY_DE = "dictionary-DE.tsv.gz";

    @Bean
    static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    ResourceAnalyserFactory raf() {
        return new ResourceAnalyser.Factory(metrics(), timeRelevanceThreshold, docSimilarityWeight);
    }

    @Inject
    StorerConfig storerConfig;

    @Bean
    MetricRegistry metrics() {
        MetricRegistry metrics = new MetricRegistry();
        metrics.register("threads", new ThreadStatesGaugeSet());
        return metrics;
    }

    @Value("${logdir}")
    File logDir;

    @Bean
    ScheduledReporter reporter() {
        ScheduledReporter reporter = CsvReporter
            .forRegistry(metrics())
            .formatFor(Locale.ROOT)
            .convertDurationsTo(TimeUnit.MILLISECONDS)
            .convertRatesTo(TimeUnit.SECONDS)
            .build(logDir);
        reporter.start(1, TimeUnit.MINUTES);
        logger.info("Started logging metrics every minute");
        return reporter;
    }

    @Bean
    org.apache.hadoop.conf.Configuration conf() {
        return HBaseConfiguration.create(new YarnConfiguration());
    }

    @Bean
    Crawler crawler() throws IOException {
        return new Crawler(conf(), indexPath, dataPath, raf(), storerConfig.storerFactory(conf()), metrics(), numThreads);
    }

    @Bean
    Module jsr310Module() {
        return new JavaTimeModule();
    }

    @Override
    public void onApplicationEvent(EmbeddedServletContainerInitializedEvent event) {
        serverPort = event.getEmbeddedServletContainer().getPort();
    }

    public int getServerPort() {
        return serverPort;
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println(
                "Usage: java " + Crawler.class.getName() + " specification [num_urls [weightingMethod [snapshotsToAnalyze]]");
            System.exit(1);
        }
        SpringApplication app = new SpringApplication(ArchiveCrawler.class);
        app.setWebEnvironment(false);
        ApplicationContext context = app.run(args);
        Crawler crawler = context.getBean(Crawler.class);
        ArchiveCrawlSpecification spec = ArchiveCrawlSpecification.readFile(new File(args[0]));
        long numUrls = args.length >= 2 ? Long.parseLong(args[1]) : 10_000;
        WeightingMethod method = args.length >= 3 ? WeightingMethod.valueOf(args[2]) : WeightingMethod.CONTENT;
        int snapshotsToAnalyze = args.length >= 4 ? Integer.parseInt(args[3]) : 10;
        crawler.crawlContinuously(spec, new NumberOfUrlsStoppingCriterion(numUrls), method, -Double.MAX_VALUE,
            snapshotsToAnalyze);
        context.getBean(ScheduledReporter.class).report();
        crawler.shutdown();
    }

}
