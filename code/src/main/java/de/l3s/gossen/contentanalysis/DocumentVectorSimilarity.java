package de.l3s.gossen.contentanalysis;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import de.l3s.gossen.contentanalysis.LanguageModel.KeywordMatcher;
import de.l3s.gossen.domain.specification.NamedEntity;
import de.l3s.gossen.domain.specification.NamedEntity.Label;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

/**
 * Class to compute the distance of a document to a document collection.
 */
@ThreadSafe
public class DocumentVectorSimilarity implements Serializable {

    static final String TOKEN_SEPARATOR = " ";
    static final int DEFAULT_NGRAM_SIZE = 2;
    private static final Logger logger = LoggerFactory.getLogger(DocumentVectorSimilarity.class);
    private static final long serialVersionUID = 3L;

    @JsonProperty
    private final Map<Locale, DocumentVector> referenceVectors;
    @JsonProperty
    private final Map<Locale, KeywordMatcher> matchers;
    @JsonIgnore
    private LanguageModels languageModels;
    @JsonIgnore
    private final Locale defaultLanguage;
    private final Map<Locale, Double> correctionFactors;

    /**
     * Create a SpecSimilarity Object that is serializable and stores a
     * reference to the input's document collection.
     *
     * @param referenceDocumentsToLanguage
     *            the document collection (each mapping to its language)
     */
    public DocumentVectorSimilarity(Map<String, Locale> referenceDocumentsToLanguage, Set<String> keywords,
            Set<NamedEntity> entities, int maxTerms, boolean useDF, Locale defaultLanguage, LanguageModels languageModels) {

        this.defaultLanguage = defaultLanguage;
        this.languageModels = languageModels;
        Multimap<Locale, String> documents = Multimaps.invertFrom(Multimaps.forMap(referenceDocumentsToLanguage),
            ArrayListMultimap.create());
        Set<String> allLanguageKeywords = new HashSet<>(keywords);
        Multimap<Locale, String> keywordsByLanguage = HashMultimap.create();
        for (NamedEntity entity : entities) {
            for (Label label : entity.getLabels()) {
                if (label.getLanguage() != null) {
                    keywordsByLanguage.put(label.getLanguage(), label.getName());
                } else {
                    allLanguageKeywords.add(label.getName());
                }
            }
        }
        ImmutableMap.Builder<Locale, DocumentVector> vectors = ImmutableMap.builder();
        ImmutableMap.Builder<Locale, KeywordMatcher> matchersBuilder = ImmutableMap.builder();
        for (Map.Entry<Locale, Collection<String>> languageDocuments : documents.asMap().entrySet()) {
            Locale lang = languageDocuments.getKey();
            KeywordMatcher keywordMatcher = languageModels.buildMatcher(lang,
                Iterables.concat(keywordsByLanguage.get(lang), allLanguageKeywords), DEFAULT_NGRAM_SIZE);
            Collection<DocumentVector> languageVectors = Lists.newArrayListWithExpectedSize(languageDocuments.getValue().size());
            for (String document : languageDocuments.getValue()) {
                languageVectors.add(languageModels.buildDocumentVector(lang, document, keywordMatcher));
            }
            logger.debug("Got doc vectors for language {}: {}", lang.getLanguage(), languageVectors);
            DocumentVector vector = DocumentVector.merge(languageVectors, useDF);
            if (maxTerms > 0) {
                vector = vector.topN(maxTerms);
            }
            vectors.put(lang, vector);
            matchersBuilder.put(lang, keywordMatcher);
        }
        this.referenceVectors = vectors.build();
        this.matchers = matchersBuilder.build();
        if (logger.isDebugEnabled()) {
            for (Entry<Locale, DocumentVector> entry : referenceVectors.entrySet()) {
                logger.debug("Reference vector for language '{}': {}...", entry.getKey(), entry.getValue().topComponents(10));
            }
        }
        ImmutableMap.Builder<Locale, Double> correctionFactors = ImmutableMap.builder();
        for (Locale language : documents.keySet()) {
            DocumentVector reference = referenceVectors.get(language);
            KeywordMatcher matcher = matchers.get(language);
            correctionFactors.put(language,
                documents.get(language)
                    .stream()
                    .mapToDouble(document -> languageModels.getSimilarity(language, document, reference, matcher))
                    .average()
                    .orElse(1.0));
        }
        this.correctionFactors = correctionFactors.build();
    }

