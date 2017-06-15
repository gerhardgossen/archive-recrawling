package de.l3s.gossen.contentanalysis;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.mapreduce.Mapper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.LineProcessor;
import com.google.common.io.Resources;

import de.l3s.gossen.util.DateUtils;
import de.l3s.gossen.util.WebPageUtils;

import static de.l3s.gossen.util.DateUtils.isValidDate;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.UNICODE_CHARACTER_CLASS;
import static java.util.stream.Collectors.maxBy;

public class WebPageDateExtractor {
    private static final Predicate<WebPageDate> VALID_DATE_PREDICATE = wpd -> DateUtils.isValidDate(wpd.getDate());
    private static final Logger logger = LoggerFactory.getLogger(WebPageDateExtractor.class);

    public enum DateSource {
        /** date is part of Web page URL */
        URL,
        /** date is contained in a {@code <time />} element */
        TIME,
        /** date is contained in a {@code <meta />} element */
        META,
        /** date was found in a paragraph containing a trigger word */
        TRIGGER_WORD,
        /** date was found in normal text content */
        TEXT_DATE,
        /** date was found in a HTTP header */
        HEADER,
        /** Modification date is unknown */
        NOT_FOUND
    }

    public static final class WebPageDate {
        private final ZonedDateTime date;
        private final DateSource dateSource;

        public WebPageDate(ZonedDateTime date, DateSource dateSource) {
            this.date = date;
            this.dateSource = dateSource;
        }

        public ZonedDateTime getDate() {
            return date;
        }

        public DateSource getDateSource() {
            return dateSource;
        }

        @Override
        public String toString() {
            return String.format("%s [%s]", date, dateSource);
        }
    }

    public static class ExtractionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ExtractionException(String message, Throwable cause) {
            super(message, cause);
        }

        public ExtractionException(String message) {
            super(message);
        }

