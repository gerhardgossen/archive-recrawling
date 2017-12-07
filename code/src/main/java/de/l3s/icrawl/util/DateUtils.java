package de.l3s.icrawl.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import static java.util.Collections.singletonMap;

public class DateUtils {

    private static final ImmutableMap<String, Integer> MONTH_MAPPINGS = ImmutableMap.<String, Integer> builder()
        .put("jan", 1)
        .put("feb", 2)
        .put("mar", 3)
        .put("apr", 4)
        .put("may", 5)
        .put("jun", 6)
        .put("jul", 7)
        .put("aug", 8)
        .put("sep", 9)
        .put("oct", 10)
        .put("nov", 11)
        .put("dec", 12)
        .put("january", 1)
        .put("february", 2)
        .put("march", 3)
        .put("april", 4)
        .put("june", 6)
        .put("july", 7)
        .put("august", 8)
        .put("september", 9)
        .put("october", 10)
        .put("november", 11)
        .put("december", 12)
        .build();

    private static final Set<ZoneId> PREFERRED_ZONES = ImmutableSet.of(
        // British summer time
        ZoneId.of("Europe/London", singletonMap("BST", "Europe/London")), ZoneId.of("Europe/Berlin", ImmutableMap.of(
            // Central European Time
            "CET", "Europe/Berlin",
            // Mitteleuropäische Zeit (german name for CET)
            "MEZ", "Europe/Berlin")));
    private static final Set<String> META_DATE_KEYS = ImmutableSet.of("article:published_time", "datepublished",
        "og:updated_time", "last-modified", "article:modified_time", "date", "datemodified", "pubdate",
        "og:modified_time", "lastmod", "dc.date", "modified");

    public static final List<String> META_ATTRIBUTE_NAMES = ImmutableList.of("name", "http-equiv", "property",
        "itemprop");