    public static DocumentVectorSimilarity fromVectors(Map<Locale, DocumentVector> referenceVectors,
            Map<Locale, Set<String>> keywords, Locale defaultLanguage, LanguageModels languageModels,
            Map<Locale, Double> correctionFactors) {
        Set<String> allLanguageKeywords = keywords.values().stream().flatMap(Collection::stream).collect(toSet());
        ImmutableMap.Builder<Locale, KeywordMatcher> matchersBuilder = ImmutableMap.builder();
        for (Entry<Locale, Set<String>> language : keywords.entrySet()) {
            Locale lang = language.getKey();
            KeywordMatcher keywordMatcher = languageModels.buildMatcher(lang, allLanguageKeywords, DEFAULT_NGRAM_SIZE);
            matchersBuilder.put(lang, keywordMatcher);
        }

        DocumentVectorSimilarity dvs = new DocumentVectorSimilarity(referenceVectors, matchersBuilder.build(), defaultLanguage,
            correctionFactors);
        dvs.setLanguageModels(languageModels);
        return dvs;
    }

    public void setLanguageModels(LanguageModels languageModels) {
        this.languageModels = languageModels;
    }

    @JsonCreator
    protected DocumentVectorSimilarity(@JsonProperty("referenceVectors") Map<Locale, DocumentVector> referenceVectors,
            @JsonProperty("matchers") Map<Locale, KeywordMatcher> matchers,
            @JsonProperty("defaultLanguage") Locale defaultLanguage,
            @JsonProperty("correctionFactors") Map<Locale, Double> correctionFactors) {
        this.referenceVectors = referenceVectors;
        this.matchers = matchers;
        this.defaultLanguage = defaultLanguage;
        this.correctionFactors = correctionFactors;
        this.languageModels = new LanguageModels(defaultLanguage, new HashMap<String, Double>(), defaultLanguage);
    }

    @Override
    public String toString() {
        return referenceVectors.entrySet()
            .stream()
            .map(e -> String.format("%s => %s", e.getKey(), e.getValue().topComponents(10)))
            .collect(joining(", ", "DocumentVectorSimilarity[", "]"));
    }

    private KeywordMatcher getMatcher(Locale language) {
        KeywordMatcher keywordMatcher = matchers.get(language);
        if (keywordMatcher == null) {
            logger.debug("No keyword matcher for language '{}', falling back to default", language);
            keywordMatcher = matchers.get(defaultLanguage);
        }
        return keywordMatcher;
    }

    private DocumentVector getReferenceVector(Locale language) {
        DocumentVector reference = referenceVectors.get(language);
        if (reference == null) {
            logger.debug("No reference vector for language '{}', falling back to default", language);
            reference = referenceVectors.get(defaultLanguage);
        }
        return reference;
    }

    public Map<Locale, DocumentVector> getReferenceVectors() {
        return referenceVectors;
    }

    public Map<Locale, KeywordMatcher> getMatchers() {
        return matchers;
    }

    public Map<Locale, Double> getCorrectionFactors() {
        return correctionFactors;
    }

    public double getSimilarity(Locale language, String text) {
        DocumentVector reference = getReferenceVector(language);
        if (reference == null) {
            logger.info("No reference vector for language {}", language);
            return 0.0;
        }
        KeywordMatcher keywordMatcher = getMatcher(language);
        if (keywordMatcher == null) {
            logger.debug("Available keywords matchers: {}", matchers.keySet());
        }
        double correction = this.correctionFactors.getOrDefault(language, 1.0);
        return languageModels.getSimilarity(language, text, reference, keywordMatcher) / correction;
    }

}
