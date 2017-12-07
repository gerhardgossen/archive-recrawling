package de.l3s.icrawl.contentanalysis;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import org.openimaj.text.nlp.language.LanguageDetector;
import org.openimaj.text.nlp.language.LanguageDetector.WeightedLocale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;

import de.l3s.icrawl.domain.specification.NamedEntity;

import com.google.common.collect.Multisets;

public class ContentAnalyser {
    public static class Counts {
        private final Multiset<String> keywords;
        private final Multiset<NamedEntity> entities;
        private final List<String> detectedKeywords;

        private final long documentLength;
        private final Locale language;

        Counts(Multiset<String> keywords, Multiset<NamedEntity> entities, List<String> detectedKeywords,
                long documentLength, Locale language) {
            this.keywords = keywords;
            this.entities = entities;
            this.detectedKeywords = detectedKeywords;
            this.documentLength = documentLength;
            this.language = language;
        }

        public Multiset<String> getKeywords() {
            return keywords;
        }

        public Multiset<NamedEntity> getEntities() {
            return entities;
        }

        public long getDocumentLength() {
            return documentLength;
        }

        public List<String> getDetectedKeywords() {
            return detectedKeywords;
        }

        public Locale getLanguage() {
            return language;
        }

        public static <T> List<T> topK(Multiset<T> set, int k) {
            ImmutableList.Builder<T> results = ImmutableList.builder();
            Multiset<T> highestCounts = Multisets.copyHighestCountFirst(set);
            Iterable<Entry<T>> first = Iterables.limit(highestCounts.entrySet(), k);
            for (Entry<T> entry : first) {
                results.add(entry.getElement());
            }
            return results.build();
        }
    }

    private static final CharMatcher SEPARATOR_MATCHER = CharMatcher.WHITESPACE.or(CharMatcher.anyOf("<>|“”„‚‘’,;.:-_'+*`'()$%!\"?"));
    private static final Splitter TEXT_SPLITTER = Splitter.on(SEPARATOR_MATCHER).omitEmptyStrings();
    private static final Logger logger = LoggerFactory.getLogger(ContentAnalyser.class);
    private final LanguageDetector languageDetector;
    private final LabelerFactory labelerFactory;
    private final TextRankWrapper textRank;

    public ContentAnalyser(LanguageDetector languageDetector,
            @Nullable LabelerFactory labelerFactory) {
        this.languageDetector = languageDetector;
        this.labelerFactory = labelerFactory;
        this.textRank = new TextRankWrapper();
    }

    public Counts analyze(List<String> paragraphs, Set<String> keywords) {
        Multiset<String> detectedKeywords = HashMultiset.create(keywords.size());
        Multiset<NamedEntity> detectedEntities = HashMultiset.create();
        long words = 0;

        String text = joinParagraphs(paragraphs);
        WeightedLocale wl = languageDetector.classify(text);

        Labeler labeler;
        if (labelerFactory != null) {
            labeler = labelerFactory.get(wl);
        } else {
            labeler = null;
        }

        List<String> extractedKeywords;
        try {
            extractedKeywords = textRank.rank(text, wl.getLocale(), 10);
        } catch (RuntimeException e) {
            logger.debug("Exception while running TextRank on '{}'@{}: ",
                text.length() > 50 ? text.substring(0, 50) + "..." : text, wl.getLocale(), e);
            extractedKeywords = Collections.emptyList();
        }

        for (String paragraph : paragraphs) {
            detectedEntities.addAll(extractEntities(labeler, paragraph));
            if (!keywords.isEmpty()) {
                words += extractSpecifiedKeywords(paragraph, detectedKeywords, keywords);
            } else {
                words += countWords(paragraph);
            }
        }

        return new Counts(detectedKeywords, detectedEntities, extractedKeywords, words, wl.getLocale());
    }

    private long extractSpecifiedKeywords(String paragraph, Multiset<String> detectedKeywords,
            Set<String> keywords) {
        long tokens = 0;
        for (String token : TEXT_SPLITTER.split(paragraph)) {
            if (SEPARATOR_MATCHER.matchesAllOf(token)) {
                continue;
            }
            tokens++;
            String actualToken = token.toLowerCase().trim();
            if (keywords.contains(actualToken)) {
                detectedKeywords.add(actualToken);
            }
        }
        return tokens;
    }

    private long countWords(String paragraph) {
        long tokens = 0;
        for (String token : TEXT_SPLITTER.split(paragraph)) {
            if (!SEPARATOR_MATCHER.matchesAllOf(token)) {
                tokens++;
            }
        }

        return tokens;
    }

    protected Collection<NamedEntity> extractEntities(Labeler labeler, String paragraph) {
        if (labeler != null) {
            return labeler.extractEntities(paragraph);
        }
        return Collections.emptySet();
    }

    private static String joinParagraphs(List<String> paragraphs) {
        return Joiner.on('\n').join(paragraphs);
    }
}