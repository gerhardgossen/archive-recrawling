package de.l3s.icrawl.crawler.frontier;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;

final class ClassMetricFilter implements MetricFilter {
    private final String className;

    ClassMetricFilter(Class<?> clazz) {
        this.className = clazz.getName();
    }

    @Override
    public boolean matches(String name, Metric metric) {
        return name.startsWith(className);
    }
}