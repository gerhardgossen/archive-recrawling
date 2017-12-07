package de.l3s.icrawl.contentanalysis;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.hadoop.mapreduce.Mapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

import de.l3s.icrawl.contentanalysis.WebPageDateExtractor.DateSource;
import de.l3s.icrawl.contentanalysis.WebPageDateExtractor.WebPageDate;

import static de.l3s.icrawl.contentanalysis.WebPageDateExtractor.getModifiedDate;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.mock;

public class WebPageDateExtractorEvaluation {
    private static final Logger logger = LoggerFactory.getLogger(WebPageDateExtractorEvaluation.class);
    private static int correct = 0;
    private static int wrong = 0;
    private static Period error = Period.ZERO;
    private static int errorCount = 0;
    private static List<String> errorComments = new ArrayList<>();
    private static Map<String, Period> errorDurations = new HashMap<>();

    public static void main(String[] args) throws IOException, InterruptedException {
        //        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        File baseDir = new File("/home/gerhard/Devel/java/archive-crawler/date-test");
        DateTimeFormatter dateParser = new DateTimeFormatterBuilder().appendValue(ChronoField.YEAR)
            .optionalStart()
            .appendLiteral('-')
            .append(DateTimeFormatter.ofPattern("MM-dd"))
            .optionalStart()
            .appendLiteral('T')
            .append(DateTimeFormatter.ISO_TIME)
            .optionalEnd()
            .optionalEnd()
            .toFormatter();

        for (String labelLine : Files.readLines(new File("dates-gold-firstHalf.tsv"), UTF_8)) {
            String[] parts = labelLine.split("\t", 5);
            if ("filename".equals(parts[0])) {
                // headers
                continue;
            }
            if (parts.length < 4 || parts[3].trim().isEmpty()) {
                // no label
                continue;
            }
            if ("TODO".equalsIgnoreCase(parts[3])) {
                logger.info("TODO label for '{}'", parts[1]);
                continue;
            }
            String file = parts[0];
            String url = parts[1];
            LocalDateTime crawlTime = LocalDateTime.parse(parts[2]);
            Optional<TemporalAccessor> correctDate = parts[3].startsWith("?") ? Optional.empty()
                    : Optional.of(dateParser.parseBest(parts[3], OffsetDateTime::from, LocalDateTime::from,
                        LocalDate::from, Year::from));
            String comment = parts[4];

            Mapper<?, ?, ?, ?>.Context context = mock(Mapper.Context.class, RETURNS_MOCKS);
            Document doc = Jsoup.parse(new File(baseDir, file), null);
            WebPageDate modifiedDate = getModifiedDate(url, doc, crawlTime.toEpochSecond(ZoneOffset.UTC) * 1000,
                context);

            if (modifiedDate == null || modifiedDate.getDateSource() == DateSource.HEADER) {
                report(!correctDate.isPresent(), url, null, correctDate.orElse(null), comment);
            } else {
                if (correctDate.isPresent()) {
                    TemporalAccessor d = correctDate.get();
                    if (d instanceof OffsetDateTime) {
                        LocalDate correctLocalDate = ((OffsetDateTime) d).toLocalDate();
                        reportDifference(url, modifiedDate, d, correctLocalDate, comment);
                    } else if (d instanceof LocalDateTime) {
                        LocalDate correctLocalDate = ((LocalDateTime) d).toLocalDate();
                        reportDifference(url, modifiedDate, d, correctLocalDate, comment);
                    } else if (d instanceof LocalDate) {
                        reportDifference(url, modifiedDate, d, (LocalDate) d, comment);
                    } else {
                        report(((Year) d).getValue() == modifiedDate.getDate().getYear(), url, modifiedDate.getDate(),
                            d, comment);
                    }
                } else {
                    reportError(url, modifiedDate.getDate(), null, comment);
                }
            }
        }
        long totalDays = error.toTotalMonths() * 30 + error.getDays();
        System.out.printf(Locale.ENGLISH, "Accurracy: %.2f%n", (correct / (double) (correct + wrong)));
        System.out.printf("Avg error: %s (%d values)%n", Period.ofDays((int) (totalDays / errorCount)).normalized(),
            errorCount);
        System.out.println("Errors: " + errorComments);
        System.out.println("Error durations: "
                + errorDurations.entrySet().stream().filter(e -> e.getValue().getMonths() > 0).collect(toList()));
    }

    private static void reportDifference(String url, WebPageDate extracted, TemporalAccessor expected,
            LocalDate correctLocalDate, String comment) {
        Period period = Period.between(correctLocalDate, extracted.getDate().toLocalDate());
        if (period.isNegative()) {
            period = period.negated();
        }
        report(period.isZero(), url, extracted.getDate(), expected, comment);
        if (!period.isZero()) {
            System.out.println(extracted.getDateSource());
            error = error.plus(period);
            errorCount++;
            errorDurations.put(url, period);
        }
    }

    private static void report(boolean test, String url, ZonedDateTime actual, TemporalAccessor expected,
            String comment) {
        if (test) {
            reportCorrect(url);
        } else {
            reportError(url, actual, expected, comment);
        }
    }

    private static void reportCorrect(String url) {
        correct++;
        System.out.printf("CORRECT\t%50s%n", url);
    }

    private static void reportError(String url, ZonedDateTime actual, TemporalAccessor expected, String comment) {
        wrong++;
        System.out.printf("ERROR\t%50s\t%s\t%s%n", url, actual, expected);

        if (!comment.trim().isEmpty()) {
            errorComments.add(comment);
        }
    }
}
