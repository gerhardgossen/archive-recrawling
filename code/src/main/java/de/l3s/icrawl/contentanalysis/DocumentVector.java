package de.l3s.icrawl.contentanalysis;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import static java.util.Objects.requireNonNull;

/**
 * A sparse representation of a term frequency document vector
 */
public class DocumentVector implements Serializable {
    private static final Ordering<Map.Entry<String, Double>> ORDER_BY_WEIGHT = Ordering.natural()
        .onResultOf(Map.Entry::getValue);
    private static final long serialVersionUID = 1L;
    @JsonProperty
    private final Map<String, Double> elements;
    private final double norm;

    @JsonCreator
    public DocumentVector(@JsonProperty("elements") Map<String, Double> elements) {
        this.elements = requireNonNull(elements);
        norm = norm(elements);
    }

    public DocumentVector(Collection<String> tokens) {
        this(toDocumentVector(tokens));
    }

    /**
     * Create a term frequency vector of a token list
     *
     * @param tokens
     *            the tokens to use
     * @return a sparse document vector
     */
    private static Map<String, Double> toDocumentVector(Collection<String> tokens) {
        Multiset<String> counts = HashMultiset.create(tokens);
        Map<String, Double> dv = Maps.newHashMapWithExpectedSize(counts.elementSet().size());
        double size = tokens.size();

        for (Entry<String> entry : counts.entrySet()) {
            dv.put(entry.getElement(), entry.getCount() / size);
        }

        return dv;
    }

    public double cosineSimilarity(DocumentVector other) {
        return dotProduct(other) / (norm * other.norm);
    }

    public double dotProduct(DocumentVector other) {
        SetView<String> bothTokens = Sets.intersection(elements.keySet(), other.elements.keySet());

        double dotProduct = 0.0;
        for (String token : bothTokens) {
            dotProduct += elements.get(token) * other.elements.get(token);
        }
        return dotProduct;
    }

    public static DocumentVector merge(Collection<DocumentVector> vectors, boolean useDocumentFrequency) {
        Preconditions.checkArgument(!vectors.isEmpty(), "Cannot merge zero vectors");
        int expectedSize = vectors.size() * vectors.iterator().next().elements.size();
        Multiset<String> keys = HashMultiset.create(expectedSize);
        for (DocumentVector vector : vectors) {
            keys.addAll(vector.elements.keySet());
        }
        Map<String, Double> mergedValues = Maps.newHashMapWithExpectedSize(keys.size());
        for (Entry<String> key : keys.entrySet()) {
            double sum = 0.0;
            for (DocumentVector vector : vectors) {
                Double vectorValue = vector.elements.get(key.getElement());
                if (vectorValue != null) {
                    sum += vectorValue;
                }
            }
            if (useDocumentFrequency) {
                sum *= (((double) key.getCount()) / vectors.size());
            }
            mergedValues.put(key.getElement(), sum);
        }
        return new DocumentVector(mergedValues);
    }

    private static <T> double norm(Map<T, Double> dv) {
        double sum = 0.0;
        for (Double value : dv.values()) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    public DocumentVector topN(int n) {
        Map<String, Double> newElements = new HashMap<>();
        for (Map.Entry<String, Double> entry : topComponents(n)) {
            newElements.put(entry.getKey(), entry.getValue());
        }
        return new DocumentVector(newElements);
    }

    public List<Map.Entry<String, Double>> topComponents(int count) {
        return ORDER_BY_WEIGHT.greatestOf(elements.entrySet(), count);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elements, norm);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        DocumentVector other = (DocumentVector) obj;
        if (Double.doubleToLongBits(norm) != Double.doubleToLongBits(other.norm)) {
            return false;
        }
        if (!elements.equals(other.elements)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        Joiner.on(", ").withKeyValueSeparator(": ").appendTo(sb, ORDER_BY_WEIGHT.sortedCopy(elements.entrySet()));
        return sb.append("}").toString();
    }
}
