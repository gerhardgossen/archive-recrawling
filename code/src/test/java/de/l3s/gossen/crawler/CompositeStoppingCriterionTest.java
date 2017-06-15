package de.l3s.gossen.crawler;

import org.junit.Test;
import org.mockito.Mockito;

import de.l3s.gossen.crawler.scheduling.CompositeStoppingCriterion;
import de.l3s.gossen.crawler.scheduling.StoppingCriterion;
import de.l3s.gossen.crawler.scheduling.StoppingCriterion.StopListener;
import static org.mockito.Mockito.verify;

public class CompositeStoppingCriterionTest {

    private final class MockStoppingCriterion extends StoppingCriterion {
        public void trigger() {
            this.stop();
        }

        @Override
        public float getProgress() {
            return 0f;
        }
    }

    @Test
    public void testStopping() {
        MockStoppingCriterion mockCriterion = new MockStoppingCriterion();
        StoppingCriterion composed = new CompositeStoppingCriterion(mockCriterion);
        StopListener mockListener = Mockito.mock(StopListener.class);
        composed.addListener(mockListener);
        mockCriterion.trigger();
        verify(mockListener).stop();
    }

}
