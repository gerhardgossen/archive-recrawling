package de.l3s.gossen.crawler.ui;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.apache.commons.io.filefilter.FileFilterUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.embedded.EmbeddedWebApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;

import de.l3s.gossen.crawler.ArchiveCrawlSpecification;
import de.l3s.gossen.crawler.Crawler;

import static org.apache.commons.io.filefilter.FileFileFilter.FILE;
import static org.apache.commons.io.filefilter.HiddenFileFilter.VISIBLE;

@Controller
public class InfoController {
    @Inject
    MetricRegistry metrics;

    @Inject
    Crawler crawler;

    @Inject
    EmbeddedWebApplicationContext context;

    @Inject
    @Value("${basePath}")
    String basePath;

    @Inject
    @Value("${logdir}")
    File logsDir;

    @RequestMapping("/")
    public ModelAndView status() {
        Optional<ArchiveCrawlSpecification> spec = crawler.getCurrentSpec();
        String crawlName = spec.isPresent() ? spec.get().getName() : "no crawl";
        return new ModelAndView("status").addObject("basePath", basePath)
            .addObject("counters", metricHierarchy(metrics.getCounters()))
            .addObject("gauges", metricHierarchy(metrics.getGauges()))
            .addObject("histograms", metricHierarchy(metrics.getHistograms()))
            .addObject("meters", metricHierarchy(metrics.getMeters()))
            .addObject("timers", metricHierarchy(metrics.getTimers()))
            .addObject("crawlName", crawlName);
    }

    private static final Pattern CLASS_BASED_METRIC = Pattern.compile("([a-z.]+\\.[A-Z][a-zA-Z]+)\\.(.*)");

    static <T extends Metric> SortedMap<String, SortedMap<String, T>> metricHierarchy(SortedMap<String, T> metrics) {
        SortedMap<String, SortedMap<String, T>> result = new TreeMap<>();
        for (Entry<String, T> entry : metrics.entrySet()) {
            String group;
            String key;
            String originalKey = entry.getKey();
            Matcher m = CLASS_BASED_METRIC.matcher(originalKey);
            if (m.matches()) {
                group = m.group(1);
                key = m.group(2);
            } else {
                int seperatorPos = originalKey.lastIndexOf(".");
                if (seperatorPos >= 0) {
                    group = originalKey.substring(0, seperatorPos);
                    key = originalKey.substring(seperatorPos + 1);
                } else {
                    group = "";
                    key = originalKey;
                }
            }
            SortedMap<String, T> groupMap = result.getOrDefault(group, new TreeMap<>());
            groupMap.put(key, entry.getValue());
            result.put(group, groupMap);
        }
        return result;
    }

    @RequestMapping("env")
    public ModelAndView environment() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threads.dumpAllThreads(true, true);
        return new ModelAndView("env").addObject("basePath", basePath)
            .addObject("headers", System.getenv())
            .addObject("threads", threadInfos);
    }

    @RequestMapping("logs/")
    public ModelAndView list() {
        File[] files = logsDir.listFiles((FileFilter) FileFilterUtils.and(FILE, VISIBLE));
        Arrays.sort(files);
        return new ModelAndView("logs").addObject("basePath", basePath).addObject("files", files).addObject("logsDir",
            logsDir);
    }

    @RequestMapping("logs/{path}")
    @ResponseBody
    public HttpEntity<?> show(@PathVariable String path) throws IOException {
        File file = new File(logsDir, path);
        if (!file.exists()) {
            throw new UiConfig.ResourceNotFoundException(path);
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(new FileSystemResource(file));
    }

    @RequestMapping(value = "/stop", method = { RequestMethod.GET, RequestMethod.POST })
    public View stop() {
        crawler.shutdown();
        context.getEmbeddedServletContainer().stop();
        context.stop();
        return new RedirectView(basePath);
    }
}
