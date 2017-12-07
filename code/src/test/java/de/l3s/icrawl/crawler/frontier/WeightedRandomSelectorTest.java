package de.l3s.icrawl.crawler.frontier;

import org.junit.Test;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import de.l3s.icrawl.crawler.frontier.WeightedRandomSelector;

import static de.l3s.icrawl.crawler.frontier.WeightedRandomSelector.findPosition;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

public class WeightedRandomSelectorTest {
    private final static int ITERATIONS = 1_000_000;

    @Test
    public void testDistribution() {
        WeightedRandomSelector selector = new WeightedRandomSelector(10, 2);
        for (int i = 0; i < 10; i++) {
            selector.enable(i);
        }
        Multiset<Integer> values = iterate(selector, ITERATIONS);
        assertThat(values.count(9) / (double) ITERATIONS, is(closeTo(0.5, 0.01)));
        assertThat(values.count(8) / (double) ITERATIONS, is(closeTo(0.25, 0.01)));
        assertThat(values.count(7) / (double) ITERATIONS, is(closeTo(0.125, 0.01)));
    }

    private Multiset<Integer> iterate(WeightedRandomSelector selector, int k) {
        Multiset<Integer> values = HashMultiset.create(10);
        for (int i = 0; i < k; i++) {
            values.add(selector.next());
        }
        return values;
    }

    @Test
    public void testQueueSelection() {
        int numQueues = 100;
        float intervalSize = (float) (1.0 / numQueues);
        assertThat((int) (0.0f / intervalSize), is(0));
        assertThat((int) (1.0f / intervalSize) - 1, is(numQueues - 1));
    }

    @Test
    public void testSelectorDisable() {
        WeightedRandomSelector selector = new WeightedRandomSelector(3, 2);

        assertThat("disabled selector returns -1", selector.next(), is(-1));

        selector.enable(2);
        Multiset<Integer> singleEnabled = iterate(selector, 100);
        assertThat(singleEnabled.count(2), is(100));
        assertThat(singleEnabled.elementSet(), hasSize(1));

        selector.enable(1);
        Multiset<Integer> twoEnabled = iterate(selector, ITERATIONS);
        assertThat(twoEnabled.count(2) / (double) ITERATIONS, is(closeTo(0.66, 0.01)));
        assertThat(twoEnabled.count(1) / (double) ITERATIONS, is(closeTo(0.33, 0.01)));
    }

    @Test
    public void testPosition() {
        double[] values = new double[] { 0.5, 0.75, 1.0 };
        assertThat(findPosition(values, 0.00), is(0));
        assertThat(findPosition(values, 0.49), is(0));
        assertThat(findPosition(values, 0.50), is(0));
        assertThat(findPosition(values, 0.51), is(1));
        assertThat(findPosition(values, 0.99), is(2));
        assertThat(findPosition(values, 1.00), is(2));
    }
}
