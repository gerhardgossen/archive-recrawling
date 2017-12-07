package de.l3s.icrawl.crawler.analysis;

import java.io.IOException;

import de.l3s.icrawl.crawler.ArchiveCrawlSpecification;
import de.l3s.icrawl.crawler.analysis.ResourceAnalyser.WeightingMethod;

public interface ResourceAnalyserFactory {

    ResourceAnalyser get(ArchiveCrawlSpecification spec, WeightingMethod method) throws IOException;

}