    private static final int FIRST_YEAR = 1990;
    // TODO vary based on document locale
    private static final List<DateTimeFormatter> FORMATS = ImmutableList.of(DateTimeFormatter.ISO_DATE_TIME,
        withTimeZoneShortName(DateTimeFormatter.ISO_DATE_TIME),
        withTimeZoneShortName(DateTimeFormatter.ofPattern("y-M-d' 'H:m:s ")),
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.getDefault()),
        withTimeZoneShortName(DateTimeFormatter.ofPattern("EEE, d MMM y H:m:s ")).withLocale(Locale.ENGLISH),
        DateTimeFormatter.ofPattern("E, d MMM y H:m:s Z").withLocale(Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMM dd, YYYY h:m a").withLocale(Locale.ENGLISH),
        DateTimeFormatter.ofPattern("YY/d/M"), DateTimeFormatter.ofPattern("YY/M/d"),
        DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss"), DateTimeFormatter.ofPattern("MM/dd/YYYY"),
        DateTimeFormatter.ofPattern("MMM. dd, YYYY, hh:mm a").withLocale(Locale.ENGLISH),
        withTimeZoneShortName(DateTimeFormatter.ofPattern("MM/dd/YYYY HH:mm:ss ")),
        withTimeZoneShortName(DateTimeFormatter.ofPattern("EEE, MMM d, YYYY H:m ")).withLocale(Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM y").withLocale(Locale.forLanguageTag("ru")),
        withTimeZoneShortName(DateTimeFormatter.ofPattern("y-M-d'@'H:m:s ")),
        DateTimeFormatter.ofPattern("YYYY-MM-dd hh:mm:ss a Z"), DateTimeFormatter.ofPattern("yyyy-M-d'T'HH:mm:ssXXX"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxx"), DateTimeFormatter.ISO_DATE,
        DateTimeFormatter.ofPattern("YYYYMMdd"), DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("ccc, dd LLL yyyy HH:mm:ss zZZ", Locale.GERMAN));

    private static final int LAST_YEAR = Calendar.getInstance().get(Calendar.YEAR);
    private static final Logger logger = LoggerFactory.getLogger(DateUtils.class);
    private static final Splitter URL_SEGMENT_SPLITTER = Splitter.onPattern("[-/_.]").omitEmptyStrings();
    public static final ZonedDateTime CUTOFF_DATE = ZonedDateTime.of(1990, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    static class DateParse {
        int year = -1;
        int month = -1;
        int day = -1;
        boolean canOverrideDay = false;
        boolean canOverrideMonth = false;

        void tryParseFullDate(String segment) {
            if (year < 0) {
                int yearCandidate = Integer.parseInt(segment.substring(0, 4));
                if (FIRST_YEAR <= yearCandidate && yearCandidate <= yearCandidate) {
                    year = yearCandidate;
                    month = Integer.parseInt(segment.substring(4, 6));
                    day = Integer.parseInt(segment.substring(6));
                } else {
                    logger.debug("Ignoring possible date '{}' because it is out of the expected date range", segment);
                }
            }
        }

        void tryParseYear(String segment) {
            int candidate = Integer.parseInt(segment);
            if (DateUtils.FIRST_YEAR <= candidate && candidate <= DateUtils.LAST_YEAR) {
                year = candidate;
            }
        }

        void tryParseMonthDay(String fullString, String segment) {
            int candidate = Integer.parseInt(segment);
            if (candidate > 12 && candidate <= 31 && day < 0) {
                day = candidate;
            } else if (candidate > 0) {
                tryAssignMonthOrDay(candidate);
            } else {
                logger.trace("Could not decide on '{}' in '{}'", candidate, fullString);
            }
        }

        private void tryAssignMonthOrDay(int candidate) {
            if (month >= 0 && canOverrideMonth) {
                month = candidate;
                day = candidate;
                canOverrideDay = true;
                canOverrideMonth = false;
            } else if (month >= 0) {
                if (day < 0 || canOverrideDay) {
                    day = candidate;
                    canOverrideDay = day == month;
                }
            } else {
                if (month < 0 || canOverrideMonth) {
                    month = candidate;
                    canOverrideMonth = year < 0;
                }
            }
        }

        public LocalDate toLocalDate() {
            return LocalDate.of(year, month, day);
        }

        @Override
        public String toString() {
            return String.format("%4d-%2d-%2d", year, month, day);
        }

        void tryParseFullMonth(String segment) {
            if (month < 0) {
                month = DateUtils.MONTH_MAPPINGS.get(segment.toLowerCase(Locale.ENGLISH));
            }
        }

        boolean isFullySpecified() {
            return year > 0 && month > 0 && day > 0;
        }

        private void tryParseDigitsSegment(String path, String segment) {
            if (segment.length() == 8) {
                tryParseFullDate(segment);
            } else if (segment.length() == 4) {
                tryParseYear(segment);
            } else if (segment.length() == 2 || segment.length() == 1) {
                tryParseMonthDay(path, segment);
            }
        }

    }

    private DateUtils() {
        // prevent instantiation
    }

    public static LocalDate extractDate(String path) {
        final CharMatcher charMatcher = CharMatcher.inRange('0', '9');
        DateParse parse = new DateParse();
        for (String segment : URL_SEGMENT_SPLITTER.split(path)) {
            if (charMatcher.matchesAllOf(segment)) {
                parse.tryParseDigitsSegment(path, segment);
            } else if (MONTH_MAPPINGS.containsKey(segment.toLowerCase(Locale.ENGLISH))) {
                parse.tryParseFullMonth(segment);
            }
        }
        if (parse.isFullySpecified()) {
            try {
                return parse.toLocalDate();
            } catch (IllegalArgumentException e) {
                logger.trace("Not a valid date {} in '{}'", parse, path);
            }
        }
        return null;
    }

    public static LocalDate extractDateFromUrl(String url) {
        try {
            return extractDate(new URL(url).getPath());
        } catch (MalformedURLException e) {
            logger.debug("Malformed URL  '{}'", url);
            return null;
        }
    }

    /**
     * Try very hard to parse the parameter as a date.
     *
     * @return the parsed date or null if the format is unknown
     */
    public static ZonedDateTime liberalParseDate(String dateCandidate) {
        if (dateCandidate == null || dateCandidate.isEmpty()) {
            return null;
        }
        String normalized = normalizeHalfDay(dateCandidate);
        for (DateTimeFormatter formatter : FORMATS) {
            try {
                TemporalAccessor parsed = formatter.parseBest(normalized, ZonedDateTime::from, LocalDateTime::from,
                    LocalDate::from);
                logger.trace("'{}' parsed to {}", dateCandidate, parsed);
                ZonedDateTime date;
                if (parsed instanceof ZonedDateTime) {
                    date = (ZonedDateTime) parsed;
                } else if (parsed instanceof LocalDateTime) {
                    date = ((LocalDateTime) parsed).atZone(ZoneOffset.UTC);
                } else if (parsed instanceof LocalDate) {
                    date = ((LocalDate) parsed).atStartOfDay(ZoneOffset.UTC);
                } else {
                    throw new IllegalStateException();
                }
                if (date.isBefore(CUTOFF_DATE) || date.isAfter(ZonedDateTime.now())) {
                    throw new IllegalArgumentException("Date out of range: " + parsed);
                }
                return date;
            } catch (DateTimeParseException e) {
                logger.trace("Could not parse date: {}: {}", dateCandidate, e.getMessage());
            }
        }
        return null;

    }

    private static String normalizeHalfDay(String dateCandidate) {
        String normalized = dateCandidate.replaceAll("[aA]\\.?\\s*[mM]\\.?", "AM").replaceAll("[pP]\\.?\\s*[mM]\\.?",
            "PM");
        if (!dateCandidate.equals(normalized)) {
            logger.debug("normalized '{}' to '{}'", dateCandidate, normalized);
        }
        return normalized;
    }

    private static DateTimeFormatter withTimeZoneShortName(DateTimeFormatter formatter) {
        return new DateTimeFormatterBuilder().append(formatter)
            .appendZoneText(TextStyle.SHORT, PREFERRED_ZONES)
            .toFormatter();
    }

    public static boolean dateMetaKey(String key) {
        if (key == null) {
            return false;
        } else if (META_DATE_KEYS.contains(key.toLowerCase(Locale.ENGLISH))) {
            return true;
        }
        return false;
    }

    public static boolean isValidDate(ZonedDateTime date) {
        return date.isAfter(CUTOFF_DATE) && date.isBefore(ZonedDateTime.now());
    }
}
