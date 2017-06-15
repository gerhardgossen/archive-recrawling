package de.l3s.gossen.crawler.scheduling;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class QueueEmptyCriterion extends StoppingCriterion {
    private final long timeOut;
    private final TimeUnit timeOutUnit;
    private final ScheduledExecutorService queueStopperRunner = Executors.newSingleThreadScheduledExecutor();
    private Future<?> queueStopper;
    private LocalDateTime started;

    public QueueEmptyCriterion(long timeOut, TimeUnit timeOutUnit) {
        this.timeOut = timeOut;
        this.timeOutUnit = timeOutUnit;
    }

    @Override
    public void updateSuccess(double relevance) {
        cancelTimeout();
    }

    @Override
    public void updateFailure() {
        cancelTimeout();
    }

    @Override
    public void updateIrrelevant(double relevance) {
        cancelTimeout();
    }

    private void cancelTimeout() {
        if (queueStopper != null) {
            queueStopper.cancel(true);
            queueStopper = null;
        }
    }

    @Override
    public void updateEmptyQueue() {
        if (queueStopper == null) {
            queueStopper = queueStopperRunner.schedule(new Runnable() {
                @Override
                public void run() {
                    QueueEmptyCriterion.this.stop();
                }
            }, timeOut, timeOutUnit);
            this.started = LocalDateTime.now();
        }
    }

    @Override
    public float getProgress() {
        if (queueStopper != null) {
            LocalDateTime now = LocalDateTime.now();
            long elapsedMillis = Duration.between(started, now).toMillis();
            long totalMillis = Duration.of(timeOut, temporalUnit(timeOutUnit)).toMillis();
            return (float) (((double) elapsedMillis) / totalMillis);
        } else {
            return 0;
        }
    }

    private static TemporalUnit temporalUnit(TimeUnit timeUnit) {
        if (timeUnit == null) {
            return null;
        }
        switch (timeUnit) {
            case DAYS:
                return ChronoUnit.DAYS;
            case HOURS:
                return ChronoUnit.HOURS;
            case MINUTES:
                return ChronoUnit.MINUTES;
            case SECONDS:
                return ChronoUnit.SECONDS;
            case MICROSECONDS:
                return ChronoUnit.MICROS;
            case MILLISECONDS:
                return ChronoUnit.MILLIS;
            case NANOSECONDS:
                return ChronoUnit.NANOS;
            default:
                throw new IllegalArgumentException("Unknown timeUnit " + timeUnit);
        }
    }

}
