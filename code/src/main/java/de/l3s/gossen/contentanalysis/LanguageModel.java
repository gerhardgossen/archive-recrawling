package de.l3s.gossen.contentanalysis;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

public class LanguageModel {

    static class KeywordMatcher implements Serializable {
        private static final long serialVersionUID = 1L;
        public static final double FULL_MATCH_WEIGHT = 2.0;
        public static final double PARTIAL_MATCH_WEIGHT = 1.5;
        public static final double NO_MATCH_WEIGHT = 1.0;

        enum Match {
            MATCHES_FULL, MATCHES_PARTIAL, NO_MATCH
        }

        private static final Joiner TOKEN_JOINER = Joiner.on(DocumentVectorSimilarity.TOKEN_SEPARATOR);
        @JsonProperty
        private final Set<String> singleTokenKeywords;
        @JsonProperty
        private final Set<String> multiTokenKeywords;
        @JsonProperty
        private final int ngramSize;

        @JsonCreator
        protected KeywordMatcher(@JsonProperty("singleTokenKeywords") Set<String> singleTokenKeywords,
                @JsonProperty("multiTokenKeywords") Set<String> multiTokenKeywords,
                @JsonProperty("ngramSize") int ngramSize) {
            this.singleTokenKeywords = singleTokenKeywords;
            this.multiTokenKeywords = multiTokenKeywords;
            this.ngramSize = ngramSize;
        }

        public KeywordMatcher(Iterable<String> keywords, Analyzer analyzer, int ngramSize) {
            this.ngramSize = ngramSize;
            ImmutableSet.Builder<String> singleTokenKeywordsBuilder = ImmutableSet.builder();
            ImmutableSet.Builder<String> multiTokenKeywordsBuilder = ImmutableSet.builder();
            for (String keyword : keywords) {
                List<String> tokens = analyzeDocument(keyword, analyzer, new ArrayList<String>());
                if (tokens.isEmpty()) {
                    logger.debug("empty tokens list for keywords '{}'", keyword);
                } else if (tokens.size() == 1) {
                    singleTokenKeywordsBuilder.add(tokens.get(0));
                } else {
                    multiTokenKeywordsBuilder.addAll(ngrams(tokens, ngramSize));
                }
            }
            this.singleTokenKeywords = singleTokenKeywordsBuilder.build();
            this.multiTokenKeywords = multiTokenKeywordsBuilder.build();
        }

        private Set<String> ngrams(List<String> tokens, int ngramSize) {
            Set<String> ngrams = Sets.newHashSetWithExpectedSize(tokens.size());
            for (int i = 0; i < tokens.size() - ngramSize + 1; i++) {
                ngrams.add(TOKEN_JOINER.join(tokens.subList(i, i + ngramSize)));
            }
            return ngrams;
        }

        public Match match(List<String> tokens) {
            for (String ngram : ngrams(tokens, ngramSize)) {
                if (multiTokenKeywords.contains(ngram)) {
                    return Match.MATCHES_FULL;
                }
            }
            for (String token : tokens) {
                if (singleTokenKeywords.contains(token)) {
                    return Match.MATCHES_PARTIAL;
                }
            }
            return Match.NO_MATCH;
        }

        public static KeywordMatcher matchNone() {
            return new KeywordMatcher(Collections.<String> emptySet(), Collections.<String> emptySet(), 1);
        }
    }

    private static final int EXPECTED_DOCUMENT_VOCABULARY_SIZE = 1024;
    private static final double MIN_NUMBER_OCCURRENCES = 0.005;
    private static final Logger logger = LoggerFactory.getLogger(LanguageModel.class);
    private final ImmutableMap<String, Double> idfDictionary;
    private final double maxIdfValue;
    private final Analyzer analyzer;

    public LanguageModel(Analyzer analyzer, Map<String, Double> idfValues) {
        this.analyzer = analyzer;
        Map<String, Double> builder = Maps.newHashMapWithExpectedSize(idfValues.size());
        for (Entry<String, Double> entry : idfValues.entrySet()) {
            String analyzed = analyzeToken(entry.getKey());
            Double oldValue = builder.get(analyzed);
            if (oldValue == null || oldValue > entry.getValue()) {
                builder.put(analyzed, entry.getValue());
            }
        }
        this.idfDictionary = ImmutableMap.copyOf(builder);
        this.maxIdfValue = idfDictionary.isEmpty() ? 1.0 : Ordering.natural().max(idfDictionary.values());

    }

    public DocumentVector buildDocumentVector(String document, KeywordMatcher keywordMatcher) {
        Multiset<String> tokens = analyzeDocument(document, analyzer,
            HashMultiset.<String> create(EXPECTED_DOCUMENT_VOCABULARY_SIZE));
        Map<String, Double> dv = Maps.newHashMapWithExpectedSize(tokens.elementSet().size());

        double size = tokens.size();
        Splitter tokenizer = Splitter.on(DocumentVectorSimilarity.TOKEN_SEPARATOR);
        for (Multiset.Entry<String> entry : tokens.entrySet()) {
            double weight;
            String multiToken = entry.getElement();
            if (CharMatcher.DIGIT.matchesAnyOf(multiToken)
                    && (entry.getCount() / size < MIN_NUMBER_OCCURRENCES || multiToken.length() == 1)) {
                continue;
            }
            List<String> tokenList = tokenizer.splitToList(multiToken);
            if (keywordMatcher != null) {
                switch (keywordMatcher.match(tokenList)) {
                case MATCHES_FULL:
                    weight = LanguageModel.KeywordMatcher.FULL_MATCH_WEIGHT;
                    break;
                case MATCHES_PARTIAL:
                    weight = LanguageModel.KeywordMatcher.PARTIAL_MATCH_WEIGHT;
                    break;
                case NO_MATCH:
                default:
                    weight = LanguageModel.KeywordMatcher.NO_MATCH_WEIGHT;
                }
            } else {
                weight = LanguageModel.KeywordMatcher.NO_MATCH_WEIGHT;
            }
            dv.put(multiToken, weight * tf(entry.getCount()) * idf(entry.getElement()));
        }
        return new DocumentVector(dv);
    }

    private static double tf(int occurrences) {
        return occurrences <= 0 ? 0 : 1 + Math.log(occurrences);
    }

    private double idf(String token) {
        Double idf = idfDictionary.get(token);
        return idf != null ? idf.doubleValue() : maxIdfValue;
    }

    static <T extends Collection<String>> T analyzeDocument(String document, Analyzer analyzer, T tokenConsumer) {
        try (TokenStream ts = analyzer.tokenStream("text", document)) {
            ts.reset();
            CharTermAttribute textAttribute = ts.addAttribute(CharTermAttribute.class);
            while (ts.incrementToken()) {
                tokenConsumer.add(textAttribute.toString());
            }
            ts.end();
        } catch (IOException e) {
            throw new AssertionError("Unexpected exception while analysing string", e);
        }
        return tokenConsumer;
    }

    String analyzeToken(String token) {
        try (TokenStream ts = analyzer.tokenStream("text", token)) {
            ts.reset();
            CharTermAttribute textAttribute = ts.addAttribute(CharTermAttribute.class);
            ts.incrementToken();
            String analyzed = textAttribute.toString();
            ts.end();
            return analyzed;
        } catch (IOException e) {
            throw new AssertionError("Unexpected exception while analysing string", e);
        }
    }

    public KeywordMatcher buildMatcher(Iterable<String> keywords, int ngramSize) {
        return new KeywordMatcher(keywords, analyzer, ngramSize);
    }
}
