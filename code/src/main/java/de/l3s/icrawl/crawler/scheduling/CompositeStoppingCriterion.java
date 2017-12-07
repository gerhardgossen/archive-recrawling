package de.l3s.icrawl.crawler.scheduling;

import java.util.Arrays;
import java.util.List;

public class CompositeStoppingCriterion extends StoppingCriterion {
    private final List<StoppingCriterion> criteria;

    public CompositeStoppingCriterion(List<StoppingCriterion> criteria) {
        this.criteria = criteria;
        StopListener propagatingListener = new StopListener() {
            @Override
            public void stop() {
                CompositeStoppingCriterion.this.stop();
            }
        };
        for (StoppingCriterion stoppingCriterion : this.criteria) {
            stoppingCriterion.addListener(propagatingListener);
        }
    }

    public CompositeStoppingCriterion(StoppingCriterion... criteria) {
        this(Arrays.asList(criteria));
    }

    @Override
    public void updateSuccess(double relevance) {
        for (StoppingCriterion stoppingCriterion : criteria) {
            stoppingCriterion.updateSuccess(relevance);
        }
    }

    @Override
    public void updateFailure() {
        for (StoppingCriterion stoppingCriterion : criteria) {
            stoppingCriterion.updateFailure();
        }
    }

    @Override
    public void updateEmptyQueue() {
        for (StoppingCriterion stoppingCriterion : criteria) {
            stoppingCriterion.updateEmptyQueue();
        }
    }

    @Override
    public void updateIrrelevant(double relevance) {
        for (StoppingCriterion stoppingCriterion : criteria) {
            stoppingCriterion.updateIrrelevant(relevance);
        }
    }

    @Override
    public float getProgress() {
        float maxProgress = -1;
        for (StoppingCriterion stoppingCriterion : criteria) {
            maxProgress = Math.max(maxProgress, stoppingCriterion.getProgress());
        }
        return maxProgress;
    }

}
