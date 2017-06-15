package de.l3s.gossen.crawler;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Doubles;

import de.l3s.gossen.snapshots.SnaphotLocation;

public class TimeSpecification implements Comparator<SnaphotLocation> {
    private static final double DEFAULT_SHAPE = 1.0;
    private static final double LOG_OF_2 = Math.log(2);
    private static final long MILLIS_PER_DAY = Duration.ofDays(1).toMillis();
    private final ZonedDateTime start;
    private final ZonedDateTime end;
    @JsonProperty
    private final Period beforeFuzziness;
    private final long beforeFuzzinessDuration;
    @JsonProperty
    private final Period afterFuzziness;
    private final long afterFuzzinessDuration;

    @JsonCreator
    TimeSpecification(@JsonProperty("start") @JsonDeserialize(using = LocalDateDeserializer.class) LocalDate start,
            @JsonProperty("end") @JsonDeserialize(using = LocalDateDeserializer.class) LocalDate end,
            @JsonProperty("beforeFuzziness") Period beforeFuzziness,
            @JsonProperty("afterFuzziness") Period afterFuzziness) {
        this.start = Objects.requireNonNull(start).atStartOfDay(ZoneOffset.UTC);
        this.end = Objects.requireNonNull(end).atStartOfDay(ZoneOffset.UTC);
        this.beforeFuzziness = Objects.requireNonNull(beforeFuzziness);
        this.afterFuzziness = Objects.requireNonNull(afterFuzziness);
        Instant now = Instant.now();
        this.beforeFuzzinessDuration = ChronoUnit.MILLIS.between(now, now.plus(beforeFuzziness));
        this.afterFuzzinessDuration = ChronoUnit.MILLIS.between(now, now.plus(afterFuzziness));
    }

    public static TimeSpecification afterDate(LocalDate start, Period afterFuzziness) {
        return new TimeSpecification(start, start, Period.ZERO, afterFuzziness);
    }

    public static TimeSpecification interval(LocalDate start, LocalDate end, Period fuzziness) {
        return new TimeSpecification(start, end, fuzziness, fuzziness);
    }

    public static TimeSpecification interval(LocalDate start, LocalDate end, Period beforeFuzziness,
            Period afterFuzziness) {
        return new TimeSpecification(start, end, beforeFuzziness, afterFuzziness);
    }

    public double getRelevance(ZonedDateTime t) {
        if (start.isBefore(t) && end.isAfter(t)) {
            return 1.0;
        } else if (start.isAfter(t)) {
            long diff = Duration.between(t, start).toMillis();
            return weibull(diff, beforeFuzzinessDuration, DEFAULT_SHAPE);
        } else {
            long diff = Duration.between(end.plusDays(1), t).toMillis();
            return weibull(diff, afterFuzzinessDuration, DEFAULT_SHAPE);
        }
    }

    public double getRelevanceExp(ZonedDateTime t) {
        if (start.isBefore(t) && end.isAfter(t)) {
            return 1.0;
        } else if (start.isAfter(t)) {
            long diff = Duration.between(t, start).toMillis();
            return exp(diff);
        } else {
            long diff = Duration.between(end.plusDays(1), t).toMillis();
            return exp(diff);
        }
    }

    private double exp(long diff) {
        return Math.exp(-diff / (2 * MILLIS_PER_DAY));
    }

    private double weibull(long t, double halfDecay, double shape) {
        // exp( -(t/L)^k * log(2) ), with L = half decay and k = shape
        double scaled = t / halfDecay;
        double shaped = -Math.pow(scaled, shape);
        return Math.exp(shaped * LOG_OF_2);
    }

    public Optional<SnaphotLocation> findBest(Iterable<SnaphotLocation> locations) {
        if (Iterables.isEmpty(locations)) {
            return Optional.empty();
        }
        return Optional.ofNullable(Ordering.from(this).reverse().min(locations));
    }

    public Iterable<SnaphotLocation> findBest(Iterable<SnaphotLocation> locations, int maxResults) {
        if (Iterables.size(locations) < maxResults) {
            return locations;
        }

        return Ordering.from(this).reverse().leastOf(locations, maxResults);
    }

    @Override
    public int compare(SnaphotLocation a, SnaphotLocation b) {
        if (a.getCrawlTime() == null && b.getCrawlTime() == null) {
            return 0;
        } else if (a.getCrawlTime() == null) {
            return -1;
        } else if (b.getCrawlTime() == null) {
            return 1;
        }
        return Doubles.compare(getRelevance(b.getCrawlTime()), getRelevance(a.getCrawlTime()));
    }

    public boolean contains(ZonedDateTime t) {
        return t != null && start.isBefore(t) && end.isAfter(t);
    }

    @Override
    public String toString() {
        return String.format("%s-%s (%s/%s)", start, end, beforeFuzziness, afterFuzziness);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TimeSpecification)) {
            return false;
        }
        TimeSpecification o = (TimeSpecification) obj;
        return Objects.equals(start, o.start) && Objects.equals(end, o.end)
                && Objects.equals(beforeFuzziness, o.beforeFuzziness)
                && Objects.equals(afterFuzziness, o.afterFuzziness);
    }

    @JsonProperty
    @JsonSerialize(using = LocalDateSerializer.class)
    public LocalDate getStart() {
        return start.toLocalDate();
    }

    @JsonProperty
    @JsonSerialize(using = LocalDateSerializer.class)
    public LocalDate getEnd() {
        return end.toLocalDate();
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end, beforeFuzziness, afterFuzziness);
    }
}
