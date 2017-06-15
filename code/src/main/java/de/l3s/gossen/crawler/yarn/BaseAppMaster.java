package de.l3s.gossen.crawler.yarn;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.yarn.api.records.FinalApplicationStatus;
import org.apache.hadoop.yarn.client.api.AMRMClient;
import org.apache.hadoop.yarn.client.api.AMRMClient.ContainerRequest;
import org.apache.hadoop.yarn.client.api.NMClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.util.concurrent.AtomicDouble;

import de.l3s.gossen.crawler.ArchiveCrawler;
import de.l3s.gossen.crawler.Crawler;

public abstract class BaseAppMaster {
    private static final Logger LOG = LoggerFactory.getLogger(BaseAppMaster.class);
    protected final Configuration conf;
    protected final AtomicDouble progress;

    public BaseAppMaster() {
        conf = HBaseConfiguration.create(new YarnConfiguration());
        progress = new AtomicDouble();
    }

    public void run(String[] args) throws YarnException, IOException {
        LOG.info("Starting AppMaster with args {}", (Object) args);

        AMRMClient<ContainerRequest> rmClient = AMRMClient.createAMRMClient();
        rmClient.init(conf);
        rmClient.start();

        NMClient nmClient = NMClient.createNMClient();
        nmClient.init(conf);
        nmClient.start();

        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
        ScheduledFuture<?> heartBeatfuture = startHeartBeatTask(conf, rmClient, executorService, progress);

        try {
            SpringApplication app = new SpringApplication(ArchiveCrawler.class);
            Set<String> profiles = getProfiles();
            if (!profiles.isEmpty()) {
                app.setAdditionalProfiles(profiles.toArray(new String[0]));
            }
            String basePath = "/yarn2" + System.getenv("APPLICATION_WEB_PROXY_BASE");
            if (!basePath.endsWith("/")) {
                basePath += "/";
            }
            ImmutableMap.Builder<String, Object> defaultProps = ImmutableMap.builder();
            defaultProps.put("basePath", basePath);
            addDefaultProps(defaultProps);
            app.setDefaultProperties(defaultProps.build());
            ApplicationContext context = app.run(args);

            int serverPort = context.getBean(ArchiveCrawler.class).getServerPort();
            String hostname = getHostname();

            rmClient.registerApplicationMaster(hostname, 0, "http://" + hostname + ":" + serverPort + "/");
            LOG.info("registerApplicationMaster done");

            Crawler crawler = context.getBean(Crawler.class);
            crawl(context, crawler);
            crawler.shutdown();
            rmClient.unregisterApplicationMaster(FinalApplicationStatus.SUCCEEDED, "Finished", null);
        } catch (Exception e) {
            LOG.info("Failed with exception: ", e);
            rmClient.unregisterApplicationMaster(FinalApplicationStatus.FAILED, e.getMessage(), null);
        } finally {
            if (heartBeatfuture != null) {
                heartBeatfuture.cancel(true);
            }
            executorService.shutdown();
            LOG.info("Shutdown of heartbeat is finished.");
        }
    }

    protected Set<String> getProfiles() {
        return Collections.emptySet();
    }

    protected abstract void crawl(ApplicationContext context, Crawler crawler) throws IOException;

    protected void addDefaultProps(Builder<String, Object> defaultProps) {
        // do nothing
    }

    protected long getMaxUrls() {
        String maxUrlsEnv = System.getenv(Crawler.NUM_URLS);
        return maxUrlsEnv != null ? Long.parseLong(maxUrlsEnv) : 10_000;
    }

    private ScheduledFuture<?> startHeartBeatTask(Configuration conf, final AMRMClient<ContainerRequest> rmClient, ScheduledExecutorService executorService, final AtomicDouble progress) {
        int heartBeatInterval = conf.getInt(YarnConfiguration.RM_AM_EXPIRY_INTERVAL_MS,
            YarnConfiguration.DEFAULT_RM_AM_EXPIRY_INTERVAL_MS) / 2;
        heartBeatInterval = Math.min(heartBeatInterval, 10_000);
        ScheduledFuture<?> future = executorService.scheduleAtFixedRate(() -> {
            try {
                rmClient.allocate(progress.floatValue());
            } catch (Exception e) {
                LOG.info("Exception during heartbeat", e);
            }
        }, heartBeatInterval, heartBeatInterval, TimeUnit.MILLISECONDS);
        LOG.info("Started heartbeat task {} every {} ms", future, heartBeatInterval);
        return future;
    }

    private static String getHostname() {
        try {
            return enumerationAsStream(NetworkInterface.getNetworkInterfaces())
                .filter(BaseAppMaster::notLoopback)
                .flatMap(ni -> enumerationAsStream(ni.getInetAddresses()))
                .filter(addr -> addr instanceof Inet4Address)
                .map(InetAddress::getCanonicalHostName)
                .findFirst()
                .orElse("localhost");
        } catch (SocketException e) {
            LOG.info("Exception while retrieving hostName", e);
            return "localhost";
        }
    }

    private static boolean notLoopback(NetworkInterface ni) {
        try {
            return !ni.isLoopback();
        } catch (SocketException e) {
            return false;
        }
    }

    private static <T> Stream<T> enumerationAsStream(Enumeration<T> e) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new Iterator<T>() {
            @Override
            public T next() {
                return e.nextElement();
            }

            @Override
            public boolean hasNext() {
                return e.hasMoreElements();
            }
        }, Spliterator.ORDERED), false);
    }

}
