package de.l3s.gossen.crawler.analysis;

import java.io.IOException;

import de.l3s.gossen.crawler.ArchiveCrawlSpecification;
import de.l3s.gossen.crawler.analysis.ResourceAnalyser.WeightingMethod;

public interface ResourceAnalyserFactory {

    ResourceAnalyser get(ArchiveCrawlSpecification spec, WeightingMethod method) throws IOException;

}
