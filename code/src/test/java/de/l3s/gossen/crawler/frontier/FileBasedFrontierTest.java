package de.l3s.gossen.crawler.frontier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Optional;

import org.junit.Test;

import com.codahale.metrics.MetricRegistry;

import de.l3s.gossen.crawler.CrawlUrl;
import de.l3s.gossen.crawler.TestUtils;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class FileBasedFrontierTest {

    @Test
    public void testRoundtrip() throws IOException {
        String URL = "http://example.org/";
        File queueDirectory = Files.createTempDirectory("queue-").toFile();
        try (FileBasedFrontier frontier = new FileBasedFrontier(queueDirectory, new MetricRegistry(), 2, false)) {
            frontier.push(Collections.singleton(CrawlUrl.fromSeed(URL, 0.0f)));
            Optional<CrawlUrl> head = frontier.pop();
            assertThat(head, is(TestUtils.present()));
            assertThat(head.get().getUrl(), is(URL));
        }
    }


}
