package de.l3s.gossen.crawler.yarn;


import java.io.IOException;
import java.util.Optional;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;

import com.google.common.collect.ImmutableMap;

import de.l3s.gossen.crawler.Crawler;
import de.l3s.gossen.crawler.io.ResultStorer;

public class EvaluationClient extends BaseClient {
    private Path hdfsSpecPath;
    private String hdfsJarPath;
    private String localSpecArchive;
    private String outputDir;
    private Optional<String> numUrls;

    public static void main(String[] args) throws Exception {
        ToolRunner.run(new EvaluationClient(), args);
    }

    @Override
    protected void configure(String[] args) {
        if (!checkUsage(args)) {
            throw new IllegalArgumentException();
        }
        this.hdfsJarPath = args[0];
        this.localSpecArchive = args[1];
        this.outputDir = args[2];
        this.numUrls = args.length > 3 ? Optional.of(args[3]) : Optional.empty();
    }

    @Override
    protected void cleanup() throws IOException {
        hdfsSpecPath.getFileSystem(getConf()).delete(hdfsSpecPath, false);
    }

    @Override
    protected void prepare() throws IOException {
        hdfsSpecPath = uploadTempFile(new Path(localSpecArchive), getConf(), "specs-", "zip");
    }

    @Override
    protected boolean checkUsage(String[] args) {
        if (args.length >= 3) {
            return true;
        } else {
            System.err.println("Usage: java " + EvaluationClient.class.getName()
                    + " hdfsJarPath localSpecArchive outputDir [numUrls]");
            return false;
        }
    }

    @Override
    protected void addParameters(ImmutableMap.Builder<String, String> env) {
        env.put(ResultStorer.OUTPUT_DIRECTORY, outputDir);

        numUrls.ifPresent(num -> env.put(Crawler.NUM_URLS, num));
    }

    @Override
    protected void addResources(ImmutableMap.Builder<String, LocalResource> resources)
            throws IOException {
        resources.put("specs", getResource(hdfsSpecPath, getConf(), LocalResourceType.ARCHIVE));
    }

    @Override
    protected String getAppMasterClass() {
        return EvaluationAppMaster.class.getName();
    }

    @Override
    protected String getJarPath() {
        return hdfsJarPath;
    }

}
