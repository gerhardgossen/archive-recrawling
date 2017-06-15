package de.l3s.gossen.crawler.frontier;

import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.ThreadLocalRandom;

import com.google.common.annotations.VisibleForTesting;

/**
 * Pick a number in [0, n) such that higher numbers are picked more frequently.
 *
 * The value i is picked with a probability proportional to x^i, where x is the
 * specified base.
 */
public class WeightedRandomSelector {
    private final double[] distribution;
    private final BitSet enabled;
    private double[] activeValues;

    public WeightedRandomSelector(int choices, int base) {
        distribution = createWeightedDistribution(choices, base);
        enabled = new BitSet(choices);
    }

    /**
     * Create the probability density function of an exponentially decreasing
     * distribution.
     * @param choices
     *            number of values
     * @param base
     *            exponent base
     *
     * @return an array of length <tt>length</tt>
     */
    private static double[] createWeightedDistribution(int choices, int base) {
        // see http://mikestoolbox.com/powersum.html
        double normalization = base == 1 ? base + 1 : (Math.pow(base, choices + 1) - 1) / (base - 1);
        double[] ret = new double[choices];
        for (int i = 0; i < choices; i++) {
            ret[i] = Math.pow(base, choices - i) / normalization;
        }
        return ret;
    }

    public int next() {
        if (activeValues == null) {
            activeValues = fillActiveValues();
        }
        if (activeValues.length == 0) {
            return -1;
        }
        double random = ThreadLocalRandom.current().nextDouble(activeValues[activeValues.length - 1]);
        int selected = findPosition(activeValues, random);
        return distribution.length - originalIndex(selected) - 1;
    }

    @VisibleForTesting
    static int findPosition(double[] haystack, double needle) {
        int pos = Arrays.binarySearch(haystack, needle);
        return pos >= 0 ? pos : -(pos + 1);
    }

    private int originalIndex(int idx) {
        for (int i = enabled.nextSetBit(0), pos = 0; i >= 0; i = enabled.nextSetBit(i + 1)) {
            if (pos++ == idx) {
                return i;
            }
        }
        return -1;
    }

    private double[] fillActiveValues() {
        int active = enabled.cardinality();
        double[] values = new double[active];
        int valIdx = 0;
        for (int i = enabled.nextSetBit(0); i >= 0; i = enabled.nextSetBit(i+1)) {
            values[valIdx] = distribution[i];
            if (valIdx >= 1) {
                values[valIdx] += values[valIdx-1];
            }
            valIdx++;
        }
        return values;
    }

    public void enable(int idx) {
        set(idx, true);
    }

    public void disable(int idx) {
        set(idx, false);
    }

    private void set(int idx, boolean value) {
        int flippedIndex = distribution.length - idx - 1;
        if (enabled.get(flippedIndex) != value) {
            enabled.flip(flippedIndex);
            activeValues = null;
        }
    }
}
