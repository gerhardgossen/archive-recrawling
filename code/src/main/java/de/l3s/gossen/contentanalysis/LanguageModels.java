package de.l3s.gossen.contentanalysis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import de.l3s.gossen.contentanalysis.LanguageModel.KeywordMatcher;

import static java.nio.charset.StandardCharsets.UTF_8;

public class LanguageModels {
    private static final Logger logger = LoggerFactory.getLogger(LanguageModels.class);
    private final Locale defaultLanguage;
    private final Map<Locale, LanguageModel> models;

    public LanguageModels(Locale language, Map<String, Double> idfDictionary, Locale defaultLanguage) {
        this(language, ImmutableMap.of(language, new LanguageModel(getAnalyzerForLanguage(language), idfDictionary)));
    }

    public LanguageModels(Locale language, Map<Locale, LanguageModel> modelsMap) {
        this.models = modelsMap;
        this.defaultLanguage = language;
    }


    public Locale getDefaultLanguage() {
        return defaultLanguage;
    }

    /**
     * calculate the cosine-similarity of the doc to the specification
     *
     * @param language
     *            the language of this document
     * @param doc
     *            the text of the document
     *
     * @return the cosine-similarity of the doc to the specification
     */
    public double getSimilarity(Locale language, String doc, DocumentVector reference, KeywordMatcher matcher) {
        Preconditions.checkArgument(!doc.isEmpty(), "Document must have length > 0.");
        LanguageModel model = getLanguageModel(language);
        DocumentVector documentVector = model.buildDocumentVector(doc, matcher);
        double documentSimilarity = reference.cosineSimilarity(documentVector);
        logger.trace("result: {}", documentSimilarity);
        if (Double.isInfinite(documentSimilarity) || Double.isNaN(documentSimilarity)) {
            logger.debug("Got NaN similarity for input '{}'@{}: {}", doc, language, documentSimilarity);
            return 0.0;
        }
        return documentSimilarity;
    }

    LanguageModel getLanguageModel(Locale language) {
        LanguageModel model = models.get(language);
        if (model == null) {
            model = models.get(defaultLanguage);
        }
        if (model == null) {
            throw new IllegalArgumentException("Could not find model for language " + language);
        }
        return model;
    }

    static Analyzer getAnalyzerForLanguage(Locale lang) {
        if (Locale.GERMAN.equals(lang)) {
            return new GermanAnalyzer(CharArraySet.EMPTY_SET);
        } else if (Locale.ENGLISH.equals(lang)) {
            return new EnglishAnalyzer();
        } else if (Locale.ITALIAN.equals(lang)) {
            return new ItalianAnalyzer();
        } else if (Locale.FRENCH.equals(lang)) {
            return new FrenchAnalyzer();
        } else {
            throw new IllegalArgumentException("Could not find model for language " + lang);
        }
    }

    public KeywordMatcher buildMatcher(Locale lang, Iterable<String> keywords, int ngramSize) {
        return getLanguageModel(lang).buildMatcher(keywords, ngramSize);
    }

    public DocumentVector buildDocumentVector(Locale language, String document, KeywordMatcher keywordMatcher) {
        return getLanguageModel(language).buildDocumentVector(document, keywordMatcher);
    }

    public static Map<String, Double> readIdfDictionary(InputStream is) throws IOException {
        final Pattern splitPattern = Pattern.compile("\\s+");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, UTF_8), 8048)) {
            ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = splitPattern.split(line, 2);
                builder.put(parts[0], Double.valueOf(parts[1]));
            }
            return builder.build();
        }
    }

    public static LanguageModel readLanguageModel(Locale locale, InputStream idfIs) throws IOException {
        return new LanguageModel(getAnalyzerForLanguage(locale), readIdfDictionary(idfIs));
    }
}