        public ExtractionException(Throwable cause) {
            super(cause);
        }

    }

    @VisibleForTesting
    static final Pattern DATE_TRIGGERS = Pattern.compile(
        "created?|updated?|modified|last modifi|letzte? (ge|ver)?änder|publi(z|sh)",
        CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS);
    private static final Set<String> SKIPPED_ELEMENTS = ImmutableSet.of("script", "style", "pre");
    private static final Map<String, Integer> NAMES_TO_MONTH = namesMap();
    static final List<Pattern> DATE_PATTERNS = buildDatePattern();

    private static List<Pattern> buildDatePattern() {
        ImmutableList.Builder<Pattern> patterns = ImmutableList.builder();
        // YYYY-mm-dd
        patterns.add(Pattern.compile("(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})", UNICODE_CHARACTER_CLASS))
            // dd. mm. yyyy
            .add(Pattern.compile("(?<day>\\d{1,2})\\.\\s*(?<month>\\d{1,2})\\.\\s*(?<year>\\d{2,4})",
                UNICODE_CHARACTER_CLASS))
            // dd. mmm. yyyy [, hh:MM [Uhr]]
            .add(Pattern.compile(
                "(?<day>\\d{1,2})\\.?\\s*(?<month>\\w+)\\.?\\s+(?<year>\\d{2,4})(,\\s*(?<hour>\\d{1,2}):(?<minute>\\d{1,2})( Uhr)?)?",
                UNICODE_CHARACTER_CLASS))
            .add(Pattern.compile("(?<day>\\d{1,2})\\.?\\s*(?<month>\\w+)\\.?\\s+(?<year>\\d{2,4})",
                UNICODE_CHARACTER_CLASS))
            // mmm. dd.,? yyyy
            .add(
                Pattern.compile("(?<month>\\w+)\\s+(?<day>\\d{1,2})[\\.,]\\s*(?<year>\\d{4})", UNICODE_CHARACTER_CLASS))
            // mm/dd/yyyy
            .add(Pattern.compile("(?<month>\\d{1,2})/(?<day>\\d{1,2})/(?<year>\\d{4})", UNICODE_CHARACTER_CLASS))
            // dd/mm/yyyy
            .add(Pattern.compile("(?<day>\\d{1,2})/(?<month>\\d{1,2})/(?<year>\\d{4})", UNICODE_CHARACTER_CLASS))
            // yyyy-dd-mm
            .add(Pattern.compile("(?<year>\\\\d{4})-(?<day>\\d{1,2})-(?<month>\\d{1,2})/", UNICODE_CHARACTER_CLASS));
        return patterns.build();
    }

    private static Map<String, Integer> namesMap() {
        try {
            URL mappingsResource = Resources.getResource("de/l3s/icrawl/month_mappings.tsv");
            return Resources.readLines(mappingsResource, StandardCharsets.UTF_8,
                new LineProcessor<Map<String, Integer>>() {
                    private final ImmutableMap.Builder<String, Integer> namesBuilder = ImmutableMap.builder();

                    @Override
                    public boolean processLine(String line) throws IOException {
                        String[] split = line.split("\t", 2);
                        String key = split[0];
                        int value = Integer.parseInt(split[1]);
                        namesBuilder.put(key, value);
                        return true;
                    }

                    @Override
                    public Map<String, Integer> getResult() {
                        return namesBuilder.build();
                    }
                });
        } catch (IOException e) {
            logger.warn("Cannot initialize date extractor: ", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Extract the likely modification date from a parsed document.
     *
     * @param dom
     *            DOM tree of an HTML document
     * @return the modification date or null
     * @throws InterruptedException
     */
    public static WebPageDate extractModifiedDate(Document dom) throws InterruptedException {
        Map<Element, WebPageDate> candidates = findCandidateElements(dom);
        logger.trace("Found {} candidates: {}", candidates.size(), candidates);
        candidates = Maps.filterValues(candidates, VALID_DATE_PREDICATE);
        if (candidates.isEmpty()) {
            candidates = Maps.filterValues(findElementsWithDate(dom), VALID_DATE_PREDICATE);
        }
        return getBestDateMatch(candidates);
    }

    private static WebPageDate getBestDateMatch(Map<Element, WebPageDate> candidates) {
        Comparator<WebPageDate> comparator = (wpd1, wpd2) -> {
            int cmp = wpd1.getDateSource().compareTo(wpd2.getDateSource());
            if (cmp != 0) {
                return -cmp;
            }
            // prefer more complete dates (has time)
            boolean lt1Empty = wpd1.getDate().toLocalTime().equals(LocalTime.MIDNIGHT);
            boolean lt2Empty = wpd2.getDate().toLocalTime().equals(LocalTime.MIDNIGHT);
            if ((lt1Empty && lt2Empty) || (!lt1Empty && !lt2Empty)) {
                return wpd1.getDate().compareTo(wpd2.getDate());
            } else if (lt1Empty) {
                return -1;
            } else {
                return +1;
            }
        };
        return candidates.values().stream().collect(maxBy(comparator)).orElse(null);
    }

    private static Map<Element, WebPageDate> findElementsWithDate(Document dom) {
        Map<Element, WebPageDate> candidates = new LinkedHashMap<>();

        for (Node n : new TreeWalker(findDomRoot(dom), SKIPPED_ELEMENTS)) {
            if (n instanceof TextNode) {
                ZonedDateTime dateTime = findDateMatch(((TextNode) n).text());
                if (dateTime != null) {
                    Element element = WebPageUtils.findParagraphParent(n, -1);
                    candidates.put(element, new WebPageDate(dateTime, DateSource.TEXT_DATE));
                }
            }
        }
        return candidates;
    }

    private static Map<Element, WebPageDate> findCandidateElements(Document dom) throws InterruptedException {
        Map<Element, WebPageDate> candidates = new LinkedHashMap<>();
        for (Element element : dom.getElementsByTag("time")) {
            ZonedDateTime date = getTimeElementDate(element);
            if (date != null) {
                candidates.put(element, new WebPageDate(date, DateSource.TIME));
            }
        }
        for (Element element : dom.getElementsByTag("meta")) {
            ZonedDateTime date = getMetaElementDate(element);
            if (date != null) {
                candidates.put(element, new WebPageDate(date, DateSource.META));
            }

        }
        for (Node n : new TreeWalker(findDomRoot(dom), SKIPPED_ELEMENTS)) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            if (n instanceof TextNode && DATE_TRIGGERS.matcher(((TextNode) n).text()).find()) {
                extractDateFromTextNode((TextNode) n, candidates);
            }
        }
        return candidates;
    }

    private static void extractDateFromTextNode(TextNode n, Map<Element, WebPageDate> candidates) {
        Element element = WebPageUtils.findParagraphParent(n, -1);
        ZonedDateTime dateTime = findDateMatch(element.text());
        if (dateTime != null) {
            candidates.put(element, new WebPageDate(dateTime, DateSource.TRIGGER_WORD));
        }
    }

    static Node findDomRoot(Document dom) {
        Node root = dom.body();
        if (root == null) {
            root = dom.ownerDocument();
        }
        return root;
    }

    private static ZonedDateTime getMetaElementDate(Element element) {
        for (String name : DateUtils.META_ATTRIBUTE_NAMES) {
            String value = element.attr(name);
            if (value != null && DateUtils.dateMetaKey(value)) {
                ZonedDateTime parsedDate = DateUtils.liberalParseDate(element.attr("content"));
                if (parsedDate != null) {
                    return parsedDate;
                }
            }
        }
        return null;
    }

    /** Get date from a <time> element. */
    private static ZonedDateTime getTimeElementDate(Element element) {
        if (element.hasAttr("datetime")) {
            return DateUtils.liberalParseDate(element.attr("datetime"));
        } else {
            logger.trace("Expected attribte 'datetime' on element '{}'", element);
            return null;
        }
    }

    public static WebPageDate getModifiedDate(String url, Document document, Long httpModifiedTime,
            Mapper<?, ?, ?, ?>.Context context) throws InterruptedException {
        LocalDate urlDate = DateUtils.extractDateFromUrl(url);
        if (urlDate != null && isValidDate(urlDate.atStartOfDay().atZone(ZoneOffset.UTC))) {
            incrementCount(context, DateSource.URL);
            return new WebPageDate(urlDate.atStartOfDay().atZone(ZoneOffset.UTC), DateSource.URL);
        }

        WebPageDate contentDate = extractModifiedDate(document);
        if (contentDate != null && isValidDate(contentDate.getDate())) {
            incrementCount(context, contentDate.getDateSource());
            return contentDate;
        }

        if (httpModifiedTime != null) {
            ZonedDateTime httpDateTime = Instant.ofEpochMilli(httpModifiedTime).atZone(ZoneOffset.UTC);
            if (isValidDate(httpDateTime)) {
                incrementCount(context, DateSource.HEADER);
                return new WebPageDate(httpDateTime, DateSource.HEADER);
            }
        }
        logger.debug("No date found for URL {}", url);
        return null;
    }

    private static void incrementCount(Mapper<?, ?, ?, ?>.Context context, Enum<?> counter) {
        if (context != null) {
            context.getCounter(counter).increment(1L);
        }
    }

    @VisibleForTesting
    static ZonedDateTime findDateMatch(String s) {
        for (Pattern pattern : DATE_PATTERNS) {
            Matcher matcher = pattern.matcher(s);
            if (matcher.find()) {
                int year = Integer.parseInt(matcher.group("year"));
                if (year < 15) {
                    year += 2000;
                } else if (year < 100) {
                    year += 1900;
                }
                String rawMonth = matcher.group("month");
                int month = -1;
                Integer monthLookup = NAMES_TO_MONTH.get(rawMonth.toLowerCase(Locale.ENGLISH));
                if (monthLookup != null) {
                    month = monthLookup.intValue();
                } else if (rawMonth.matches("\\d{1,2}")) {
                    month = Integer.parseInt(rawMonth);
                } else {
                    continue;
                }
                int day = Integer.parseInt(matcher.group("day"));
                int hour = 0;
                int minute = 0;
                if (matcher.groupCount() > 3 && matcher.group("hour") != null && matcher.group("minute") != null) {
                    hour = Integer.parseInt(matcher.group("hour"));
                    minute = Integer.parseInt(matcher.group("minute"));
                }
                try {
                    return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC);
                } catch (DateTimeException e) {
                    logger.trace("Could not use as a date: {}-{}-{}: ", year, month, day, e);
                }
            }
        }
        return null;
    }
}
