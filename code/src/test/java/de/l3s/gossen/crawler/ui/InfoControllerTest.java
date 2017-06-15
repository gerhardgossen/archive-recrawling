package de.l3s.gossen.crawler.ui;

import java.util.SortedMap;

import org.junit.Test;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;

import static com.codahale.metrics.MetricRegistry.name;
import static de.l3s.gossen.crawler.ui.InfoController.metricHierarchy;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;

public class InfoControllerTest {

    @Test
    public void testMetricHierarchy() {
        MetricRegistry metrics = new MetricRegistry();
        metrics.counter(name(getClass(), "foo"));
        metrics.counter(name(getClass(), "bar"));
        metrics.counter("group.foo");
        metrics.counter("group.bar");
        metrics.counter("foo");
        metrics.counter("bar");

        SortedMap<String, SortedMap<String, Counter>> groupedCounters = metricHierarchy(metrics.getCounters());

        assertThat(groupedCounters.keySet(), contains("", "de.l3s.gossen.crawler.ui.InfoControllerTest", "group"));
        for (String groupName : asList("", "de.l3s.gossen.crawler.ui.InfoControllerTest", "group")) {
            assertThat(groupName, groupedCounters.get(groupName).keySet(), contains("bar", "foo"));
        }
    }

}
