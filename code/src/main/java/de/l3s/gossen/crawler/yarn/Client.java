package de.l3s.gossen.crawler.yarn;

import java.io.IOException;
import java.util.Optional;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import de.l3s.gossen.crawler.Crawler;
import de.l3s.gossen.crawler.analysis.ResourceAnalyser.WeightingMethod;
import de.l3s.gossen.crawler.io.ResultStorer;

public class Client extends BaseClient {
    private Path hdfsSpecPath;
    private String hdfsJarPath;
    private String localSpecFile;
    private String outputDirectory;
    private Optional<String> numUrls;
    private Optional<String> weightingMethod;
    private Optional<String> relevanceThreshold;
    private Optional<String> snapshotsToAnalyze;

    public static void main(String[] args) throws Exception {
        ToolRunner.run(new Client(), args);
    }

    @Override
    protected void configure(String[] args) {
        if (!checkUsage(args)) {
            throw new IllegalArgumentException();
        }
        this.hdfsJarPath = args[0];
        this.localSpecFile = args[1];
        this.outputDirectory = args[2];
        this.numUrls = args.length > 3 ? Optional.of(args[3]) : Optional.empty();
        this.weightingMethod = args.length > 4 ? Optional.of(args[4]) : Optional.empty();
        weightingMethod.ifPresent(
            wm -> Preconditions.checkArgument(WeightingMethod.valueOf(wm) != null, "Unknown weighting method %s", wm));
        this.relevanceThreshold = args.length > 5 ? Optional.of(args[5]) : Optional.empty();
        this.snapshotsToAnalyze = args.length > 6 ? Optional.of(args[6]) : Optional.empty();
    }

    @Override
    protected void cleanup() throws IOException {
        hdfsSpecPath.getFileSystem(getConf()).delete(hdfsSpecPath, false);
    }

    @Override
    protected void prepare() throws IOException {
        hdfsSpecPath = uploadTempFile(new Path(localSpecFile), getConf(), "spec-", "json");
    }

    @Override
    protected boolean checkUsage(String[] args) {
        if (args.length >= 3) {
            return true;
        } else {
            System.err.println("Usage: java " + Client.class.getName()
                    + " hdfsJarPath localSpecFile outputDirectory [numUrls [weightingMethod [relevanceThreshold [snapshotsToAnalyze]]");
            return false;
        }
    }

    @Override
    protected void addParameters(ImmutableMap.Builder<String, String> env) {
        env.put(ResultStorer.OUTPUT_DIRECTORY, outputDirectory);

        numUrls.ifPresent(num -> env.put(Crawler.NUM_URLS, num));
        weightingMethod.ifPresent(wm -> env.put(Crawler.WEIGHTING_METHOD, wm));
        relevanceThreshold.ifPresent(rt -> env.put(Crawler.RELEVANCE_THRESHOLD, rt));
        snapshotsToAnalyze.ifPresent(count -> env.put(Crawler.SNAPSHOTS_TO_ANALYZE, count));
    }

    @Override
    protected void addResources(ImmutableMap.Builder<String, LocalResource> resources) throws IOException {
        resources.put("spec.json", getResource(hdfsSpecPath, getConf(), LocalResourceType.FILE));
    }

    @Override
    protected String getAppMasterClass() {
        return AppMaster.class.getName();
    }

    @Override
    protected String getJarPath() {
        return hdfsJarPath;
    }

}
