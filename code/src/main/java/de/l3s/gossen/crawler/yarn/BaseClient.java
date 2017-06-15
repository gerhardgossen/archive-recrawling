package de.l3s.gossen.crawler.yarn;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptReport;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.hadoop.yarn.util.Records;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import static org.apache.hadoop.yarn.api.ApplicationConstants.LOG_DIR_EXPANSION_VAR;

public abstract class BaseClient extends Configured implements Tool {

    private static final int MEMORY_MB = 12 * 1024;
    private static final int NUM_CORES = 10;
    private static final String TEMP_FILES_PATH = "hdfs:///tmp/archive-crawler";
    private static final Logger LOG = LoggerFactory.getLogger(BaseClient.class);

    @Override
    public int run(String[] args) throws YarnException, IOException, InterruptedException {
        configure(args);

        YarnClient client = YarnClient.createYarnClient();
        client.init(getConf());
        client.start();

        YarnClientApplication app = client.createApplication();
        prepare();

        ImmutableMap.Builder<String, String> env = ImmutableMap.builder();
        env.put("CLASSPATH", getAMClassPath());
        addParameters(env);

        ApplicationSubmissionContext appContext = createAM(getConf(), app.getApplicationSubmissionContext(),
            env.build(),
            getJarPath());

        ApplicationId appId = client.submitApplication(appContext);
        waitAndPrint(client, appId);
        client.stop();
        cleanup();
        return 0;
    }

    protected void cleanup() throws IOException {
    }

    protected void prepare() throws IOException {
    }

    private void waitAndPrint(YarnClient client, ApplicationId appId) throws YarnException, IOException, InterruptedException {
        float previousProgress = -1;
        boolean reportedUrl = false;
        while (true) {
            ApplicationReport report = client.getApplicationReport(appId);
            YarnApplicationState state = report.getYarnApplicationState();
            if (!reportedUrl && report.getTrackingUrl() != null) {
                LOG.info("Tracking URL: {}", report.getTrackingUrl());
                reportedUrl = true;
            }
            float progress = report.getProgress();
            if (Math.abs(progress - previousProgress) >= 0.01) {
                LOG.info("State={}, Progress={}", state, progress);
            }
            if (state == YarnApplicationState.FINISHED || state == YarnApplicationState.KILLED) {
                break;
            } else if (state == YarnApplicationState.FAILED) {
                LOG.info("Application {} failed with cause: ", appId, client.getFailureCause());
                break;
            }
            previousProgress = progress;
            TimeUnit.SECONDS.sleep(10);
        }
        for (ApplicationAttemptReport report : client.getApplicationAttempts(appId)) {
            LOG.info("Attempt {} report: containerId={} diagnostics={}", report.getApplicationAttemptId(),
                report.getAMContainerId(), report.getDiagnostics());
        }
    }

    protected abstract boolean checkUsage(String[] args);

    protected void addParameters(ImmutableMap.Builder<String, String> env) {
    }

    private String getAMClassPath() {
        List<String> classpathEntries = Lists.newArrayList(getConf().getStrings(
            YarnConfiguration.YARN_APPLICATION_CLASSPATH, YarnConfiguration.DEFAULT_YARN_APPLICATION_CLASSPATH));
        classpathEntries.add("$CDH_HBASE_HOME/lib/*");
        classpathEntries.add("./*");
        String classpath = Joiner.on(':').join(classpathEntries);
        return classpath;
    }

    private ApplicationSubmissionContext createAM(Configuration conf, ApplicationSubmissionContext appContext, Map<String, String> env, String jarPath) throws IOException {
        appContext.setApplicationName("archive-crawler");

        Resource resource = Records.newRecord(Resource.class);
        resource.setMemory(MEMORY_MB);
        resource.setVirtualCores(NUM_CORES);
        appContext.setResource(resource);

        ContainerLaunchContext amContainer = Records.newRecord(ContainerLaunchContext.class);
        ImmutableMap.Builder<String, LocalResource> resources = ImmutableMap.builder();
        resources.put("AppMaster.jar", getResource(new Path(jarPath), conf, LocalResourceType.FILE));
        addResources(resources);
        amContainer.setLocalResources(resources.build());
        amContainer.setEnvironment(env);
        amContainer.setCommands(getAMCommands());
        appContext.setAMContainerSpec(amContainer);
        return appContext;
    }

    protected void addResources(ImmutableMap.Builder<String, LocalResource> resources) throws IOException {
    }

    private List<String> getAMCommands() {
        String libJarValue = getConf().get("tmpjars", "");

        StringBuilder libjars = new StringBuilder();
        for (String jar : libJarValue.split(",")) {
            try {
                libjars.append(new File(new URI(jar)).getAbsolutePath()).append(':');
            } catch (URISyntaxException | IllegalArgumentException e) {
                LOG.info("Illegal jar path: {}", jar);
            }
        }
        return Collections.singletonList(
            String.format("${JAVA_HOME}/bin/java -Xmx10G -cp ./*:%s$CLASSPATH %s --logdir=%s 1>%s/stdout 2>%s/stderr",
                libjars, getAppMasterClass(), LOG_DIR_EXPANSION_VAR, LOG_DIR_EXPANSION_VAR, LOG_DIR_EXPANSION_VAR));
    }

    protected abstract String getAppMasterClass();

    protected static Path uploadTempFile(Path localPath, Configuration conf, String prefix, String extension)
            throws IOException {
        Path tmpDir = new Path(TEMP_FILES_PATH);
        FileSystem fs = tmpDir.getFileSystem(conf);
        fs.mkdirs(tmpDir);
        int rand = new Random().nextInt(Integer.MAX_VALUE);
        Path hdfsPath = new Path(tmpDir, String.format(Locale.ROOT, "%s%05d.%s", prefix, rand, extension));
        LOG.info("Uploading local file {} to HDFS path {}", localPath, hdfsPath);
        fs.copyFromLocalFile(localPath, hdfsPath);
        return hdfsPath;
    }

    protected static LocalResource getResource(Path path, Configuration conf, LocalResourceType resourceType)
            throws IOException {
        FileSystem fs = path.getFileSystem(conf);
        FileStatus jarStatus = fs.getFileStatus(path);
        LocalResource amJarRsrc = Records.newRecord(LocalResource.class);
        amJarRsrc.setType(resourceType);
        amJarRsrc.setVisibility(LocalResourceVisibility.APPLICATION);
        amJarRsrc.setResource(ConverterUtils.getYarnUrlFromPath(path));
        amJarRsrc.setTimestamp(jarStatus.getModificationTime());
        amJarRsrc.setSize(jarStatus.getLen());
        return amJarRsrc;
    }

    protected abstract String getJarPath();

    protected void configure(String[] args) {
    }

}
